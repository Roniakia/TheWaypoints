import json
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pathlib import Path

from packs import build, categories, normalize, search_text, fts_query, osm_features
from catalogue import assemble
from benchmark import nearby


class PacksTest(unittest.TestCase):
    def test_transliteration_and_aliases(self):
        self.assertEqual(normalize('Skåne München'), 'skane munchen')
        self.assertIn('athena', normalize('Αθήνα'))
        self.assertEqual(normalize('東京'), 'dong jing')  # Generic transliteration is not an English exonym.
        text = search_text({'name': '東京', 'name:en': 'Tokyo'}, ['food.cafe'])
        self.assertIn('tokyo', text)
        self.assertIn('coffee', text)
        self.assertEqual(fts_query('bike repair'), 'bike* AND repair*')
        self.assertEqual(fts_query('" OR *'), 'or*')

    def test_category_safety_and_multiple_membership(self):
        self.assertEqual(categories({'amenity': 'fountain'}), [])
        self.assertEqual(categories({'amenity': 'drinking_water', 'drinking_water': 'no'}), [])
        self.assertEqual(categories({'amenity': 'cafe', 'access': 'private'}), [])
        self.assertEqual(categories({'amenity': 'charging_station'}), [])
        self.assertIn('charging.ebike', categories({'amenity': 'charging_station', 'bicycle': 'yes'}))
        self.assertEqual(categories({'amenity': 'cafe', 'service:bicycle:pump': 'yes'}), ['bicycle.pump', 'food.cafe'])

    def test_osm_to_searchable_pack(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'sample.osm'
            source.write_text('''<osm version="0.6" generator="test">
              <node id="1" lat="55" lon="13"><tag k="amenity" v="drinking_water"/></node>
              <node id="2" lat="55.1" lon="13.1"><tag k="name" v="Київ"/><tag k="name:en" v="Kyiv"/><tag k="amenity" v="cafe"/></node>
              <node id="3" lat="60" lon="15"><tag k="amenity" v="cafe"/></node>
              <node id="4" lat="55.2" lon="13.2"/><node id="5" lat="55.2" lon="13.3"/>
              <node id="6" lat="55.3" lon="13.3"/><node id="7" lat="55.3" lon="13.2"/>
              <way id="1"><nd ref="4"/><nd ref="5"/><nd ref="6"/><nd ref="7"/><nd ref="4"/><tag k="amenity" v="cafe"/><tag k="name" v="Skåne café"/></way>
              <way id="99"><nd ref="4"/><nd ref="5"/><nd ref="4"/><tag k="amenity" v="cafe"/></way>
              <relation id="1"><member type="way" ref="1" role="outer"/><tag k="type" v="multipolygon"/><tag k="amenity" v="shelter"/></relation>
              <relation id="99"><member type="way" ref="99" role="outer"/><tag k="type" v="multipolygon"/><tag k="amenity" v="cafe"/></relation>
            </osm>''')
            (root / 'boundary.json').write_text(json.dumps({'type': 'Polygon', 'coordinates': [[[12,54],[14,54],[14,56],[12,56],[12,54]]]}))
            region = root / 'region.json'
            region.write_text(json.dumps(dict(id='se-test', name='Skåne', continent='Europe', country='Sweden', boundary='boundary.json')))
            kwargs = dict(source=source, region_path=region, output=root/'out', revision='test-1', data_date='2026-10-21', source_url='https://example.org/fixture')
            report = build(**kwargs)
            self.assertEqual(report['poi_count'], 5)
            self.assertTrue(report['quality_review_required'])
            self.assertTrue(report['source_geometry_failures'])
            report_path = root/'out'/Path(report['file']).with_suffix('.json')
            catalogue = assemble([report_path], 'https://example.org/assets')
            self.assertEqual(catalogue['regions'][0]['id'], 'se-test')
            with self.assertRaises(ValueError):
                assemble([report_path, report_path], 'https://example.org/assets')
            with zipfile.ZipFile(root/'out'/report['file']) as archive:
                archive.extractall(root/'unpacked')
            db = sqlite3.connect(root/'unpacked'/'region.sqlite')
            try:
                self.assertEqual(db.execute("SELECT count(*) FROM poi_search WHERE search_text MATCH 'water'").fetchone()[0], 1)
                self.assertEqual(db.execute("SELECT count(*) FROM poi_search WHERE search_text MATCH 'kyiv'").fetchone()[0], 1)
                self.assertEqual(db.execute("SELECT count(*) FROM poi_search WHERE search_text MATCH 'skane'").fetchone()[0], 1)
                self.assertEqual(db.execute('SELECT count(*) FROM poi_geo').fetchone()[0], 5)
                self.assertEqual([x[1] for x in nearby(db, 55, 13, 15)], ['node/1', 'node/2'])
                self.assertEqual(db.execute('SELECT count(DISTINCT osm_id) FROM poi').fetchone()[0], 5)
            finally:
                db.close()
            with self.assertRaises(FileExistsError):
                build(**kwargs)


    def test_area_events_before_parent_do_not_look_unassembled(self):
        class Object:
            def __init__(self, kind):
                self.kind, self.id = kind, 7
                self.tags = {'amenity': 'cafe', 'type': 'multipolygon'}
            def is_node(self): return False
            def is_way(self): return False
            def is_area(self): return self.kind == 'area'
            def is_relation(self): return self.kind == 'relation'
            def from_way(self): return False
            def orig_id(self): return 7
        class Processor:
            def with_locations(self, *args): return self
            def with_areas(self): return self
            def with_filter(self, *args): return self
            def __iter__(self): return iter([Object('area'), Object('relation')])
        polygon = json.dumps({'type': 'MultiPolygon', 'coordinates': [[[[0,0],[1,0],[1,1],[0,0]]]]})
        diagnostics = []
        with patch('packs.osmium.FileProcessor', return_value=Processor()), patch('packs.osmium.geom.GeoJSONFactory') as factory:
            factory.return_value.create_multipolygon.return_value = polygon
            features = list(osm_features(Path('fixture'), 'unused', diagnostics=diagnostics))
        self.assertEqual([x[0] for x in features], ['relation/7'])
        self.assertEqual(diagnostics, [])

    def test_empty_region_does_not_leave_partial_pack(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root/'sample.osm'
            source.write_text('<osm version="0.6"><node id="1" lat="55" lon="13"><tag k="amenity" v="drinking_water"/></node></osm>')
            (root/'boundary.json').write_text(json.dumps({'type': 'Polygon', 'coordinates': [[[0,0],[1,0],[1,1],[0,1],[0,0]]]}))
            config = root/'region.json'
            config.write_text(json.dumps(dict(id='empty', name='Empty', country='Test', continent='Test', boundary='boundary.json')))
            with self.assertRaisesRegex(ValueError, 'empty region'):
                build(source, config, root/'out', 'test', '2026-10-20', 'https://example.org')
            self.assertEqual(list((root/'out').iterdir()), [])


if __name__ == '__main__':
    unittest.main()
