# Synthetic cross-platform POI fixture

`fixture.zip` was generated from `source.osm` using the Python generator at
`736150d73837247909d34f35e85cf0943f3f70ef` on `codex/poi-data-pipeline`.
It contains 66 synthetic POIs, including distinct node/1 and way/1 identities,
Ukrainian/Japanese names with English aliases, a cafe/bicycle-pump combination,
and 64 drinking-water points. No production region is bundled in the app.

`descriptor.json` records the generated archive hash and sizes. To regenerate,
use the server branch's pinned environment and `packs.build` with `source.osm`,
id `se-test`, name `Skåne test`, continent `Europe`, country `Sweden`, revision
`fixture-1`, data date `2026-10-21`, source URL `https://example.invalid/test`,
and a Polygon boundary `[[12,54],[14,54],[14,56],[12,56],[12,54]]`.
Update the descriptor from that build report because ZIP timestamps affect bytes.

These files are instrumentation-test assets only. Real Skåne verification uses
an optional external assets directory; see `docs/LOCAL_TESTING.md`.
