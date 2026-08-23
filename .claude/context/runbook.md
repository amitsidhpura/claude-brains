# Runbook

## Re-audit `docs/feature-checklist.md` against a new CLI / extension version
Used 2026-08-17 (2.1.222→233) and 2026-08-23 (233→241). Everything is measured, nothing read
from release notes. Gotchas § "Auditing the reference clients" has the traps.

1. **Versions on disk**: `claude --version`; `ls ~/.local/share/claude/versions/`;
   `ls ~/.vscode/extensions/ | grep claude`.
2. **Re-extract `vscode/`** (not in git): `rsync -a --delete --exclude resources/native-binary/
   --exclude resources/audio-capture/ ~/.vscode/extensions/anthropic.claude-code-<ver>-linux-x64/ vscode/`.
3. **Baseline no longer on disk?** Download it:
   `https://marketplace.visualstudio.com/_apis/public/gallery/publishers/anthropic/vsextensions/claude-code/<ver>/vspackage?targetPlatform=linux-x64`
   — the response is a gzip-wrapped vsix (`gunzip`, then `unzip`); `extension/extension.js`,
   `extension/package.json`, and `extension/resources/native-binary/claude` (the CLI binary).
4. **Extension diff**: package.json `contributes` (commands/configuration/keybindings/views)
   old-vs-new; `grep -o 'case"[a-z_0-9]*"' extension.js | sort -u` both sides, `comm -13` under
   `LC_ALL=C`. New labels get context reads (`grep -o '.\{120\}case"<w>".\{160\}'`) — most are
   enum values or internals, not host messages.
5. **CLI typed-subtype diff**: find the schema helper per build by matching
   `subtype:<ident>\("initialize"\)`, then extract all `subtype:<helper>\("([a-z_0-9]+)"\)`.
6. **Command roster**: a LIVE bare `initialize` control request over stream-json, run in
   `~/Sites/claude-brains-testing` (this repo's /context skill shadows the built-in). Never read
   hints out of the binary — `/goal` carries two records there and the wire sends the hintless
   one. Diff names AND argumentHints (a hint change flips the panel's insert-vs-run pick rule).
7. **Probe acceptance** of any subtype a checklist row hinges on: send it as a
   `control_request` over stdio and read the `control_response`. Whitelist Sets in the binary
   don't answer this (gotchas). Do NOT probe `rewind_files` (mutates files).
8. **Fold results into rows** — ids stable, re-derive ids from the file (never paraphrase from
   memory, conventions.md); update the header References/date, §2's "unchanged in <ver>", and
   the [NEW] legend. Stale-count sweep of §16 while there.
