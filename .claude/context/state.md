# State

## Current focus
**2026-10-11 (twenty-ninth session, Linux): 0.16.0 RELEASED — `docs/release.md` end to end
(prep re-asserted from step 1 after the halt; `test buildPlugin verifyPlugin` 176 / 8-of-8
Compatible / 7 jars, baked notes re-read; gate held with the COMPLETE notes → "Go ahead please";
commit `1e4011d`, tag `v0.16.0`, pushed; `gh release create` clean, published with the asset;
asset `cmp` identical; feed serving 0.16.0; `marketplace-upload` completed success two seconds
after publish). Context save NOT committed (needs its own ask, conventions).**
- **Next release**: 0.16.1 for fixes only, 0.17.0 with a feature — the user's call.
- **Pending in the real PhpStorm: nothing** — both channels serve 0.16.0; the IDE replaces the
  disk-installed build (00:20 zip) on its next plugin check. Marketplace: **Approved** the same
  night (user's screenshot: IDE run on 2026.3 EAP + Compatible on 2026.3 EAP / 2026.2.3 / 2026.1.5;
  the API still said 0.15.0 at the time, the known lag). Description check on the Overview tab is
  the standing user errand.
- **Copy response control (MT-15.4b, 2026-10-11, committed `f754eac`)**: first child of `.blk`, floated right, hidden by
  opacity until the reply is hovered, icon-only brighten on hover, `.codeblock`/`.diff` clear it,
  `.blk > .blk-copy + *` margin 0 (`css/30-blocks.css`, `copyable()` in `20-markdown.js`, mockup
  mirrored). Decided with the user in three injected-CSS rounds in the sandbox (decisions.md).
  Fixture 96 step 9b (54 asserts), test 176, harness 1166→1172.
- **Shipped since v0.15.0, committed and pushed** (detail: checklist 6.6 / MT-11.8, journal
  twenty-seventh + twenty-eighth): 6.6 auto-include selection + Include open file (`cf8ee58`:
  `SelectionTracker.kt` → `__selection` → the pill; `#tglSel`/`#tglFile` switch rows; the panel
  writes the CLI's own `<ide_selection>` / `<ide_opened_file>` tag after the prompt, the CLI's
  `selection_changed` route being dead in stream-json mode; fixture 99, 69 asserts) and MT-11.8
  (`abcf060`: no summary for the CLI's zero-turn wake on `--resume`; fixture 100). Both hand-tested
  by the user in the real PhpStorm.
- **Pending the user**: nothing release-wise (0.16.0 is out) — 0.16.0 would be the version if asked (feature →
  minor; one feature since v0.15.0, commit `cf8ee58`). The real PhpStorm runs the 21:17 zip of this
  code, so nothing is pending there. The `· file` pill click is caret-only now (`select:false` on the open route,
  user's ask 2026-10-10; fixture 99 step 21b) — zip rebuilt after it.
- Earlier today, all committed and released in 0.15.0 (journal twenty-second → twenty-sixth for the
  detail): 9.9 retired + popup fit (`8324cd8`, `96e32d1`; fixtures 97 + 98), 15.4 export / copy
  response (`759c904`; fixture 96; every copy through `CopyPasteManager`), 1.30 midnight relay
  (`96315e0`), 11.7 measured + hand-tested (`a556a3a`), probe-transcript cleanup, extraction → 2.1.296.

**[DECIDE] rows:** none (At a glance: 99 ✅ · 0 ⬜ · 46 ➖ — "no open tasks", answered 2026-10-10).

**Pending in the real PhpStorm: nothing** — 0.15.0 is on both channels and the IDE replaces the
disk-installed 0.14.0 build (17:37 zip) on its next plugin check. **0.15.0 is the RELEASED version
(2026-10-10)**; the next release is 0.15.1 for fixes only, 0.16.0 with a feature (user's call,
`verifyPlugin` every release, budget 15 min for the build — gotchas § Build). The sandbox PhpStorm was left RUNNING on the final build
(CDP 9222; dies with the Claude Code process that launched it — `:9222` closed at `load` is normal).

## Open investigations
- **Where the user's Fable default comes from** — still unknown. A bare `initialize` on 2.1.296
  (this Max account) resolved `default` → `claude-sonnet-5-5`; the 2.1.270 binary said
  `claude-opus-5[1m]`. The panel shows what the CLI serves; it does not change what runs.
- **Model chip "Fable (1M)" with no ✓ row** (parked, backlog § Next up) — the switch that wrote
  tagged values is retired; fixture 97 pins that a persisted tagged value keeps its ✓.
- **Roster per PROCESS**: 2.1.293 terminal probe 11 rows (no `fable` alias), 2.1.296 13 WITH it —
  a flags-fetch timing lead, unproven.
- **The wake turn** (a completed background shell wakes the model): CHECKED 2026-10-10 — it draws
  its reply as a second text block in the same turn, no user bubble, busy ends normally.
- **4.10 has never met a real frame** — `window.__dialogSeen` + console warning watch it.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04) — waiting on the user's diagnostic.
- **Title tooltips never show on Linux JCEF** — backlog § Next up.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **1089** (fixtures to **98**); `./gradlew test` **169**.
- Sandbox PhpStorm 2024.2.6: `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"` (background, from the repo root). `runIde` blocks
  until the IDE exits. Stop it with `pgrep -f 'transformed/PhpStorm-2024.2.[6]' | xargs -r kill`
  (never `pgrep -x claude`), wait for `:9222` to vanish with `ss -ltn` in a bounded `until` loop
  (the bash `/dev/tcp` idiom is NOT a port check under this zsh). Never run the harness and a CDP
  injection concurrently. A running sandbox does NOT block `test`/`buildPlugin`/`compileKotlin`.
  **Resource AND Kotlin changes need a restart** (the tree is packaged at launch).
- **Free negative control**: launch the sandbox BEFORE touching `plugin/`, write the fixture, run
  it, then edit. Pin list LENGTHS; `JSON.stringify(count)` is a STRING; Python `json.dumps` needs
  `separators=(',', ':')` to match `JSON.stringify` (gotchas § Testing).
- **A fresh IDE needs a minute before the full harness** (its own `__project` frame lands
  mid-fixture — fixture 40's control). Rerun alone / rerun warm.
- **Live hand tests at haiku prices**: drive the sandbox panel over CDP — `bridge({kind:'new'})`,
  `bridge({kind:'model', model:'haiku'})`, `sendTurn(prompt, [])`, click `.card-b .ok` on each
  card, poll the DOM (`scratchpad/drive117.py` shape, gotchas § Testing). `sendTurn` SENDS a real
  prompt; `addUserMessage(text, [], ts)` only draws. Clipboard truth: `xclip -selection clipboard -o`.
- **Shell on this box** (gotchas § Testing): `grep` is `ugrep` (→ `/usr/bin/grep`), `ls` is `eza`,
  `comm` needs `LC_ALL=C sort -u`; Bash output past ~30 KB is persisted with a 2 KB preview; bound
  minified-bundle greps or use a python `str.find` loop. A bare `sleep` in a Bash call is blocked
  (use a bounded `until` loop).
- **Stdio probes that run a turn: scratch `CLAUDE_CONFIG_DIR`** (credentials copy + a
  `.claude.json` whose `projects` holds only the scratch cwd with `hasTrustDialogAccepted:true` +
  settings.json; `python3 -I`, strip every other `CLAUDE*` env var, `--model haiku`); delete the
  dir in a `finally` and CHECK it is gone (a crash left one behind 2026-10-10).
- **The harness is NOT side-effect free on the sandbox CLI** (fixture 55 `setModel`) — start a
  fresh CLI (`bridge({kind:'new'})`) before a live end-to-end.
- **Real-panel render for review**: `window.__gallery()` + `tg('<menu>')` over `tools/cdp.py`,
  then `--screenshot`; measure computed colours against the tokens. Hover-only controls: inject a
  `display:inline-flex !important` style for the shot.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).
  A release starts only when the user asks (conventions).

## Next steps
- [x] 6.6 auto-include selection — measured, built, fixture 99, live + replay over CDP (2026-10-10).
- [x] **6.6 hand test in the real PhpStorm** — the user, 2026-10-10, six steps all passed: pill
      follows highlight / caret / tab switch; × per message; toggle off survives a restart; send
      → bubble pill + exact quote; resume + pill click selects the lines; a queued message carries
      its own selection. Label renamed "Include selection" (wrapped at the popup's fixed width);
      zip 20:18 rebuilt with it — the user installs it to confirm the one-line label.
- [x] **Commit 6.6** — `cf8ee58`, pushed 2026-10-10 on the user's ask.
- [x] "Include open file" switch + switch rows — built, fixture 99 (67 asserts), live over CDP (2026-10-10 evening).
- [x] **Hand test of the open-file half** — the user, 2026-10-10 evening, seven steps all passed
      (tag note moved before the content after Sonnet quoted it as the last line; zip 21:10).
- [x] 15.4 export / copy response — built, measured, live-checked (2026-10-10).
- [x] 11.7 hand-tested live; disabled / Monitor / interrupt measured.
- [x] 1.30 midnight relay; probe-transcript cleanup; extraction → 2.1.296; zip rebuilt.
- [x] 9.9 decided (retired) and built; popup fit; **0.15.0 released** — 2026-10-10.
- [x] MT-11.8 resume summary fix — measured, fixture 100, harness 1166, committed `abcf060` + pushed (2026-10-11).
- [ ] Confirm in the real PhpStorm that it offered / took 0.15.0 (Plugins list shows 0.15.0) and
      that the Marketplace Overview still reads plugin.xml's description (user errand, standing).
- [ ] When a real `auto_mode_server_fallback` frame lands (`window.__dialogSeen`, console warning),
      compare with fixture 94's payloads and the 4.10 fold; fix the card if they differ.
- [ ] 15.4 Save row: drive the native dialog by hand once (the user, or an input tool).
- [ ] Leads from the audit, probe-first (backlog § Next up): on-demand diagnostics (2.3), the
      autosave 10-minute stall (2.10), `session_title_changed` (8.3), Ultracode toggle and
      `effortLevel` persistence (9.2), `claude_code_version` for the out-of-date hint,
      `turn_preempted` / `queued_turn_count` (1.9).
- [ ] Renderer follow-ups in backlog § Housekeeping (indent-only code blocks; `*` inside inline
      code; mid-line fence placeholder leak; `~~~` fences).
- [ ] **Model chip / menu mismatch** — parked; on a recurrence capture chip title + persisted value.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; Windows CRLF splice
      check; Windows look of the 1.29 card header; Windows `./gradlew test` + VFS click check.
- [ ] Testing repo hand-test leftovers (`hand test/…`, `plain-control.txt`, sandbox transcripts
      incl. today's drive sessions, 6.6's `583f77e9…` among them) — the user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **15.4**: the Monitor row reads `local_bash` (the CLI's own type); no typed `/copy` / `/export`
  (host widgets — the chip / New rule); the export is the CLI's text, uncapped.
- **11.7's pane lives with its roster row**; a sub-agent's row answers "Output is not available."
  on the first ask; the disabled note is unreachable (no `task_started` under the flag). Sub-agents
  across an interrupt unmeasured.
- **4.10 is live-only and schema-built**; only `auto_mode_server_fallback` is declared — the Fable
  overage consent (9.7) stays deferred.
- **Timestamps**: tool loops later in a turn get no stamp (user's choice); the history list
  relabels only on open.
- **Before the first turn nothing on the wire names a settings `model` override** (9.1 gap).
- **The Thinking switch is INERT on Fable** — "document only". **Do not gate UI on roster
  capability flags** (9.10); only `supportsFastMode` is gated — disabled on Default by design.
- **No 1M switch** (9.9, retired 2026-10-10): the CLI serves 1M with or without `[1m]`; a tagged
  value is accepted if persisted or typed. The gauge seeds 200K for an untagged pick until the
  first result confirms the real window (a few seconds of a too-high percentage on a fresh pick).
- The picker shows the CLI's whole roster, pinned previous versions included (eight at 2.1.296).
- Typing `/model` or `/clear` is refused; the chip / New button are the surfaces. No keyboard-only
  new conversation; no shortcuts on any card.
- Banner frames, task frames, withdrawn asks and host dialogs are live-only; replay draws the
  CLI's own auto-deny result. A note typed before Accept on an ordinary card is dropped.
- Markdown: a bullet-character change does not split a list; the offline highlighter colours its
  keyword set inside EVERY language (1.11).
- plugin.xml "Not there yet" list: dark UI only, one conversation, selection-not-whole-file (6.6 replaced the
  "no auto-context" bullet 2026-10-10).

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-10 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped; Windows has no shell PATH layer.
