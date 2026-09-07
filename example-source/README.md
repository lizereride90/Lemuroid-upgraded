# Homebrew Collection — example Game Source

A ready-to-import reference package for the [Game Sources](../docs/Game-Sources.md) feature.

This package contains **only free homebrew entries with illustrative download URLs** — nothing
copyrighted. Import the whole folder as a ZIP inside the app:

```sh
cd <repo root>
zip -r homebrew-collection.zip example-source/. -x '*/.git/*' -x '*.DS_Store'
```

Then in Lemuroid → **Game Sources** → press `+` and pick `homebrew-collection.zip`.

Contents:

- `source.json` — manifest (`name` required)
- `games.json` — catalog (`id`, `title`, `system`, `format` required per game)
- `covers/` — placeholder cover art referenced by the catalog

The `sha256` values are all zeros on purpose: the demo download URLs do not resolve, so the
checksum is only there to show the field. Replace `downloadUrl`/`sha256` with real, legal files
before distributing your own source.