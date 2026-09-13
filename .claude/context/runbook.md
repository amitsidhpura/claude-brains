# Runbook

## Re-audit `docs/feature-checklist.md` against a new CLI / extension version
Used 2026-08-17 (2.1.222→233), 2026-08-23 (233→241), 2026-08-28 (246→250), 2026-08-30 (250→251). Every MARK is measured;
the public changelog (step 3b) supplies LEADS only — a note never changes a row on its own. Gotchas § "Testing, probes and
sandboxes" has the traps (extraction is the only extension diff base; whitelist Sets don't prove acceptance).

1. **Versions on disk**: `claude --version`; `ls ~/.local/share/claude/versions/`;
   `ls ~/.vscode/extensions/ | grep claude`.
2. **Re-extract `reference/anthropic-claude-code/`** (not in git; moved from `vscode/` 2026-08-29):
   `rsync -a --delete --exclude resources/native-binary/ --exclude resources/audio-capture/
   ~/.vscode/extensions/anthropic.claude-code-<ver>-linux-x64/ reference/anthropic-claude-code/`.
3. **Baseline no longer on disk?** Download it:
   `https://marketplace.visualstudio.com/_apis/public/gallery/publishers/anthropic/vsextensions/claude-code/<ver>/vspackage?targetPlatform=linux-x64`
   — the response is a gzip-wrapped vsix (`gunzip`, then `unzip`); `extension/extension.js`,
   `extension/package.json`, and `extension/resources/native-binary/claude` (the CLI binary).
   Worked 2026-08-30 for 2.1.250 (~99 MB gunzipped; unzip only `extension/extension.js`,
   `extension/package.json`, `extension/webview/*`, `extension/claude-code-settings.schema.json`).
   The native binary doubles as the OLD-CLI BASELINE when `~/.local/share/claude/versions/` no
   longer holds it — worked 2026-09-04: the 2.1.251 vsix's binary ran the baseline `initialize`
   and subtype extraction for the 2.1.260 audit. While there, run the backlog's binary-grep
   watch-items (e.g. the `update_settings` key allowlist — grep near "update_settings keys not
   allowed").
   Extra cheap diffs that catch what the `case` diff misses: `tool("…")` registrations, `tengu_*`
   gates, the settings schema (`jq -S` both sides), and the webview's readable sentences (a
   `comm` of quoted strings ≥ 25 chars with ≥ 3 words — minifier renames drown a plain string diff).
3b. **Changelog leads** (added 2026-09-13): `git -C reference/claude-code-log pull` (clone of
   `github.com/anthropics/claude-code`, not in git; holds `CHANGELOG.md` + `feed.xml`, no binaries), then
   read every entry between the last audited version and the current one — `[VSCode]`-tagged lines are the
   extension, untagged lines the CLI. Sort each line into three piles: **By design** under the scope rule
   (Hooks / Permission-rules / MCP dialogs, account screens → a ➖ row needs only existence, no probe);
   **candidate row** → a probe target for steps 4-7 (behaviour changes that reuse existing labels — chip
   X replacing Hide, prompt fold button, flat model list — are invisible to the `case`/subtype diffs and
   this is the only place they surface); **no-op**. The row's evidence cites the measurement, never the
   note; record the version range read in the checklist header.
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

## Iterate a webview change against the live harness (the 5.6 polish loop, 2026-08-23)
Eight rounds ran this loop; each takes ~3 minutes:
1. **Fixture first**: add the new DISCRIMINATING asserts to the fixture while the sandbox still
   runs the PRE-change build — running them now is a free negative control ("sequence a fix so
   its control is free", conventions.md). Every new assert must be SEEN failing.
2. Apply the CSS/JS change; `cat plugin/src/main/resources/webview/js/*.js > /tmp/all.js &&
   node --check /tmp/all.js`.
3. Restart the sandbox (resource-only changes need only a runIde restart):
   `pkill -f 'run[I]de'` in its OWN Bash call (self-match trap, gotchas), then background
   `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222 --args="$HOME/Sites/claude-brains-testing"`.
4. Wait for CDP on 9222 and **verify the build BY CONTENT** (eval a changed token — a computed
   style, `typeof newFunction`) before trusting any run; the port alone proves nothing.
5. `python3 tools/live_harness.py 53` → green, then the FULL harness for regressions.
6. Update the checklist §16 counts; leave the sandbox up for the user's hands-on pass.
