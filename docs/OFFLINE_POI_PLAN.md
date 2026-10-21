# Offline POIs implementation agreement

Status: implementation started, 2026-10-21. Server work lives on
`codex/poi-data-pipeline`; Android work must start on its own branch from main.
On 2026-10-21 the user authorized committing and publishing the prototype and
continuing implementation. No physical device installation or schedule activation
is authorized. Source work continues through feature PRs to protected main.

## Product requirements

- No screen titles: menu button and search input share a compact top row.
- Collapsible navigation contains POIs and Regions.
- First launch invites regional downloads and supports skipping setup.
- Regions: downloaded first, recommended second, remaining continent/country/region
  tree last. No duplicate entries. Search applies to all sections, preserves parent
  context, and may hide downloaded regions. Hidden selections persist with count.
- Parent selections support all/partial states. Nothing selected means Update all
  downloaded; any selection means Update selected, downloading missing regions too.
  Unchanged packs are skipped. Region removal is separate and preserves bookmarks.
- Recommend containing region and nearest supported regions using local boundary
  data. Locale country is fallback, not a substitute for position. Never auto-select.
- POIs: search downloaded data, current-style list, bookmarks, native map handoff,
  default nearest sorting, alphabetical fallback without position.
- Round lower-right filter opens expandable groups/subtypes with all/partial
  checkboxes, Apply with count, and Reset. No name field inside filters.
- Search names, English/alternative names, transliteration, categories and synonyms.
  Use original names in display and localized type labels for unnamed features.
  Search respects active type filters. Retain filters on return from details.
- Include all supported categories in packs; default UI selection excludes noisy
  types such as benches/parking. One POI can have multiple categories.
- Offline search does not provide offline map rendering or route calculation.
- Existing Photon search remains; its final UI entry point is still undecided.

## Delivery sequence

1. Specify category rules, administrative region mapping, data identities and format.
2. Generate Skåne prototype; measure source/pack bytes, RAM, time, temporary disk,
   counts, multilingual search and geometry quality. Review before scaling coverage.
3. Build generation validation, signed catalogue and explicit publication tooling.
4. Android storage/search and bookmark migration on separate app branch.
5. Download queue, progress, cancellation/recovery, verification, atomic replacement.
6. Compact screens, selection, recommendations, search and expandable filters.
7. Unit/emulator/lint/build checks, then propose an authorized Karoo beta.

## Distribution design

Generate offline on a laptop/server from country OSM extracts. Monthly/manual runs;
no running request backend. Publish in existing app repository GitHub Releases using
`poi-data-<revision>` tags marked not latest, preserving app latest-release manifest.
Never place packs or source extracts in Git history. Upload immutable packs before
switching catalogue. Data revisions and pack formats are independent of app version.

Catalogue: stable region IDs, names/aliases/transliterations, hierarchy and boundaries
for recommendations, download/installed sizes, POI count, actual OSM snapshot date,
revision, minimum compatible app/format, immutable URL and SHA-256. Authenticate
catalogue with a pinned-key signature. Keep previous compatible releases available.

Separate read-only regional databases from mutable app bookmarks/install state.
OSM identity includes object type and ID. Deduplicate across overlapping regions;
preserve bookmark snapshots when packs disappear. Verify compatibility, signatures,
checksums, coordinates and database integrity; failed updates retain old data.
Reserve space for old pack plus compressed and unpacked replacement.

## Prototype status and limitations

`server/poi_packs` builds local, unsigned packs from local OSM sources. It is not yet
an app-consumable published format. Local catalogue assembly and detached Ed25519 signing are implemented; publication
and Android code do not exist. FTS4 and RTree are provisional until checked against Karoo SQLite support.
Generic transliteration does not guarantee an English exonym or every romanization:
index OSM English/alternative names too, then add curated language aliases as needed.
Nearest search must merge geographic candidates globally, not just sort a first page.

## Measured first milestone

Skåne prototype complete: 19,159 POIs, 2.32 MB compressed, 7.66 MB database,
146.512 seconds generation and 2.89 GB process peak RSS on the measured host.
See [OFFLINE_POI_PROTOTYPE.md](OFFLINE_POI_PROTOTYPE.md) for inputs, checks,
query measurements and limitations. Ten Python tests pass. Local unsigned catalogue
assembly succeeds. Android integration, publication and scheduling remain pending.

## Data candidate publication preparation

User authorized publication on 2026-10-21. Tag: `poi-data-2026-10-prototype.2`,
prerelease, explicitly not latest. The final prototype pack bytes are unchanged.
Catalogue is signed with a separate persistent Ed25519 identity; only the public
key is tracked under `server/poi_packs/config/poi-data-public.pem`. Private key
remains outside the repository and needs a private backup for future updates.
The one documented source geometry omission is acknowledged for this prototype.
