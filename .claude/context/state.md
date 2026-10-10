# State

## Current focus
**2026-10-10 (twenty-second session, Linux): 4.10 — the auto-mode server-fallback dialog — built,
verified and committed (`d26f670`); the user's "save / commit and push" closes the session.**
1. **4.10 built from the schema.** The panel declares `initialize.supportedDialogKinds:
   ["auto_mode_server_fallback"]` (`ClaudeCli.DIALOG_KINDS` — the declared list AND the forwarding
   filter) and renders the CLI's `request_user_dialog` as a `.card.warn.dlg` Continue / Stop card
   (`renderDialog`, `plugin/src/main/resources/webview/js/85-cards.js`): title → header, non-blank
   paragraphs, the help URL only when https on claude.com / anthropic.com (linkified in a paragraph
   that quotes it, else a trailing "Learn more"), the reference webview's stock sentence when both
   are empty, both buttons disabled for the CLI's 500 ms `armInputGrace`. Answer:
   `{kind:"dialog", id, result}` over the bridge (ChatPanel allowlists `continue|interrupt`) →
   `ClaudeSessionService.respondDialog` (pending set, first answer wins) →
   `{behavior:"completed", result}`. An UNDECLARED kind is logged and left unanswered (the schema
   forbids answering it). `__perm_cancelled` lapses the card (1.28 wording). Row evidence in
   `docs/feature-checklist.md` 4.10 (✅); wire facts in `docs/ide-mcp-protocol.md` § 9c.
   **MEASURED:** payload `{gatewayHost?, title, paragraphs[], helpUrl}` + result enum + answer
   shape from the 2.1.296 binary; render rules from the 2.1.296 VS Code webview; a bare
   `initialize` carrying the declaration answers `success` on 2.1.296 (stdio, no transcript).
   **UNMEASURED:** a real frame — needs the server classifier down; the first frame of each kind
   lands in `window.__dialogSeen[kind]` + a console warning. Check the card against it when one
   arrives.
2. Evidence: fixture 94 (negative control on the pre-change sandbox: every discriminating assert
   failed, step 2 aborted on the missing card; after: **28/28**), full harness **980/0**,
   `./gradlew test` **169**. Real-panel render over CDP found the help link in the browser's
   default blue (outside `.blk`) → `.card.dlg .dlg-p a` rule + the 28th assert. Gallery + mockup
   carry the card (pending and answered). **Not hand-tested live** — nothing can trigger it.

**Three [DECIDE] rows remain: 9.9, 11.7, 15.4** (At a glance: 97 ✅ · 2 ⬜ · 46 ➖).

**Pending in the real PhpStorm: EIGHT fixes** (PATH lookup 2026-09-27, Default chip + thinking trim
2026-10-08, bold-wrapping-italic + spaced @-mentions 2026-10-10, 1.30 + 1.31 2026-10-10, now 4.10).
The `plugin/build/distributions/claude-brains-0.14.0.zip` built 11:14 PREDATES 1.30/1.31/4.10 —
rebuild (`buildPlugin`) for a disk install, or a 0.14.1 release (user's call; `verifyPlugin` every
release).

**Cleanup owed, the user's (the session's permission classifier refused both, 2026-10-10 twenty-first):**
delete the three audit-probe transcripts `982ad0ee-aeab-4882-b005-5e34069eff71`,
`60d9bd11-f732-43af-854f-de084163b0ca`, `830d916b-f6a0-49b5-a4c4-793bba06622e` under
`~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` and set that project's
`lastSessionId` in `~/.claude.json` back to `8c92f59b-0b22-4152-95b9-1c6d4260ecab`. The hand-test
session ("AskUserQuestion scratch file preview", one Haiku turn) may stay. Also:
`reference/anthropic-claude-code/` is still the 2.1.270 extraction — rsync it to 2.1.296 (runbook
step 2) before the next audit (this session read the 2.1.296 extension straight from
`~/.vscode/extensions/anthropic.claude-code-2.1.296-linux-x64/`).
The sandbox PhpStorm was left RUNNING on the final 4.10 build (CDP 9222; dies with the Claude Code
process that launched it — a `load` that finds `:9222` closed is normal).

## Open investigations
- **Where the user's Fable default comes from** — still unknown. A bare `initialize` on 2.1.296
  (this Max account) resolved `default` → `claude-sonnet-5-5`; the 2.1.270 binary said
  `claude-opus-5[1m]`. The panel shows what the CLI serves; it does not change what runs.
- **Model chip "Fable (1M)" with no ✓ row** (parked) — the 9.9 measurement (tagless roster, tag
  accepted and echoed) supports the "persisted `fable[1m]` matches no tagless row" candidate.
- **Roster per PROCESS**: the 2.1.293 terminal probe listed 11 rows (no `fable` alias); the 2.1.296
  terminal probe listed 13 WITH it — a flags-fetch timing lead, unproven.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04) — waiting on the user's diagnostic.
- **Title tooltips never show on Linux JCEF** — backlog § Next up.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **980** (fixtures to **94**); `./gradlew test` **169**.
- Sandbox PhpStorm 2024.2.6: `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"` (background, from the repo root). `runIde` blocks
  until the IDE exits. Stop it with `pkill -f 'transformed/PhpStorm-2024.2.[6]'` (never
  `pgrep -x claude`; the real snap PhpStorm's CLI is a sibling — kill the sandbox's by PARENT
  cmdline), wait for `:9222` to vanish (bounded `until` loop). Never run the harness and a CDP
  injection concurrently. A running sandbox does NOT block `test`/`buildPlugin`.
- **Free negative control**: launch the sandbox BEFORE touching `plugin/` (it packages the tree
  at launch; later edits don't reach it), write the fixture, run it, then edit — this session's
  order. Pin list LENGTHS in button asserts: `[].every()` is true (gotchas § Testing).
- **Shell on this box** (gotchas § Testing): `grep` is `ugrep` (GNU `\{n\}` fails → use
  `/usr/bin/grep`), `ls` is `eza` (hung a chain once), `comm` needs `LC_ALL=C sort -u`; Bash output
  past ~30 KB is persisted to a file with a 2 KB preview; a minified-bundle `grep -o` with a wide
  context window can take minutes — bound the window and background it.
- **Probes that write transcripts cannot be cleaned by the session** — the classifier refuses the
  delete and the `lastSessionId` restore; list the ids for the user (gotchas § Testing).
- **The harness is NOT side-effect free on the sandbox CLI** (fixture 55 `setModel`) — start a
  fresh CLI (`bridge({kind:'new'})`) before a live end-to-end. Live turns over CDP:
  `sendTurn('<prompt>', [])` SENDS a real prompt; `addUserMessage(text, [], ts)` only draws.
- **Stdio probes**: `initialize` + optional turn via a 40-line python (scratchpad shape — stdin
  JSON lines, read until `control_response` / `result`); a bare initialize writes no transcript, a
  turn does. Run with `python3 -I` from a scratchpad dir.
- **Real-panel render for review**: a 30-line script on the harness's `Panel` (reset, feed the
  frame over `onClaudeEvent`, `screenshot`, measure gaps via `getBoundingClientRect`) — how the
  4.10 link-colour defect was caught; the mockup and headless Chrome would not have shown it.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).
  A release starts only when the user asks (conventions).

## Next steps
- [x] 4.10 auto-mode server-fallback dialog — built 2026-10-10 (fixture 94, commit `d26f670`).
- [ ] **Decide the three open rows**: 9.9 (retire / hide / keep the inert 1M switch), 11.7 (task
      output, `get_task_output` success shape needs a live task), 15.4 (export + copy response, [XS]).
- [ ] Get all EIGHT fixes into the real PhpStorm — rebuild the zip (the 0.14.0 one predates
      1.30/1.31/4.10) or cut 0.14.1 (user's call).
- [ ] Cleanup owed (above): three probe transcripts + `lastSessionId`; rsync the extraction to
      2.1.296.
- [ ] When a real `auto_mode_server_fallback` frame ever lands (`window.__dialogSeen`, console
      warning), compare it with fixture 94's payloads and the 4.10 fold; fix the card if they differ.
- [ ] Check the listing's Overview still reads plugin.xml's description text (user errand).
- [ ] Leads from the audit, probe-first (backlog § Next up): on-demand diagnostics (2.3), the
      autosave 10-minute stall (2.10), `session_title_changed` (8.3), Ultracode toggle and
      `effortLevel` persistence (9.2), `claude_code_version` for the out-of-date hint.
- [ ] Renderer follow-ups in backlog § Housekeeping (indent-only code blocks; `*` inside inline
      code; mid-line fence placeholder leak; `~~~` fences).
- [ ] **Model chip / menu mismatch** — parked; capture chip title + persisted value vs roster.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; Windows CRLF splice
      check; Windows look of the 1.29 card header; Windows `./gradlew test` + VFS click check.
- [ ] Testing repo hand-test leftovers (`hand test/…`, `plain-control.txt`, transcripts) — the
      user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **4.10 is live-only and schema-built**: nothing is known to be persisted for the dialog, so
  replay draws nothing; the card has never met a real frame. Only `auto_mode_server_fallback` is
  declared — the Fable overage consent (9.7) stays deferred and undeclared (reviving it = add the
  kind to `ClaudeCli.DIALOG_KINDS` + a card; a declared kind without a card parks the turn).
- **Timestamps**: day labels are relative ("Today"/"Yesterday"), so a page left open past
  midnight reads stale until a clear/resume — same property as the history list. Tool loops later
  in a turn get no stamp; local-command echo turns get no reply stamp. No on/off switch.
- **Before the first turn nothing on the wire names a settings `model` override** (9.1 gap).
- **The Thinking switch is INERT on Fable** — "document only". **Do not gate UI on roster
  capability flags** (9.10); only `supportsFastMode` is gated — and the `default` row lost it at
  2.1.296 (Sonnet 5.5), so the switch is disabled on Default by design.
- **The 1M switch is inert on 2.1.296** (9.9 [DECIDE]) — reads OFF on every row.
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
