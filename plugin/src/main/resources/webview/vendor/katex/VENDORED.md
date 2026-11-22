# KaTeX 0.19.1 (vendored)

Math rendering for the chat panel (feature register row 1.34). MIT — see `LICENSE`.

- Source: the npm package `katex@0.19.1`, files copied unchanged from its `dist/`:
  `katex.min.js`, `katex.min.css`, `fonts/*.woff2` (the 20 woff2 files; the woff and ttf
  fallbacks are not shipped — the panel's browser is Chromium).
- Loaded by `ui/WebviewAssets.kt`: the script in its own `<script>` block, the stylesheet with
  every font inlined as a data URI (the page is loaded with `loadHTML` and has no base URL, so a
  font file could never resolve).
- To update: replace these files from the new package's `dist/`, change the version here and in
  `WebviewAssets.kt`, run `./gradlew test` and the live harness (fixture 111).
