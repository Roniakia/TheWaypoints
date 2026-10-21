"""Offline OSM POI pack prototype. Never publishes or modifies the Android app."""
import argparse
import datetime
import hashlib
import json
import os
import re
import resource
import sqlite3
import sys
import tempfile
import time
import zipfile
from collections import Counter
from pathlib import Path

import osmium
from shapely import prepare
from shapely.geometry import Point, shape
from text_unidecode import unidecode

ROOT = Path(__file__).parent
RULES = json.loads((ROOT / 'categories.json').read_text())
ATTRIBUTION = '© OpenStreetMap contributors\nhttps://www.openstreetmap.org/copyright\nData licensed under ODbL 1.0: https://opendatacommons.org/licenses/odbl/1-0/\n'


def normalize(value):
    return ' '.join(re.findall(r'[a-z0-9]+', unidecode(value).lower()))


def fts_query(value):
    # Only normalized ASCII words enter FTS syntax; input operators are never executed.
    return ' AND '.join(word + '*' for word in normalize(value).split())


def categories(tags):
    if tags.get('access') == 'private' or any(tags.get(k) == 'yes' for k in ('disused', 'abandoned', 'demolished')):
        return []
    result = [key for key, rule in RULES.items() if any(tags.get(k) in values for k, values in rule['match'].items())]
    if tags.get('drinking_water') == 'no':
        result = [x for x in result if x != 'water.drinking_water']
    if tags.get('amenity') == 'charging_station' and tags.get('bicycle') in ('yes', 'designated'):
        result.append('charging.ebike')
    if tags.get('amenity') == 'device_charging_station':
        result.append('charging.device')
    if tags.get('amenity') == 'vending_machine':
        items = tags.get('vending', '').split(';')
        if 'bicycle_tube' in items:
            result.append('bicycle.tubes')
        if set(items) & {'food', 'drinks', 'water', 'bread', 'milk'}:
            result.append('supplies.vending')
    return sorted(set(result))


EXTRA_ALIASES = {'charging.ebike': 'ebike bicycle charging battery', 'charging.device': 'phone device charging battery', 'bicycle.tubes': 'bicycle bike tube vending spare', 'supplies.vending': 'food drink vending supplies'}


def search_text(tags, types):
    names = [v for k, v in tags.items() if k in ('name', 'alt_name', 'official_name', 'short_name', 'brand', 'operator') or k.startswith(('name:', 'alt_name:'))]
    aliases = [RULES[k]['aliases'] if k in RULES else EXTRA_ALIASES[k] for k in types]
    return normalize(' '.join(names + types + aliases))


def osm_features(source, index, boundary_iso=None, diagnostics=None):
    factory = osmium.geom.GeoJSONFactory()
    keys = sorted({k for rule in RULES.values() for k in rule['match']} | {'boundary', 'drinking_water', 'vending'})
    processor = (osmium.FileProcessor(str(source)).with_locations(index).with_areas()
                 .with_filter(osmium.filter.KeyFilter(*keys)))
    unresolved = {}
    resolved = set()
    closed_ways = {}
    for obj in processor:
        tags = dict(obj.tags)
        if boundary_iso:
            if not obj.is_area() or tags.get('ISO3166-2') != boundary_iso:
                continue
        elif not categories(tags):
            continue
        if not boundary_iso:
            if obj.is_relation() and tags.get('type') in ('multipolygon', 'boundary'):
                identity = f'relation/{obj.id}'
                if identity not in resolved:
                    unresolved[identity] = tags
            elif obj.is_area():
                resolved.add(f'{"way" if obj.from_way() else "relation"}/{obj.orig_id()}')
                unresolved.pop(f'{"way" if obj.from_way() else "relation"}/{obj.orig_id()}', None)
                closed_ways.pop(f'way/{obj.orig_id()}' if obj.from_way() else '', None)
        try:
            if obj.is_node():
                geometry = Point(obj.location.lon, obj.location.lat)
                identity = f'node/{obj.id}'
            elif obj.is_area():
                geometry = shape(json.loads(factory.create_multipolygon(obj)))
                identity = f'{"way" if obj.from_way() else "relation"}/{obj.orig_id()}'
            elif obj.is_way():
                geometry = shape(json.loads(factory.create_linestring(obj)))
                identity = f'way/{obj.id}'
                if geometry.is_empty or not geometry.is_valid:
                    raise ValueError(f'Invalid geometry for {identity}')
                if obj.is_closed():
                    # Not every closed tagged way is an OSM area. Prefer assembled
                    # polygons; otherwise retain a point on the original line.
                    if identity not in resolved:
                        closed_ways[identity] = (tags, geometry.representative_point())
                    continue
            else:
                continue
            if geometry.is_empty or not geometry.is_valid:
                raise ValueError(f'Invalid geometry for {identity}')
        except (osmium.InvalidLocationError, RuntimeError, ValueError) as exc:
            if diagnostics is None or boundary_iso:
                raise ValueError(f'Cannot resolve geometry for OSM object {obj.id}: {exc}') from exc
            diagnostics.append({'object': f'{obj.type_str()}/{obj.id}', 'error': str(exc)})
            if len(diagnostics) > 100:
                raise ValueError('More than 100 geometry failures; refusing incomplete source') from exc
            continue
        yield identity, tags, geometry
    for identity, (tags, point) in closed_ways.items():
        yield identity, tags, point
    if unresolved:
        if diagnostics is None or len(diagnostics) + len(unresolved) > 100:
            raise ValueError(f'{len(unresolved)} candidate areas could not be assembled; examples: {list(unresolved.items())[:3]}')
        diagnostics.extend({'object': key, 'error': 'Candidate area was not assembled'} for key in sorted(unresolved))


def boundary(source, iso, output):
    with tempfile.TemporaryDirectory() as work:
        for identity, tags, geometry in osm_features(source, f'sparse_file_array,{work}/nodes', boundary_iso=iso):
            if tags.get('ISO3166-2') == iso and geometry.geom_type in ('Polygon', 'MultiPolygon'):
                output.write_text(json.dumps({'type': 'Feature', 'properties': {'osm_id': identity, 'iso': iso, 'name': tags.get('name'), 'source': 'OpenStreetMap'}, 'geometry': geometry.__geo_interface__}, ensure_ascii=False))
                return
    raise ValueError(f'Boundary {iso} not found; use an extract containing the complete boundary')


def create_db(path):
    db = sqlite3.connect(path)
    db.executescript('''
    PRAGMA user_version=1;
    CREATE TABLE poi(id INTEGER PRIMARY KEY, osm_id TEXT NOT NULL UNIQUE, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, tags_json TEXT NOT NULL);
    CREATE TABLE poi_category(poi_id INTEGER REFERENCES poi(id), category TEXT NOT NULL, PRIMARY KEY(poi_id, category));
    CREATE INDEX category_lookup ON poi_category(category, poi_id);
    CREATE VIRTUAL TABLE poi_search USING fts4(search_text, tokenize=unicode61);
    CREATE VIRTUAL TABLE poi_geo USING rtree(id, min_lon, max_lon, min_lat, max_lat);
    ''')
    return db


def build(source, region_path, output, revision, data_date, source_url):
    start = time.monotonic()
    datetime.date.fromisoformat(data_date)
    with osmium.io.Reader(str(source)) as reader:
        snapshot = reader.header().get('osmosis_replication_timestamp')
    if snapshot and snapshot[:10] != data_date:
        raise ValueError(f'Source snapshot is {snapshot}; supplied data date does not match')
    config = json.loads(region_path.read_text())
    for key in ('id', 'name', 'country', 'continent', 'boundary'):
        if not config.get(key):
            raise ValueError(f'Missing region field: {key}')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]*', config['id']) or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9.-]*', revision):
        raise ValueError('Unsafe region ID or revision')
    document = json.loads((region_path.parent / config['boundary']).read_text())
    polygon = shape(document['geometry'] if document.get('type') == 'Feature' else document)
    if polygon.geom_type not in ('Polygon', 'MultiPolygon') or not polygon.is_valid or polygon.is_empty:
        raise ValueError('Region boundary must be a valid nonempty polygon')
    prepare(polygon)
    output.mkdir(parents=True, exist_ok=True)
    target = output / f'{config["id"]}-{revision}.zip'
    if target.exists() or target.with_suffix('.json').exists():
        raise FileExistsError(f'Immutable pack already exists: {target}')
    counts = Counter()
    diagnostics = []
    with tempfile.TemporaryDirectory(dir=output) as work:
        work = Path(work)
        db_path = work / 'region.sqlite'
        db = create_db(db_path)
        try:
            for identity, tags, geometry in osm_features(source, f'sparse_file_array,{work}/nodes', diagnostics=diagnostics):
                types = categories(tags)
                if not types:
                    continue
                point = geometry if geometry.geom_type == 'Point' else geometry.representative_point()
                if not polygon.covers(point):
                    continue
                if not (-180 <= point.x <= 180 and -90 <= point.y <= 90):
                    raise ValueError(f'Invalid coordinates: {identity}')
                name = tags.get('name') or tags.get('name:en') or ''
                # Empty names are rendered with localized category labels by the client.
                cursor = db.execute('INSERT INTO poi(osm_id,name,lat,lon,tags_json) VALUES(?,?,?,?,?)', (identity, name, point.y, point.x, json.dumps(tags, ensure_ascii=False, sort_keys=True)))
                row = cursor.lastrowid
                db.executemany('INSERT INTO poi_category VALUES(?,?)', ((row, t) for t in types))
                db.execute('INSERT INTO poi_search(docid,search_text) VALUES(?,?)', (row, search_text(tags, types)))
                db.execute('INSERT INTO poi_geo VALUES(?,?,?,?,?)', (row, point.x, point.x, point.y, point.y))
                counts.update(types)
            db.commit()
            total = db.execute('SELECT count(*) FROM poi').fetchone()[0]
            if not total:
                raise ValueError('Refusing to publish an empty region')
            if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise ValueError('SQLite integrity check failed')
            db.execute('VACUUM')
        finally:
            db.close()
        metadata = {k: config[k] for k in ('id', 'name', 'country', 'continent')}
        metadata.update(
            format_version=1,
            generator_version='0.1.0',
            category_rules_sha256=file_hash(ROOT / 'categories.json'),
            revision=revision,
            data_date=data_date,
            source_timestamp=snapshot or None,
            source_url=source_url,
            source_sha256=file_hash(source),
            boundary_sha256=file_hash(region_path.parent / config['boundary']),
            poi_count=total,
            source_geometry_failures=diagnostics,
            quality_review_required=bool(diagnostics),
            categories=dict(counts),
            database_bytes=db_path.stat().st_size,
            region_bounds=list(polygon.bounds),
            region_geometry=polygon.__geo_interface__,
            search_name=normalize(config['name']),
            licence='ODbL-1.0',
        )
        (work / 'metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, sort_keys=True, indent=2))
        (work / 'attribution.txt').write_text(ATTRIBUTION)
        archive = work / 'pack.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for filename in ('region.sqlite', 'metadata.json', 'attribution.txt'):
                z.write(work / filename, filename)
        os.link(archive, target)  # Atomic create-if-absent; concurrent builds cannot replace a pack.
    report = dict(
        metadata,
        file=target.name,
        download_bytes=target.stat().st_size,
        sha256=file_hash(target),
        elapsed_seconds=round(time.monotonic() - start, 3),
        peak_rss_bytes=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        * (1 if sys.platform == 'darwin' else 1024),
        publication_status='local-unsigned-prototype',
    )
    target.with_suffix('.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    return report


def file_hash(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    b = commands.add_parser('boundary', help='Extract an administrative boundary from local OSM data')
    b.add_argument('--source', type=Path, required=True)
    b.add_argument('--iso', required=True)
    b.add_argument('--output', type=Path, required=True)
    p = commands.add_parser('build', help='Build a local region pack; never uploads')
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--region', dest='region_path', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--revision', required=True)
    p.add_argument('--data-date', required=True, help='Actual source snapshot date, not build date')
    p.add_argument('--source-url', required=True)
    args = vars(parser.parse_args())
    command = args.pop('command')
    if command == 'boundary':
        boundary(**args)
    else:
        print(json.dumps(build(**args), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
