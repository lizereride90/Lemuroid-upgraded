# Game Sources

A self-contained catalog system built into Lemuroid. Users import a **source package** (a ZIP
file), browse its game catalog, and download games directly into the library — no manual file
management, no extra storage permission.

The feature is:

- **Data-only.** A source package is a plain ZIP of JSON + image covers. Nothing in it is ever
  executed, interpreted, or loaded as code.
- **Isolated.** Every source lives in its own `Sources/source_NNN/` directory. Extraction is
  guarded against path traversal ("zip-slip") and absolute paths.
- **Capped.** Zips, catalogs and archive expansion are size-limited so a source can never eat the
  device (see [Limits](#limits)).
- **ICS/Https-only.** Every download URL must be `http(s)`; `file:`, `javascript:`, etc. are
  rejected.

## Where things live

All paths are under the app-specific external directory. No storage permission is required.

| What | Location | Notes |
| --- | --- | --- |
| Installed sources | `…/Android/data/<pkg>/files/Sources/source_001/`, `source_002/`, … | New id allocated on install |
| Source registry | `…/Android/data/<pkg>/files/Sources/sources.json` | JSON index of installed sources |
| Downloaded games | `…/Android/data/<pkg>/files/Games/<system>/` | `<system>` is the Lemuroid dbname (`ps2`, `psp`, `psx`, `nds`, …) |

Downloaded games land in `Games/<system>/` and the normal library scan indexes them automatically.
Deleting a source **never** deletes already-downloaded games.

## Package format

A source package is a ZIP. Its root must contain:

```
source.json      # manifest (required)
games.json       # catalog (path may be customized by the manifest)
covers/...       # optional cover art referenced by the catalog
```

### source.json

```json
{
  "name": "Homebrew Corner",
  "version": "1.0",
  "author": "Example Author",
  "description": "Curated freeware and homebrew games.",
  "catalog": "games.json",
  "sourceUrl": "https://example.com/homebrew-corner.zip"
}
```

Only `name` is required. `sourceUrl` is optional: when present, "Refresh source" re-downloads
this ZIP and replaces the source content.

### games.json

```json
{
  "games": [
    {
      "id": "jetpack-rabbit",
      "title": "Jetpack Rabbit",
      "system": "PlayStation 2",
      "format": "iso",
      "size": 1386923008,
      "cover": "covers/jetpack-rabbit.png",
      "description": "A free homebrew platformer.",
      "downloadUrl": "https://example.com/games/jetpack-rabbit.iso",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
    }
  ]
}
```

Required per game: `id`, `title`, `system`, `format`. Optional: `size`, `cover`,
`description`, `downloadUrl`, `sha256`, `version`.

### Rules

- `system` is resolved loosely: exact dbname (`ps2`), enum names (`PS2`), display names
  (`PlayStation 2`), aliases (`PS1` → `psx`) and a contained-dbname fallback all work.
  Unknown systems are skipped, never crash the app.
- `format` is a bare alphanumeric extension (`iso`, `bin`, `chd`, `cue`, `nds`, …).
- `cover` is a relative path inside the package, resolved against the source directory.
- `downloadUrl` must be `http(s)`. `sha256` must be 64 hex chars; when present it is verified
  after download.
- Filenames are sanitized (`sanitizeFileName`) before anything is written to disk.
- The catalog must not contain duplicate `id`s.

## Limits

| Thing | Limit |
| --- | --- |
| `source.json` size | 64 KB |
| Catalog file size | 16 MB |
| Total uncompressed package | 512 MB |
| Archive entries | 10,000 |
| Games per catalog | 20,000 |
| Name/description lengths | 128 / 20,000 chars |

## Downloads

- Files stream straight to `Games/<system>/<title>.<format>.part` — never into memory.
- Range resume is attempted from an existing `.part` file; servers that ignore `Range` restart
  from zero.
- Transient failures (network errors, HTTP 408/429/5xx) retry with backoff (1s–30s, up to 4 tries).
- Size and optional SHA-256 are verified before the file is renamed into place.
- A foreground service (notification) keeps downloads running when the app is backgrounded.
  Downloads survive leaving the screen; you can pause, resume and cancel per game.

## Android APIs used

Background download progress, pause/resume and cancellation live in
`GameDownloadCoordinator` (app-scoped). The foreground `GameSourceDownloadService` uses
`NotificationCompat` + the `dataSync` foreground service type. The system file picker
(`ActivityResultContracts.OpenDocument`) selects the source ZIP. No storage permissions, no
SD-card writes outside app storage.

See the reference package in [`example-source/`](../example-source/) for a complete,
importable source.