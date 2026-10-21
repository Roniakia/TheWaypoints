"""Download a fresh OSM PBF to a local path; never overwrites a previous extract."""
import argparse
import hashlib
import os
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

import osmium


def fetch(url, output, expected_sha256=None):
    if urlparse(url).scheme != 'https':
        raise ValueError('Source URL must use HTTPS')
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.osm.pbf', delete=False) as stream:
            temporary = Path(stream.name)
            request = Request(url, headers={'User-Agent': 'WaypointsKaroo-POI-generator/0.1'})
            digest = hashlib.sha256()
            size = 0
            with urlopen(request, timeout=60) as response:
                if urlparse(response.url).scheme != 'https':
                    raise ValueError('Insecure source redirect')
                length = response.headers.get('Content-Length')
                for block in iter(lambda: response.read(1024 * 1024), b''):
                    stream.write(block)
                    digest.update(block)
                    size += len(block)
                if length and size != int(length):
                    raise ValueError('Incomplete download')
            stream.flush()
            os.fsync(stream.fileno())
        if expected_sha256 and digest.hexdigest() != expected_sha256.lower():
            raise ValueError('Source checksum mismatch')
        with osmium.io.Reader(str(temporary)) as reader:
            snapshot = reader.header().get('osmosis_replication_timestamp')
        if not snapshot:
            raise ValueError('Source lacks snapshot timestamp')
        # Hard link gives create-if-absent semantics even if another fetch completed.
        os.link(temporary, output)
        return {'file': str(output), 'bytes': size, 'sha256': digest.hexdigest(), 'source_timestamp': snapshot}
    finally:
        if temporary:
            temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    import json
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--url', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--expected-sha256')
    print(json.dumps(fetch(**vars(p.parse_args())), indent=2))
