import tempfile
import io
import hashlib
import osmium
import unittest
from pathlib import Path
from unittest.mock import patch

from fetch import fetch


class FetchTest(unittest.TestCase):
    def test_existing_extract_is_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)/'source.osm.pbf'
            target.write_bytes(b'existing')
            with patch('fetch.urlopen') as network:
                with self.assertRaises(FileExistsError):
                    fetch('https://example.org/source.osm.pbf', target)
                network.assert_not_called()
            self.assertEqual(target.read_bytes(), b'existing')

    def test_insecure_url_is_rejected_before_download(self):
        with patch('fetch.urlopen') as network:
            with self.assertRaises(ValueError):
                fetch('http://example.org/source.osm.pbf', Path('/tmp/unused-waypoints-test'))
            network.assert_not_called()

    def test_valid_download_and_checksum_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture = root/'fixture.osm.pbf'
            header = osmium.io.Header()
            header.set('osmosis_replication_timestamp', '2026-10-20T20:23:47Z')
            with osmium.SimpleWriter(str(fixture), header=header) as writer:
                writer.add_node(osmium.osm.mutable.Node(id=1, location=(13, 55)))
            payload = fixture.read_bytes()
            def response():
                stream = io.BytesIO(payload)
                stream.url = 'https://example.org/source.osm.pbf'
                stream.headers = {'Content-Length': str(len(payload))}
                return stream
            target = root/'download.osm.pbf'
            with patch('fetch.urlopen', return_value=response()):
                report = fetch('https://example.org/source.osm.pbf', target, hashlib.sha256(payload).hexdigest())
            self.assertEqual(report['source_timestamp'], '2026-10-20T20:23:47Z')
            self.assertEqual(target.read_bytes(), payload)
            rejected = root/'rejected.osm.pbf'
            with patch('fetch.urlopen', return_value=response()):
                with self.assertRaises(ValueError):
                    fetch('https://example.org/source.osm.pbf', rejected, '0'*64)
            self.assertFalse(rejected.exists())
            self.assertEqual(sorted(x.name for x in root.iterdir()), ['download.osm.pbf', 'fixture.osm.pbf'])
