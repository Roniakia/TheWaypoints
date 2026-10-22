# Regional OSM POI pack generator — prototype

Reusable Python 3.11+ CLI, independent of Codex and Android. Run locally or on a
server; no database service or GPU needed. It never uploads or publishes.

## Setup

```sh
python3 -m venv server/poi_packs/.venv
server/poi_packs/.venv/bin/pip install -r server/poi_packs/requirements.txt
```

## Skåne prototype

Obtain the Sweden PBF from https://download.geofabrik.de/europe/sweden.html.
Keep source extracts outside tracked directories. Record the actual snapshot date
from the source metadata; do not substitute the download date.

```sh
python server/poi_packs/packs.py boundary \
  --source /path/to/sweden.osm.pbf --iso SE-M \
  --output server/poi_packs/config/se-skane.geojson
python server/poi_packs/packs.py build \
  --source /path/to/sweden.osm.pbf \
  --region server/poi_packs/config/se-skane.json \
  --revision 2026-10-prototype.1 --data-date ACTUAL-SNAPSHOT-DATE \
  --source-url https://download.geofabrik.de/europe/sweden-latest.osm.pbf \
  --output server/poi_packs/output
```

Use the virtual environment's Python for these commands. Boundary extraction needs
complete administrative relation geometry. It reads OSM data, not an external
geocoder. Generated boundary geometry must be reviewed before distribution.

The JSON report records counts, compressed/database size, source/boundary SHA-256,
and elapsed generation time. Use `/usr/bin/time -l` on macOS or `/usr/bin/time -v`
on Linux to measure peak memory. Temporary disk indexes are deleted on completion.

## Current format

ZIP with `region.sqlite`, `metadata.json`, `attribution.txt`. SQLite user_version 1:

- `poi`: stable typed OSM ID, original display name, coordinates, original tags.
- `poi_category`: many-to-many subtype memberships and category lookup index.
- `poi_search`: FTS4 normalized aliases, names and transliterations; docid = poi.id.
- `poi_geo`: RTree of point locations; id = poi.id.

FTS4 is intentionally conservative for Android compatibility, but device support
still requires verification. Never interpolate user input as raw FTS syntax.

Points retain coordinates; polygon POIs use an interior representative point;
open tagged ways use a representative point on their geometry. Closed ways prefer
assembled polygons, with a line-point fallback when no area is emitted. Region membership
is based on this point, including the boundary. Non-area relations are not yet
supported. Invalid candidate geometries and unassembled candidate areas are recorded in
`source_geometry_failures` and mark the pack as requiring quality review; more than
100 failures abort the build. Boundary extraction remains strict. Failures are
source-wide because missing geometry cannot reliably be assigned to a region. Different OSM objects representing one real place are not yet conflated.

Unnamed places retain an empty name for the app to label by localized category.
Private, explicitly disused/abandoned/demolished places are excluded. Customer-only
access, fees and opening hours remain in tags. No opening-hours evaluation exists.

`categories.json` contains OR rules. Special composite rules cover bicycle charging
and vending. Aliases currently cover English plus a small Swedish vocabulary, not
all app languages. Generic transliteration plus OSM multilingual aliases cannot
guarantee every accepted romanization. These limitations must be addressed before
claiming complete multilingual support.

Output revisions are immutable: an existing ZIP is never overwritten. Build into a
new revision after changing source, rules or format. Pack ZIPs are authenticated through the separately signed catalogue. No secure
Android installation flow exists yet. The original local reports are prototype
reports; publication uses a signed catalogue verified against the tracked public key.

## Tests

```sh
PYTHONPATH=server/poi_packs python -m unittest discover -s server/poi_packs/tests -v
```

Tests exercise a synthetic OSM source through area assembly, boundary membership,
SQLite/FTS/RTree creation, category rules, multilingual aliases, and immutable output.
They do not establish real Swedish coverage or Karoo performance.

## Sources and licensing

Source data: © OpenStreetMap contributors, ODbL 1.0.
https://www.openstreetmap.org/copyright
Generated databases carry attribution and licence links. Preserve provider notices.
Dependency licences are separate from the OSM data licence.

Full requirements and later milestones: ../../docs/OFFLINE_POI_PLAN.md.

## Local catalogue and signatures

```sh
python server/poi_packs/catalogue.py \
  --reports server/poi_packs/output/se-skane-2026-10-prototype.1.json \
  --asset-base-url https://github.com/Roniakia/TheWaypoints/releases/download/poi-data-2026-10 \
  --output server/poi_packs/output/catalog.json
```

This validates ZIP hashes and metadata before assembling the catalogue. Without
`--private-key /external/path/to/key.pem` it explicitly emits an unsigned prototype.
With an existing Ed25519 private PEM key it also writes a detached base64 signature
in `catalog.json.sig`. Private keys must stay outside the repository; no distribution
key is generated automatically. Test keys are ephemeral and cannot sign production
releases. Public-key pinning, key rotation, minimum app compatibility, compact/sharded worldwide catalogues, and the
publish-last command remain future work. Catalogue entries include full regional
boundary geometry for local recommendations in this prototype.

For a local text-search benchmark, unpack a pack in a temporary directory and run:

```sh
python server/poi_packs/benchmark.py /temporary/path/region.sqlite
```

This measures repeated warm text queries and a fixed 5 km Malmö spatial query
with straight-line distance sorting. It does not measure cold start, global nearest
pagination, Android compatibility or Karoo device performance.

## Repeatable acquisition and batch generation

```sh
python server/poi_packs/fetch.py \
  --url https://download.geofabrik.de/europe/sweden-latest.osm.pbf \
  --output /external/cache/sweden-2026-10.osm.pbf
python server/poi_packs/run.py \
  --source /external/cache/sweden-2026-10.osm.pbf \
  --regions server/poi_packs/config/se-skane.json \
  --revision 2026-10.1 --output server/poi_packs/output \
  --source-url https://download.geofabrik.de/europe/sweden-latest.osm.pbf
```

Fetch uses HTTPS, a temporary file, timestamp/header and content-length checks,
optional `--expected-sha256`, and create-if-absent installation. Interrupted fetches
are discarded; resumable acquisition is not implemented. Batch generation reads
the actual source snapshot date and refreshes each boundary from that same source.
It currently rescans the country for each region; optimize shared extraction before
large coverage. Scheduling these commands requires no Codex session. No scheduler
or publication task has been enabled.

Geometry omissions require explicit `--acknowledge-geometry-warnings` before
catalogue signing. This records an operator decision in the command, not permission
to publish. The first real Sweden source had an invalid swimming-area multipolygon;
all omissions and source-wide audit results must be reviewed before distribution.

## Data-signing identity

`config/poi-data-public.pem` is the public verification key for the first data
publication. Its SHA-256 is
`43294ee8ec4b93e2b219a03cca29d23bab5830aa16d0ae39da8468555f05c28f`.
The private key is stored outside Git and must be backed up privately. This
identity is independent of the Android APK signing key. Pin this public key in
the app; downloading a public key beside a signature does not establish trust.
