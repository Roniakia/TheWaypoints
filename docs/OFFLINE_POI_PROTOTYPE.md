# Skåne offline POI prototype results

Measured locally on 2026-10-21, on branch `codex/poi-data-pipeline`.
This is a server/data feasibility result; the Android feature is not implemented.

## Inputs and reproducibility

- Source: Geofabrik Sweden OSM PBF, snapshot `2026-10-20T20:23:47Z`.
- Source bytes: 927,264,351.
- Source SHA-256: `6e3449ca4b49bac530fffc1bea789e2688dcf76a1f7aad95eac704186f715e53`.
- Boundary: Skåne län, OSM relation 54409, ISO 3166-2 SE-M.
- Boundary SHA-256: `76186c15bbb6b1f9642be79536ca41a20096427f4ae263d5d8572d991e508416`.
- Generator: 0.1.0; category-rule SHA-256: `507a6c21a828b2667761a6f1e3fbc60a964220e10b958e5576f050b22040a44f`.
- Environment: Python 3.14.7, SQLite 3.53.4, Darwin arm64.
- Hardware model/RAM could not be read under the sandbox; no particular laptop
  CPU or installed RAM specification is asserted.

Commands and environment setup are in `server/poi_packs/README.md`. Generated packs,
source extracts and signing keys are excluded from Git. No production signing key,
tag, release, upload, schedule or physical-device installation was created.

## Final candidate

| Measurement | Result |
| --- | --- |
| Revision | `2026-10-prototype.2` |
| POIs | 19,159 |
| Unnamed POIs retained | 13,821 |
| Compressed pack | 2,315,422 bytes (2.32 MB) |
| SQLite database | 7,655,424 bytes (7.66 MB) |
| Pack generation | 146.512 seconds |
| Process peak RSS | 2,892,857,344 bytes (2.89 GB) |
| Temporary node index | Approximately 1.9 GB observed; not an instrumented peak |
| Boundary extraction | Approximately 79 seconds, measured separately |

Generation excludes source download and boundary extraction. The initial download
was approximately 223 seconds on this connection. Peak RSS includes mapped index
pages and is process-specific. These numbers are measurements of this run, not
minimum requirements or Karoo performance guarantees.

Pack SHA-256: `afba3859c838dbee016ff72c375977482170735496c2119801a09555b856a566`.

## Warm local query results

Text queries fetch a first page of up to 50 rows and run 100 times per term.
No network or Karoo services are involved. The 50-row benchmark page is not a
storage/search-result cap.

| Query | Median | Maximum |
| --- | --- | --- |
| `water` | 0.037 ms | 0.169 ms |
| `coffee` | 0.042 ms | 0.09 ms |
| `bike repair` | 0.157 ms | 0.249 ms |
| `wc` | 0.041 ms | 0.081 ms |
| `malmo` | 0.042 ms | 0.107 ms |

A fixed 5 km Malmö query returns 4,295 POIs, including benches/parking,
using the RTree index and straight-line distance sorting. Across 20 repetitions,
median latency was 6.325 ms, maximum 6.874 ms.

This does not measure cold start, Android SQLite compatibility, multiple-region
merging, adaptive global-nearest pagination, or real-device connectivity.

## Coverage examples

- `water.drinking_water`: 136.
- `toilets.toilets`: 686.
- `food.cafe`: 517.
- `bicycle.shop`: 104.
- `bicycle.repair`: 74.
- `bicycle.pump`: 159.
- `rest.shelter`: 2,637.
- `rest.bench`: 5,054.
- `bicycle.parking`: 2,855.

Category memberships overlap; do not sum them as unique places. Dense types remain
in packs but should not dominate the app's default filters.

## Quality and limitations

One source-wide invalid swimming-area multipolygon is recorded as area 32523305,
corresponding to OSM relation 16261652. It cannot be assigned to a region without
valid geometry; this does not establish that it lies within Skåne. The pack retains
`quality_review_required=true`; catalogue signing requires explicit acknowledgement.

The audit handles assembled areas arriving before their original relation and
retains closed linear features without fabricating polygons. Invalid/unassembled
area omissions are reported; over 100 failures abort generation.

Generic transliteration is supplemented by OSM English/alternative names but does
not guarantee every conventional spelling. Category synonyms currently cover
English and limited Swedish. Complete supported-language aliases, minimum-client
compatibility, Karoo SQLite checks, shared country-pass optimization and secure
app installation remain future work. No production distribution is ready yet.

## Verification and next milestone

Ten Python tests pass. The existing Android unit-test Gradle target succeeds using
cached outputs; no Android implementation changed. `git diff --check` passes.
An unsigned local catalogue was assembled successfully against the final pack.

The measurements support proceeding with the pack format and Android read-only
import/search prototype on a separate app branch. First verify FTS4/RTree support
on the emulator/Karoo SQLite baseline, then implement bookmark migration,
download lifecycle and the agreed compact interface. Review category defaults and
source quality before expanding beyond Sweden or publishing anything.
