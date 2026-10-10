# State

## Current focus
**2026-10-10 (twenty-sixth session, Linux): 0.15.0 RELEASED and Approved — `docs/release.md`
end to end (commit `1da78a8`, tag `v0.15.0`, GitHub release with the asset, feed serving 0.15.0,
`marketplace-upload` green, Marketplace "Approved" with JetBrains' ladder green). Earlier today:
9.9 retired + gauge lookup fixed and the popup fit (`8324cd8`, `96e32d1`); 15.4, the Kotlin
clipboard route, 1.30's midnight relay, 11.7 measured + hand-tested (`759c904`, `96315e0`,
`a556a3a`). Twelve features/fixes shipped since v0.14.0 (2026-09-13).**
0. **9.9 + popup fit** (`8324cd8`, `96e32d1`). The footer's 1M switch is gone; `windowConfirmed`
   replaces `oneMFromCli`; `reconcileFromResult` (`80-gauge.js`) also matches the roster's
   `resolvedModel`, tags stripped both sides; `capToRows` (`40-sessions.js`) measures the popup
   against `#head` + viewport and `tg('modelMenu')` caps `#modelItems` with no row count. Fixtures
   97 + 98; hand-tested by the user in the real PhpStorm (4% after a turn, the switch row gone).
   The user has the 17:37 zip installed (everything through 9.9); the popup fit is in the 17:45
   zip, NOT yet installed.
1. **15.4 export / copy response** (`759c904`). A hover copy control on every finished assistant
   text block (`mdBlock` / `copyable` in `plugin/src/main/resources/webview/js/20-markdown.js`,
   source on `el.__md`, `finishBubble` in `70-events.js` at every live finalisation site) and an
   Export conversation popup hanging from the header (`#exportBtn` / `#exportMenu` in `chat.html`,
   logic in `40-sessions.js`): one `bridge{kind:'export'}` per open → `ClaudeCli.exportConversation`
   (`export_conversation`) → `__export{text, default_filename | error}`; Copy / Save rows off until
   the text lands; Save sends the shown text back on `exportSave` → `ChatPanel.saveText` (native
   dialog). **Every copy control** (code block, response, export row) bridges `{kind:'copy', text}`
   → `CopyPasteManager` — `navigator.clipboard` needs focus + a user activation (measured). Also
   fixed: the streaming accumulator never reset between text blocks of one message; a CLI exit
   under a loading export. Evidence: fixture 96 (15 steps, 48 asserts; control on the pre-change
   sandbox), harness **1073/0**, test **169**, `xclip -o` read all three copies back verbatim, live
   export against the sandbox CLI (fresh → "Nothing to export yet.", resumed → 739 chars).
   **Not driven:** the Save row's native dialog (no Wayland input tool on this box).
2. **1.30 midnight relay** (`96315e0`): `armMidnight` in `50-blocks.js`, one timer per local
   midnight re-lays the date lines. Not fixture-pinned beyond "timer armed" (needs a fake clock).
3. **11.7 measured + hand-tested** (`a556a3a`): disabled answer, Monitor task, interrupt — all
   measured (protocol § 12); the roster pane and the "Run in background" offer driven live in the
   sandbox panel over CDP against the real CLI (checklist 11.7). A bare foreground `sleep` is
   refused by the CLI itself — use a loop.
4. **Cleanup done:** the three audit-probe transcripts deleted; `lastSessionId` was already
   `8c92f59b-…`; `reference/anthropic-claude-code/` rsynced to **2.1.296**; the 0.14.0 zip
   REBUILT 16:48 (`plugin/build/distributions/claude-brains-0.14.0.zip`, `buildPlugin` only — no
   `verifyPlugin`, not a release).

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
- [x] 15.4 export / copy response — built, measured, live-checked (2026-10-10).
- [x] 11.7 hand-tested live; disabled / Monitor / interrupt measured.
- [x] 1.30 midnight relay; probe-transcript cleanup; extraction → 2.1.296; zip rebuilt.
- [x] 9.9 decided (retired) and built; popup fit; **0.15.0 released** — 2026-10-10.
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
      incl. today's drive sessions) — the user's call whether to reset.
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
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-10 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped; Windows has no shell PATH layer.
