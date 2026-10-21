"""Repeatable local country-to-region batch. Explicit inputs; no publication."""
import argparse
import json
import tempfile
from pathlib import Path

import osmium

from packs import boundary, build


def run(source, regions, output, revision, source_url):
    with osmium.io.Reader(str(source)) as reader:
        snapshot = reader.header().get('osmosis_replication_timestamp')
    if not snapshot:
        raise ValueError('Source lacks snapshot timestamp; use packs.py with an independently verified date')
    reports = []
    for config_path in regions:
        config = json.loads(config_path.read_text())
        if not config.get('boundary_iso'):
            raise ValueError(f'Missing boundary_iso in {config_path}')
        with tempfile.TemporaryDirectory(dir=output) as work:
            work = Path(work)
            geometry = work/'boundary.geojson'
            boundary(source, config['boundary_iso'], geometry)
            config['boundary'] = geometry.name
            local_config = work/'region.json'
            local_config.write_text(json.dumps(config, ensure_ascii=False))
            reports.append(build(source, local_config, output, revision, snapshot[:10], source_url))
    return reports


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--regions', nargs='+', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--revision', required=True)
    p.add_argument('--source-url', required=True)
    args = vars(p.parse_args())
    args['output'].mkdir(parents=True, exist_ok=True)
    print(json.dumps(run(**args), ensure_ascii=False, indent=2))
