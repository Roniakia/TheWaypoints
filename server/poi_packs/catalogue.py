"""Assemble and authenticate a local catalogue. Does not upload assets."""
import argparse
import base64
import json
import re
import zipfile
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from packs import ATTRIBUTION, file_hash


def assemble(reports, asset_base_url):
    if not asset_base_url.startswith('https://'):
        raise ValueError('Asset URL must use HTTPS')
    regions = []
    seen = set()
    for path in reports:
        report = json.loads(path.read_text())
        identity = report['id']
        if identity in seen:
            raise ValueError(f'Duplicate region {identity}')
        seen.add(identity)
        filename = report['file']
        if not re.fullmatch(r'[a-zA-Z0-9.-]+\.zip', filename):
            raise ValueError('Unsafe pack filename')
        pack = path.parent / filename
        if file_hash(pack) != report['sha256'] or pack.stat().st_size != report['download_bytes']:
            raise ValueError(f'Pack differs from build report: {identity}')
        with zipfile.ZipFile(pack) as z:
            if sorted(z.namelist()) != ['attribution.txt', 'metadata.json', 'region.sqlite']:
                raise ValueError('Unexpected pack contents')
            metadata = json.loads(z.read('metadata.json'))
            if any(metadata[k] != report[k] for k in metadata):
                raise ValueError('Pack metadata differs from report')
        regions.append({k: v for k, v in report.items() if k not in ('elapsed_seconds', 'publication_status')})
        regions[-1]['url'] = asset_base_url.rstrip('/') + '/' + filename
    if not regions:
        raise ValueError('Empty catalogue')
    return {'catalogue_format': 1, 'attribution': ATTRIBUTION, 'regions': sorted(regions, key=lambda x: x['id'])}


def sign(payload, key_path):
    key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise ValueError('An Ed25519 signing key is required')
    return base64.b64encode(key.sign(payload))


def verify(payload, signature, public_key_path):
    key = serialization.load_pem_public_key(public_key_path.read_bytes())
    if not isinstance(key, Ed25519PublicKey):
        raise ValueError('An Ed25519 public key is required')
    key.verify(base64.b64decode(signature, validate=True), payload)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reports', nargs='+', type=Path, required=True)
    p.add_argument('--asset-base-url', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--acknowledge-geometry-warnings', action='store_true', help='Explicit review acknowledgement before signing packs with geometry omissions')
    p.add_argument('--private-key', type=Path, help='Existing private PEM key, kept outside repository')
    args = p.parse_args()
    if args.output.exists() or args.output.with_suffix('.json.sig').exists():
        raise FileExistsError('Catalogue output already exists; use a new revision directory')
    document = assemble(args.reports, args.asset_base_url)
    if args.private_key and not args.acknowledge_geometry_warnings and any(r.get('quality_review_required') for r in document['regions']):
        raise ValueError('Review source_geometry_failures before signing; explicit acknowledgement required')
    payload = (json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()
    signature = sign(payload, args.private_key) if args.private_key else None
    args.output.write_bytes(payload)
    if signature:
        args.output.with_suffix('.json.sig').write_bytes(signature)
    else:
        print('UNSIGNED local catalogue; do not distribute to clients')


if __name__ == '__main__':
    main()
