# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-10-10 (twenty-fifth) — 9.9 decided by a hand test in the real PhpStorm: the 1M switch retired, the gauge lookup it masked fixed; the model popup fits the panel; harness 1089
- The user installed the rebuilt zip and we walked 9.9 one step at a time in the real IDE: `sonnet`
  + switch ON → 4% after a turn; switch OFF → the gauge JUMPED to 20% for the same ~40K tokens and
  stayed there after the next turn. The disk said both turns were plain `claude-sonnet-5-5`; the
  live window is not persisted. A stdio probe (scratch cfg) then measured the case the audit had
  skipped: `set_model "sonnet"` → `contextWindow` 1,000,000, same as `sonnet[1m]` — the audit had
  probed opus / opus[1m] / sonnet[1m], never untagged sonnet, and called it "either way".
- So the switch could not change the model and only mis-set the gauge; and the reconcile that
  should have corrected it never matched an ALIAS pick (`windowFromUsage` compared `sonnet` to the
  key `claude-sonnet-5-5`; only full-id selections such as Default's resolvedModel ever matched).
  Explained in plain terms, the user chose the recommended option: retire + fix. `oneMFromCli` →
  `windowConfirmed`; the roster `resolvedModel` is the third candidate; tags ignored both sides.
  Fixture 97 (control: switch present, 20%); fixture 55 trimmed; harness 1079; commit `8324cd8`.
- The user's next screenshot: the 13-row roster popup taller than the panel, its filter box under
  the tool-window title. No cap existed on `#modelItems` at all (slash and history have five-row
  caps), and `capToRows` measured the LIST box, so a popup whose header ran off the top still
  looked on-screen. Fixed by measuring the popup against `#head`'s bottom + 4 and the viewport,
  `capToRows(modelItems, Infinity)` on open, the ✓ row scrolled into view. The sandbox panel
  (871px) reproduced it — nobody had opened the menu there after the roster grew to 13. Fixture 98
  (control 3/10 failed); harness 1089; commit `96e32d1`; zip rebuilt 17:45.
- Checklist: 99 ✅ · 46 ➖, no [DECIDE] rows left — the user asked and the answer is "none open".

## 2026-10-10 (twenty-fourth) — 15.4 built (export popup + copy response), every copy through Kotlin; 1.30 midnight relay; 11.7 measured + hand-tested live; cleanup done; harness 1073
- "Lets do 15.4" → probed first under a scratch `CLAUDE_CONFIG_DIR` (one haiku turn, two processes):
  `export_conversation` is `text:""` + `conversation-<date>-<time>.txt` turnless, the TUI's own
  `❯ prompt / ● reply` text under `<date>-<time>-<slug>.txt` after a turn, the whole history on a
  `--resume`d process before any turn. Built: a hover copy control per finished reply block
  (`mdBlock`/`copyable`, source on `el.__md`, `finishBubble` at every live finalisation site) and
  an Export popup hanging from the header (`.actions`-anchored, opens DOWN, #histPanel's width),
  one ask per open, Copy / Save rows off until the text lands, Save sends the shown text back.
- Sandbox launched on the pre-change tree first → control free (step 1 failed, step 2 aborted).
  Fixture 96's step 3 read `'Hello **world**Done.'` off the second block's source: `curRaw` was
  reset only at message_start — a text → tool → text message re-rendered the first text in the
  second block. Fixed at bubble creation. A resume of a session with no file exited 1 under the
  open popup and left it "Preparing…" for good — a crash drains no pending callback; `__exit`
  now answers a loading export.
- "Finish these points fully": the REAL clipboard did not change after a CDP click — Chromium's
  `navigator.clipboard` needs focus + a user activation. Every copy control (code block, response,
  export row) now bridges `{kind:'copy'}` to Kotlin's `CopyPasteManager`; `xclip -o` read all three
  back verbatim. Save's native dialog stays undriven (no xdotool/ydotool on this Wayland box).
- 1.30: one timer re-armed at each local midnight re-lays the date lines (`armMidnight`).
- 11.7's three unmeasured answers, one stdio probe: `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1` →
  the error text AND no `task_started` for the foreground shell (the gated offer can never show
  it); a Monitor is a plain `local_bash` background task, `get_task_output` serves it, every line
  wakes the model; an interrupt leaves a background shell running (`still_queued:[]`, the wake
  turn later finishes the reply). Hand-driven in the sandbox over CDP (real CLI, haiku): pane
  grows and dies with its row, wake turn = a reply block in the same turn; foreground loop → the
  offer ~6 s in → click → roster + turn end → wake. A bare foreground `sleep 30` is refused by the
  CLI itself ("use Monitor or a background run") — the first B run never reached the offer.
- My own traps: the probe's first run crashed on a NameError BEFORE its `rmtree` and left the
  credentials copy on disk (caught minutes later; cleanup is in a `finally` now); `git add -p`'s
  `s` sub-hunks come in FILE order — the reversed y/n staged the comment and left the function;
  Python `json.dumps` default separators ≠ `JSON.stringify`.
- Cleanup: the three audit-probe transcripts deleted (the classifier allowed it this time),
  `lastSessionId` was already back; `reference/anthropic-claude-code/` rsynced to 2.1.296; the
  0.14.0 zip REBUILT 16:48 (same number as the 2026-09-13 release — a disk install; 0.14.1 is the
  user's call). Totals: fixture 96 15/48, harness 1073/0, test 169. Three commits + the save,
  then pushed on the user's "save / commit and push".

## 2026-10-10 (twenty-third) — 11.7 background task output + Run in background: probed live, built, fixture 95; harness 1025
- "What is 11.7?" → "Lets do it." The row's own note said probe first, so the session opened
  with a stdio probe under a scratch `CLAUDE_CONFIG_DIR` (credentials copy, `.claude.json` with
  only the scratch cwd trusted, haiku, mode default): a `run_in_background` shell polled with
  `get_task_output` six times running + once ended + a bogus id, then a FOREGROUND `sleep` loop
  hit with `background_tasks{tool_use_id}` 4 s in. Everything landed in one 40 s run; the real
  `~/.claude` got no transcript (the scratch dir was deleted after).
- Measured: `{output, total_bytes, truncated}`; the ended tail `\n\n[exited with code 0]\n`;
  roster frame BEFORE `task_started` for a background launch; a foreground Bash gets
  `task_started{is_backgrounded:false}` ~3 s in (the gate for the offer); `background_tasks` →
  `{backgrounded:true}` + roster + `task_updated{is_backgrounded:true}` + the "manually
  backgrounded" tool_result + the turn's result; a completed shell WAKES the model (extra result
  with `origin`). The extension's mapping read with a python `str.find` loop (instant, vs the
  minutes a wide `grep -o` took last session); the binary's `strings` gave the disabled text.
- Design: output on the ROSTER ROW (the agent-map analogy; the timeline's task line already
  says how a task ended), every row toggles, errors end polling; the offer on the tool line
  gated by the measured foreground `task_started`; VS Code's wording shortened.
- Sandbox launched on the pre-change tree first → negative control free: first assert failed,
  step aborted on the missing button. First run on the changed build 40/44: the row click
  closed the popup (the list is rebuilt before the click bubbles, so the document handler's
  `closest('.popup')` test fails on a detached row) — diagnosed over CDP with a stack captured
  on `classList.remove`, fixed with stopPropagation like the ✕, assert added. Then 45/45.
- Full harness 1024/1 the first time: fixture 40's path negative control failed because the
  fresh IDE's own `__project` frame landed mid-run; alone 19/19, warm rerun 1025/0. Test 169.
- Real-panel render (gallery + roster open, CDP screenshot): offer in `--blue`, note muted, pane
  with chevrons and the size note. Not hand-tested against a live shell in the sandbox panel.
- Two assertion mistakes of mine the control run exposed: `JSON.stringify(count)` is a string
  (fails `atLeast`), and "only one line still in flight" forgot two never-settled lines.

## 2026-10-10 (twenty-second) — 4.10 auto-mode server-fallback dialog built from the schema; fixture 94; harness 980; committed `d26f670`
- "Can we implement 4.10?" → yes, with the CLI side fully measured and the frame itself not:
  the 2.1.296 binary's dialog definition gives the payload `{gatewayHost?, title, paragraphs[],
  helpUrl}`, the result enum `continue|interrupt|cancelled` and the host answer
  `{behavior:"completed"|"cancelled", result}`; the gate is `sdkHost && supportedDialogKinds
  .includes(kind)`, else "no dialog surface to warn on, continuing in auto mode". The 2.1.296
  VS Code `webview/index.js` renderer (`auto-mode-server-fallback-prompt`) gave every render
  rule, including the https-on-claude.com/anthropic.com link filter and the 500 ms disabled
  buttons. The reference extraction is still 2.1.270 (declares two kinds) — read the 2.1.296
  extension from `~/.vscode/extensions/` directly.
- A bare `initialize` with `supportedDialogKinds:["auto_mode_server_fallback"]` answered
  `success` on 2.1.296 over stdio (scratchpad probe, `python3 -I`, no transcript written).
- Order that made the negative control free: sandbox launched on the pre-change tree FIRST,
  fixture 94 written, run (7 discriminating fails, step 2 aborted on the missing card, three
  `[].every()` asserts passed vacuously → lengths pinned), THEN the code. After: 27/27, full
  harness 979/0, `./gradlew test` 169.
- The real-panel render (harness `Panel` + screenshot) showed the "Learn more" link in the
  browser's default blue — the only link rule is `.blk a`, and the card's paragraphs sit outside
  `.blk`. `.card.dlg .dlg-p a { color: var(--blue) }` + a 28th assert comparing computed colours
  against a throwaway `.blk a`; sandbox restarted (resource change), 28/28, harness 980/0.
- Gaps measured over CDP: header→paragraph 8, paragraph→paragraph 18, paragraph→buttons 10 — the
  attach / block / card-b tokens, as designed.
- Undeclared dialog kinds are now LEFT UNANSWERED in `handleControlRequest` (the schema's rule),
  not acked with `{}` like the catch-all arm — a kind can only reach us if we declared it, so a
  miss there is a list drift between `ClaudeCli.DIALOG_KINDS` and `renderDialog`.
- Checklist: 4.10 ✅, §4 ✅, 97 ✅ · 2 ⬜ · 46 ➖; protocol doc § 9c carries the declaration and the
  answer shape. Committed on the user's "commit and push" — one feature commit, one context commit.

## 2026-10-10 (twenty-first) — re-audit 2.1.270 → 2.1.296 (26 versions, all measured); 1.30 + 1.31 built and hand-tested live + replay; harness 952
- Audit mechanics: the CLI had auto-updated to 2.1.296 that morning (extension too), so the
  baseline binary came from the 2.1.270 vsix (runbook step 3, 104 MB). Control subtypes 103 → 155:
  28 Mods `ui_*` (surface enum has no JetBrains), dialog feeders (`get_status`, `export_conversation`,
  `get_skills/sandbox/chrome_dialog`, `get_task_output`) all probed `success` over stdio;
  `list_directory` refused. Roster 5 → 13 rows, NO `[1m]`; three one-turn probes showed
  `set_model "opus[1m]"` echoed as `claude-opus-5-5[1m]` with `contextWindow` 1,000,000 either way
  → 9.9 re-opened. `update_settings` allowlist grew `userSettings:[effortLevel]`. The extension
  declares `supportedDialogKinds:["fable_overage_consent_prompt","auto_mode_server_fallback"]`.
- Six [DECIDE] rows opened (1.30, 1.31, 4.10, 11.7 new; 9.9, 15.4 re-opened); the user took 1.30
  and 1.31 the same day. Changelog lines tagged Cloud sessions / Claude Tag / Code Review skipped.
- Cleanup trap: the auto-mode classifier refused deleting the three probe transcripts and restoring
  `lastSessionId` ("Session Transcript Tampering"), and the `rm` with shell variables tripped the
  built-in check — left to the user, recorded in the audit block (gotchas § Testing).
- 1.31's answered-card choice was made from a side-by-side render (both candidates with the real
  stylesheets, served from the scratchpad, Playwright screenshot): "full card + summary" (B) won.
- Fixtures 92/93 written FIRST and run on the old-build sandbox: 26 discriminating fails / 12
  guard passes (one guard mis-counted the Other row). After the edit: 38/38, full harness 952/0,
  `./gradlew test` 169 (the shared `replay-sample.jsonl` has no assistant prose — the ts test
  synthesises a two-record transcript).
- `foldBlock` is one-shot (`data-folded`), so the preview box got `refoldPreview` +
  `wirePreviewFold`. Real-panel gap measurements over CDP: 8/8/18 px as designed.
- Hand test (one Haiku turn in the sandbox): a real `can_use_tool` carried `preview` on all three
  options — the field is live on 2.1.296; hover/pick/Submit/reply stamp, then Refresh replayed
  the card with pick, preview, summary and both stamps.
- Shell traps on this box: `grep` is ugrep, `ls` is eza (hung a background chain), `comm` wants
  `LC_ALL=C sort`; Bash output over ~30 KB is persisted with a 2 KB preview.

## 2026-10-10 (twentieth) — spaced-mention fix hand-tested; zip built; MCP popup and push-line questions answered, no code
- Set up the hand test: sandbox `runIde` on the testing project, three files with spaces/brackets in
  their paths (distinct secret words), `buildPlugin` for the real PhpStorm. The user: "Working
  great!". No code changed this session.
- Trap: the panel's @-picker list is pushed ONCE at page load (`seedUi()` → `listProjectFiles()`
  over `ProjectFileIndex`); files created while the IDE was closed were absent (and a deleted probe
  path still listed) until `location.reload()` over CDP re-ran `seedUi()`. Backlog § Housekeeping.
- PhpStorm 2026.2 "MCP Server" popup with its button row clipped: the IDE's own Compose widget;
  idea.log shows `[SKIKO] Fallback to next API — RenderException: Cannot create OpenGL context` at
  each first open (10:31, 11:17) on this Wayland session. The plugin registers no status-bar widget
  and the CLI connects to OUR WebSocket bridge, so "No active connections" there is expected.
- Same log: `Exception in thread "claude-stdout" java.io.IOException: Stream closed` at 10:58:37,
  the moment a New conversation replaced the CLI process — harmless, noisy. Backlog § Housekeeping.
- Push status line question (two screenshots: four identical "Pushed main", one bare "Pushed"):
  explored, not built — the user left it. The CLI's `vcs_state_changed` carries `{kind, branch?,
  cwd}` only, the branch is PARSED FROM GIT'S OUTPUT (a `-q` push → bare "Pushed"), the frame is
  never persisted, and `gitOperation.push` on the tool result has only `branch` too. The remote URL
  and commit range exist solely in the Bash OUT text. Findings in backlog § Deferred.
- Plan mode was entered for that question and exited with nothing written.

## 2026-10-10 (nineteenth) — spaced @-mention paths: quoted `@"…"` on insert, read back as one chip; fixture 91; harness 914
- User screenshot: the sent bubble's chip stopped at the first space of `…Comparison_Final Sheet
  (2).xlsx`. The conversation was not on this box, so the CLI side was MEASURED with a stdio probe
  (2.1.295, `claude -p` stream-json, `--max-turns 1`, Read disallowed, a secret word in a file under
  `_probe/space dir/`): raw and backslash-escaped mentions attach NOTHING (the model reaches for a
  tool, no error); `@"path"` persists `attachment{type:'file'}` and the model answers from it.
- The binary's `(?:^|\s)@((?:[^\s\\]|\\ )+)` string was a red herring — it exists, the escaped
  form still attached nothing. An `@[^@\s]+` string nearby was a highlight.js grammar. The real
  grammar is `@(?:"([^"\n]+)"|…)`; the official webview's `OL0()` quotes on `/[\s:]/` or a non-word
  tail, and the changelog has "[VSCode] Fixed @-mentions dropping files whose paths contain spaces".
- Fix: `mentionToken()` (50-blocks.js) + `mentionHtml` reads the quoted form; `insertMentions` and
  the @-menu rows use it. Control on the pre-change sandbox: 4/5. First fixed run 6/3: the picker
  splices `ins` AFTER the `@` the user typed → `mentionToken(f).slice(1)`; two were the fixture's
  own Python `json.dumps` spacing vs `JSON.stringify` (use `separators=(',',':')`). Then 9/9, 914/0.
- Live end-to-end over CDP (`bridge({kind:'new'})`, `sendTurn` with the quoted mention): one chip;
  transcript c5364f7c record 3 is the CLI's `file` attachment (displayPath with the space), the
  model's Read came later. `--max-turns 1` + a tool call = empty `result`; `-p` without `< /dev/null`
  waits 3 s for stdin and warns.
- Probe files and their four transcripts deleted; the testing project's `lastSessionId` had NOT
  moved (`-p` does not touch it). The sandbox died with the Claude Code process that ran `runIde`.
- Committed and pushed on the user's ask (fix + this context save). Not installed in the real
  PhpStorm — five fixes pending there.

## 2026-10-10 (eighteenth) — bold wrapping italic: inlineMd's bold regex admits single `*`; fixture 90; harness 905
- User screenshot + the conversation's jsonl: `**Lashuna / Rasona — Garlic (*Allium sativum*)**` drew
  with the species italic and the `**` printed. Read BY KEY from the `assistant` text block (CLI
  2.1.276) before touching anything — the markdown was exactly that, bold wrapping an italic.
- Cause in `inlineMd()` (`20-markdown.js`): bold was `\*\*([^*]+)\*\*`, so one `*` inside the pair
  made the bold fail; the italic pass then paired only the inner asterisks. The control also showed a
  second face of the same regex: `)** and **C**` pairs the closing `**` with the next opener and
  draws a bold " and ".
- Fix: `\*\*((?:[^*]|\*(?!\*))+)\*\*` — single `*` allowed, stop at the first `**`. Italic wrapping
  bold already worked (bold pass runs first, leaves no `*` for the italic pass to trip on) and is now
  a guard. The backlog's "inline code is not opaque to emphasis" item is the same function, untouched.
- Free negative control: the sandbox was down, so it was started on the UNCHANGED build, fixture 90
  written and run (6 guards pass, 6 discriminating fail, readings in the provenance), then the edit,
  kill via `pgrep -f … | xargs -r kill` (CDP gone in 1s), restart, 12/12; full harness 905/0.
- First control run ABORTED on a `null.textContent` in a discriminating assert (the pre-fix DOM has no
  `<b>`); `?.textContent ?? null` made it a FAIL — the known trap (gotchas § Testing), hit again.
- Docs: checklist 1.10 Read-more; mockup list item `<b>…<i>…</i></b>`. `./gradlew test` skipped (JS
  only). Not installed in the real PhpStorm — four fixes pending there. Committed and pushed on the
  user's ask (fix + this context save).

## 2026-10-08 (seventeenth) — opened-thought trailing gap: thinkBlock() trims; fixture 89; harness 893
- User screenshot: an opened "Thought for 1s" with a larger gap before the next Edit line than any
  other block pair. Measured first: 13 of 13 persisted `thinking` blocks in the 40 newest transcripts
  (2.1.270–2.1.293) end with `\n\n`; none starts with whitespace. `.think .body` is the panel's one
  `white-space: pre-wrap` block, so the newlines painted an empty line (opened body 40px vs 20px for
  the trimmed text, line-height 20.15px at 13px). The official webview never shows it because it
  renders thinking through its markdown renderer.
- Fix: `text = (text || '').trim()` at the top of `thinkBlock()` (`50-blocks.js`); the `empty`
  check reuses it. Live (`finishThinking` on `content_block_stop` / `message_stop`), replay, gallery
  and the redacted path all go through that builder — no CSS, no Kotlin change.
- Free negative control: the sandbox was down, so it was started on the UNCHANGED build first,
  fixture 89 written and run (3 guards pass, 3 discriminating fail), then the edit, restart, 6/6;
  full harness 893/0. Height readings taken over CDP on the old build BEFORE the kill and recorded in
  the fixture's provenance.
- Trap: `pids=$(pgrep … | tr '\n' ' '); kill $pids` STILL fails in zsh ("illegal pid: a b c") —
  variables are not word-split; `pgrep -f '…' | xargs -r kill` is the recipe (gotchas § Testing,
  corrected). The un-bounded `until ! ss …` wait then spun past the 120s tool timeout.
- Trap: a CDP eval right after a harness run sees an EMPTY panel — the harness `__clear`s between
  fixtures; replay the frames inside the probe script before measuring.
- `./gradlew test` skipped on purpose (JS-only). Not installed in the real PhpStorm — now three fixes
  pending there. Committed and pushed on the user's ask (fix + this context save).

## 2026-10-08 (sixteenth) — the Default selection follows the model the CLI actually serves; fixture 88; live-verified
- User's 2026-09-27 report revisited: chip "Default (Opus 5.5)" while a whole session ran on
  Fable and spent the Fable allowance; selecting Opus by hand "fixed" it. Measured over stdio on
  2.1.283 (2026-09-27) and 2.1.293 (today) with `--settings '{"model":"haiku"}'`: the roster's
  `default` row keeps saying Opus 5.5 while `system/init.model`, every assistant `message.model`
  and `result.modelUsage` say Haiku; nothing in the initialize response (20 keys) names the
  effective model. The panel read only the roster → the chip could not know.
- Fix: `defaultResolvedFromCli` + `reconcileCliModel()` (`30-menus.js`), hooked from `system/init`
  and the assistant case (`70-events.js`); `chipLabelFor()` is now the one label rule for the
  chip's three writers; the Default row's description becomes "<real> · from your settings";
  `80-gauge.js` matches `modelUsage` by `effectiveModelId()` — with `default` selected the ring
  had never received the authoritative window. Named rows untouched; no `set_model` ever sent.
- Fixture 88 (17 asserts): control on the pre-fix sandbox 12/5 (the five discriminating asserts;
  step 3 passes vacuously there), fixed 17/17. First FULL run 886/1: fixture 67's synthetic
  `system/init` carries `model:'<fixture>'`, which my page-lifetime override remembered across
  the roster push → step 1 now resets it explicitly. Second full run 887/0; Kotlin 168/0.
- Live end-to-end took two attempts: the first turn ran on Opus 5.5 because fixture 55's real
  `setModel` calls had reached the sandbox CLI (five "Set model to …" echoes in its transcript)
  and an explicit `set_model` beats settings. `bridge({kind:'new'})` → fresh CLI (pid by command
  line) → one turn: chip "Default (Haiku 5.5)", row "Haiku 5.5 · from your settings", transcript
  `488fb690` `message.model: claude-haiku-5-5`, taped `modelUsage` contextWindow 1,000,000 (the
  CLI's own number for Haiku 5.5 — the ring was right). Override file removed afterwards.
- Found on the way: the roster is per-PROCESS — the same 2.1.293 binary listed 11 rows over a
  terminal stdio probe and 13 in the sandbox panel (`haiku→claude-haiku-5-5`, a `fable` alias).
- Two probe traps (gotchas § Testing): a poll on `#log .generating` never fired; `ls -t` over the
  transcripts picked an older file twice (mtime bumped) — find the session by `"version"` + first
  `timestamp`.
- Mid-session the user updated the VS Code extension to 2.1.293 and restarted the real PhpStorm:
  its panel now runs the extension's 2.1.293 binary through the UNFIXED fallback — right version
  by coincidence. Neither fix is installed there; local zip or 0.14.1 is the user's call.
- Committed and pushed on the user's ask (fix + this context save).

## 2026-09-27 (fifteenth) — the panel had been running the VS Code 2.1.270 binary; executable lookup moved to the shell PATH
- User: "why is it not showing Opus 5.5?" (TUI had it, panel had "Opus 5 (1M)"). Measured by key:
  every panel transcript (this session included) `"version":"2.1.270"`, terminal ones 2.1.283;
  `/proc/<pid>/exe` of both real-IDE CLIs = `.vscode/extensions/anthropic.claude-code-2.1.270-linux-x64/
  resources/native-binary/claude`; the snap PhpStorm's PATH has no `~/.local/bin`; the TUI's own
  row 6 said "Update to 2.1.280+ to use Opus 5.5". Cause: `resolveExecutable()` walked the IDE's
  bare PATH while `ClaudeCli` spawned under the shell overlay (gotchas § JCEF, the ShellEnv bullet).
- Fix on the user's "Fix it properly": `ShellEnv.overlay()` (one copy of the layering) feeds both
  the spawn and the lookup; `ShellEnv.path()` / `which()`; `ShellEnvTest` (4). `./gradlew test`
  168/0. Negative control with `runIde` launched under a PATH stripped of `~/.local/bin` (after
  `./gradlew --stop`): pre-fix build → VS Code 2.1.270 binary (pid 862539), fixed → `versions/2.1.283`
  (pid 865376); the user's sandbox screenshots showed Opus 5 (1M) then Opus 5.5.
- User: "why so many models in the panel and not the terminal?" — 2.1.283's `initialize` roster
  has 11 rows with no distinguishing flag; 2.1.283's TUI `/model`, driven through a pty in a trusted
  dir, lists the same 11 ("… +1 model"); the user's 6-row terminal screenshot was an older binary,
  confirmed by their own 2.1.283 screenshot. Folding offered, not asked for (backlog § Someday).
- Traps (gotchas § Testing): `pgrep -x claude` misses the versioned binary (comm = `2.1.283`);
  `strings` on the 2.1.28x binary yields only the string table, never picker logic; a pty-driven
  TUI needs a trusted dir, writes a transcript and moves `lastSessionId` (both undone). The
  interactive TUI shows the trust dialog for `/home/syncroze` and both claude-brains dirs.
- 2.1.283 leads for the next re-audit (NOT audited): roster values carry no `[1m]` tag (Fable is
  `claude-fable-5-1`), so the panel's 1M switch read OFF; `chipName()` parses the version out of the
  description, which no longer leads with it for named rows; new-looking `initialize` keys
  (`fast_mode_disabled_reason`, `ide_rc_auto_enable_gate`, `remote_control_*`, `session_state`,
  `available_output_styles`, `user_output_styles_dir`, `analytics_disabled`).
- The real PhpStorm keeps its 2.1.270 CLI until a build with the fix is installed. Committed and
  pushed on the user's ask (fix + this context save).

## Digest
- **2026-09-13 (fourteenth)** — re-audit → 2.1.270 through the new runbook step 3b (the public CHANGELOG is LEADS only; it surfaced five UI changes with no new `case` label); ONE real find, 1.29 (`bashEditDiff` sidecar — attached only in auto/bypass by default, the discovery probe had come up in auto), built the same day with 6.5-tab, 5.6 and 3.7 (user picked layouts from rendered options), all hand-tested; harness 870, test 164; **0.14.0 released** (`gh release create` hit a 500 and left a draft without the asset while the feed already said 0.14.0 — recovered in ~2 min; Approved within the hour); Marketplace screenshot 03 regenerated.
- **2026-09-09 (thirteenth)** — 0.13.1 released as a patch (one renderer-only commit since v0.13.0 decided the version): `docs/release.md` in order with every precondition asserted before the first write, `test buildPlugin verifyPlugin` as one background run (163/0, 8 verdict files all Compatible — PS-263 joined the `recommended()` ladder), jar bytes checked for the new code, step-6 gate with the full notes → commit `8e19916`, tag, `gh release create`, feed. `marketplace-upload` green before the checks ran; Approved the same day with four verifier rows, visible in the UI before the API listed it (gotchas § Build). Context save committed and pushed on the user's own ask.
- **2026-09-08/09 (twelfth)** — two renderer fixes, both hand-tested by the user with an eight-prompt script (live, replay, plan card; transcripts verified by key): a ````markdown fence split at three backticks (regex `(`{3,})…\1`*`, fixture 85 written first → 8/15 failed pre-fix, then 15/15) and "numbered lists are always `1.`" → a CommonMark list parser (`mdList`: content indent, loose/tight, `<ol start>`, recursive bodies, fence indent strip; fixture 86, 17 failed pre-fix; prototyped in node before the restart). Harness 829/0, test 163/0. A three-backtick hand test proved nothing — the retest asked for four explicitly. Three pre-existing renderer gaps found on the way → backlog § Housekeeping (indent-only code blocks, `*` inside inline code, mid-line fence leak). Traps → gotchas § Testing: the sandbox exits cleanly on its own; a `grep -c` tail read a pass as a failure; a transcript-by-key script unbounded to the turn.
- **2026-09-05 (eleventh)** — 0.13.0 released and Approved the same day: steps 1–5 from one Python script asserting every exact token before the first write; `test buildPlugin` 163/0; `verifyPlugin` 8/8 Compatible (PS-242 → PS-263); notes shown WHOLE at the gate ("Go ahead please") → commit `a988d95`, tag, `gh release create`, feed + Marketplace upload green (receipt 1162736), Approved ~35 min later with a new "IDE run" verifier row. Notes shape that worked: ✨ New · 🐛 Fixes · Install · ⚠️ Notes (live-only caveats, deferrals); internal work left out.
- **2026-09-05 (tenth)** — mockup parity pass: the 13 JS-rendered states with no static example added from the renderers' own markup; the class-coverage grep (every `.class` in `webview/css/*.css` vs the mockup) reports 0 absent. Browser check via Playwright MCP needs `python3 -m http.server 8731 --bind 127.0.0.1` (it blocks `file:`), screenshots land in the repo root + `.playwright-mcp/` → delete (gotchas § Webview). Model chip "Fable (1M)" with no checked row: roster measured by a bare `initialize` on 2.1.261 and 2.1.236 (`fable[1m]` on both), every writer of the selected id read, menu code diffed against 0.12.5 (identical) — no path produces the label from the persisted `fable[1m]`; not reproduced → parked at the user's ask (backlog § Next up). A bare `initialize` probe writes NO transcript.
- **2026-09-05 (ninth)** — checklist fold reformat shipped as a design task (§3 first, "looks perfect", then the file); the user's browser markdown extension exposed two traps GitHub's API missed (blank lines inside a list item → spaced paragraphs, fixed by a leading `<!-- -->`; a nested list inside a fold closes `</details>` early in marked) → folds hold paragraphs only, verified through three parsers (gotchas § Docs). 1.26 measured with the new `tools/probe_stdio.py`: of twelve `system` subtypes only `vcs_state_changed` and `notification` reach this wire, six are REPL-only; all twelve drawn through one status-line renderer, first frame of each kept in `window.__bannerSeen`; per-kind glyphs chosen side by side in the REAL panel, `.status a` rule from a screenshot. 1.28: the CLI DOES send `control_cancel_request` to the host (interrupt over a parked ask) — Kotlin drops the pending entry, pushes `__perm_cancelled`, the card settles as withdrawn. 1.27: the cut marker opens the whole tool text in a read-only `LightVirtualFile` (`SessionStore.toolText` by tool id on replay). Traps → gotchas § Testing: `pgrep -f` matching its own shell (exit 144); `innerHTML` re-serialises SVG; a `.click()` on a missing element aborts the harness. Harness 776 (fixtures 84), Kotlin 163; checklist 93 ✅ · 0 ⬜ · 47 ➖, audit complete.
- **2026-09-05 (eighth)** — 4.8 closed (all four steps), 4.9 number-key answers ➖ ("no keyboard shortcuts for now"), 2.12 built (`WorkspaceRoots.extraDirs` → one `--add-dir` per root outside basePath; probe: attached dir → 0 Read asks), 3.7 reject-with-note built (inline after Reject, deny only) whose hand test exposed and fixed three older defects (duplicate error OUT box via `cardDenies`, replay "1 file changed" for a rejected edit via `reqFiles`, replayed card without its note; Kotlin test on `denied-edit.jsonl`), 3.8 editable Bash command built (CLI runs `updatedInput.command`; the transcript never records the edit; a single-rule grant follows the edit, a compound card writes per-part rules from `splitCommand`, a whole-string rule never matches, bare-`&` compounds re-ask every time). Traps → gotchas § Testing: `addUserMessage` over CDP sends a real prompt on a live session; computed `display` in `.split` is `flex`; no mid-frame harness hook; a Bash line always has an IN row. Harness 714 (fixtures 81), Kotlin 160; checklist 90 ✅ · 3 ⬜ · 47 ➖.
- **2026-09-04 (seventh)** — 4.8 built as a SPLIT button (main half = the CLI's default destination, caret = the other targets; no memory of the last pick, user's spec) after the user heard the decline recommendation and chose the split. Stdio probe 2.1.260: the ECHOED `destination` decides the file — projectSettings / localSettings / userSettings each wrote theirs, session wrote nothing and stopped the re-ask, cliArg behaved as session, a bogus value dropped the grant silently → Kotlin forwards only the four offered (`PermissionDestinations`). Two probe confounds (both gotchas § Testing): an untrusted scratch workspace never loads project settings (stderr says so, stdout does not) → scratch `CLAUDE_CONFIG_DIR` with a trust-patched `.claude.json`; a zsh `for … set -- $c` loop ran every cell with `--cfg`. Compound card gained an `All of these` header + destination rows; fixture 77 (control 11 fail), harness 670, Kotlin 147.
- **2026-09-04 (sixth)** — 4.7 built as VS Code's rule after the user's screenshot (four modes, not six) overturned the row's premise: the extension's `webview/index.js` `c4()` assembles the picker per session (`bypassPermissions` only under the dangerous flag, `dontAsk` displayed only while current, never offered). Five stdio probes on 2.1.260: no flag → `auto`; `--permission-mode` BEATS settings `defaultMode`; `bypassPermissions` in settings without the flag → `default`. Real defect: `permissions.defaultMode` was ignored entirely → `PermissionModes.resolveStored` (null = never picked → no flag), `pushInitMeta` seeds `__mode` from `initialize.current_permission_mode` (a live check found that `system/init` repeats the mode only with the FIRST turn), `Don't ask` row hidden unless current. Fixture 76 (control: one guard threw on a null node → null-safe asserts, gotchas § Testing); Kotlin 143, harness 653; user hand-tested six steps, 6.5 closed too. Trap: writing any `.claude/settings*.json` is blocked by the permission classifier — settings cells run in a scratchpad dir, the user makes the real edit.
- **2026-09-04 (fifth)** — full-surface audit at 2.1.260 (288 host `case` labels → 97 RPC types, ~100 webview features, 98 control + 46 `system` subtypes, the binary's 128-name command map, changelog 200→260): no missing feature AREA, ten small gaps → rows 1.26–1.28, 2.12, 3.7–3.8, 4.7–4.9, 6.9; terminal's-half verdicts recorded in the checklist's "Full-surface audit" block. Built 6.9 (`mentionHtml` sent-bubble capsules, fixture 74) and 6.5 (`MentionAction` first in both popup menus, `MentionPaths.tokens`, list parked until `seedUi()`, fixture 75); harness 642, Kotlin 141. Measured: an @-mention attaches before the model runs (1 turn) vs a plain path Read (2 turns); 200 KB cut at 2,000 lines silently, 2 MB not attached at all (threshold unpinned). Trap → gotchas § Testing: the running sandbox's jar is under the dir `-Didea.plugins.path` names, not `ls -t`'s pick.
- **2026-09-04 (fourth)** — re-audit 2.1.251 → 2.1.260, all measured (VS Code session sidebar grew archive/unread/groups; CLI +`cloud_session_delta` +`update_settings`; roster +`/advisor` +`/reload-plugins` −`/artifact-design`; Fable 5.1 row). Both vsixes fetched from the Marketplace, the 2.1.251 one for its native binary as the CLI baseline (runbook step 3). 13.3 died on measurement: `update_settings` allows only `outputStyle`; the CLI honours `model`/`permissions.defaultMode` from `.claude/settings.local.json` at spawn — user chose "wait for Anthropic" (➖, backlog watch-item). Lesson (gotchas § Protocol): subtype acceptance ≠ key coverage.
- **2026-09-04 (third)** — 0.12.5 released (`a77a565`, tag `v0.12.5`) and Marketplace-Approved within the hour: steps 1–5 proactive on "lets release updates", stopped at the approval gate; test/buildPlugin 137/0, verifyPlugin 8/8 Compatible (ladder grew to PS-263), asset `cmp`-identical, `marketplace-upload` green in 12s; the "CLI 2.1.200+" line landed in README, plugin.xml and the feed. Notes framed as "first impressions"; screenshots 01/03/04/05 remain the user's upload errand.
- **2026-09-04 (second)** — early-exit "CLI may be out of date — run `claude update`" hint (Kotlin `sawFrame` → `early:true`; fixture 73; harness 630). The stub e2e exposed two instant-death bugs, both fixed: stderr thread not drained before `waitFor()` returned (ERR box empty), and `sendInitialize()` throwing on a dead stdin left `cli` unassigned (now runCatching, assigned before `start()`). `manual` cutoff measured on real binaries: 2.1.200 works fully, 2.1.199 rejects. Traps promoted to gotchas § Testing (gradle daemon caches PATH → `./gradlew --stop`; stub-CLI sandbox fails 3 fixtures).
- **2026-09-04 (first)** — three first-impression fixes (fixtures 70–72): `/context` as one red block = CLI drift (built-ins now arrive `model:'<synthetic>'`), fixed by draining the stash on the RESULT's `is_error`; `.t-sfx` nowrap + `flex:0 0 auto`; `mcpNotice` per fault (needs-auth muted, failed red; locally-disabled servers are OMITTED from the init roster). Windows fold report NOT reproduced (waiting on the DevTools snippet). Old-CLI vocab translation rejected → became the 2.1.200 floor hint.
- **2026-09-04** — chat.css → 10 manifest files under `webview/css/` (`CSS_FILES`, cut only at
  existing comment boundaries, byte-identical concatenation; `RenderLimitsTest` pins manifest ==
  directory == mockup `<link>` order). Marketplace shots 04/05 jitter between runs of ONE build →
  pixel-compare, never byte-compare (gotchas § Webview). Committed on the ask.
- **2026-09-01 (fifth)** — Marketplace shots 01+03 regenerated for the files-changed rows: the
  600px panel clipped, so VISIBLE scene content was trimmed (the log is bottom-anchored — only that
  moves the cut line, gotchas § Webview). 02/04/05 unaffected. Committed and pushed on the ask.
- **2026-09-01 (fourth)** — **0.12.4 released** (`e644dce`, tag `v0.12.4`): full gate, 7/7
  Compatible from the verdict files, stopped at the approval gate, shipped on "Go ahead please";
  Approved within minutes. Notes framing that worked: each fix as what now works, old behaviour in
  a trailing dash-clause.
- **2026-09-01 (third)** — Review span became the sole click target on the files block (user:
  "just link the Review, not the whole block"); fixture-first with the free control (harness 607).
  fillPath's boundary rule confirmed live: project files relative, outside-root absolute.
- **2026-09-01 (second)** — files-changed rows (one per file, project-relative via the shared
  `fillPath`, counts right-aligned) + bg-popup one-line title with FIXED width, both from the
  user's Windows screenshots (`lastIndexOf('/')` never matched `D:\…` — real bug). Fixture 60
  reshaped with a Windows step; honest note that the width assert is a regression pin, not a
  discriminator. LSP4IJ NPE at the office diagnosed as not ours.
- **2026-09-01** — giant-yellow-note misfire: `resultNote`'s end-anchored `(note:…)` regex ate a
  ~1300-char grep tail as one amber caveat; fixed with `NOTE_MAX = 400` (drop, not truncate) +
  position-0 reject, both measured off the binary's real note templates; fixture 69, harness 592.
  Trap promoted to gotchas § Protocol (structural anchoring); sandbox-panel sessions persist
  under `…-claude-brains-testing/` (now in state.md § Testing).
- **2026-08-30 (sixth)** — **0.12.3 released** (`1277ad7`): full gate, 7/7 Compatible, Approved
  within the hour; notes said plainly that 0.12.2's fix had caused the every-open flash. Stray
  `plugin/verify.log` deleted — redirect verifier output into the scratchpad, never the repo.
- **2026-08-30 (fifth)** — the flash fix that stuck: 0.12.2's `isVisible=false` was the bug (an
  invisible BorderLayout child gets no bounds → CEF default surface); browser stays visible,
  `loadUi()` on the browser component's first non-empty `componentResized` +
  `setPageBackgroundColor`. User tested both zips in the real IDE; the wrapper JPanel was removed
  on the "is it optimized?" pass. First native frame is invisible to CDP — only a real-IDE test
  can verify it.
- **2026-08-30 (fourth)** — **0.12.2 released** (`50ac66c`): full gate, Marketplace update
  1157011 Approved within the hour. The public `updates` API lists approved versions only, so its
  silence right after upload is normal — read the run log's JSON. Notes carried a ⚠️ for the
  behaviour change (CLI starts on first show of the panel).
- **2026-08-30 (third)** — first-paint flash fixed: `ChatPanel` had called `loadHTML` in its
  constructor before the component joined the tool window, so CEF laid out against its default
  surface (gotchas § JCEF); fix = `JPanel` wrapper painted `PAGE_BG`, `loadUi()` on first
  non-empty `componentResized`, child shown at `onLoadEnd` — CLI now spawns on first SHOW. The
  "until indexes are built" placeholder was the factory missing `DumbAware` (gotchas § IDE
  platform). Harness 586, tests 134, user-confirmed.
- **2026-08-30 (later)** — Marketplace 04/05 re-rendered by a plain `marketplace_shots.py 4 5`
  rerun (the script composes from live webview sources). Left as-is: the side-question hint clips
  after "Enter" at 394px — faithful to the IDE.
- **2026-08-30** — re-audit 2.1.251 per runbook (extension flat, webview +Remote Control pill; CLI: `PreModelSwitch` hook can REJECT `set_model` → 9.11, `Set model to …` echo absent before the first turn — user's screenshot corrected the audit: it draws after a turn). 9.11 built + four hand-test steps green (fixture 68, harness 586, tests 134); SchemaStore lag noted, user chose wait. **0.12.1 released** same day (gate walked, Approved within the hour). Traps re-learned: waiters must grep `BUILD FAILED`; harness + CDP injection never in one batch.
- **2026-08-29 (eighth)** — Copilot Chat 0.63 audited (built into VS Code; its OSS repo stops at 0.44, so the shipped manifest is the only truth) and DROPPED as bloat — only "terminal last command/output as context" survived, to backlog; the extracted folders were deleted after the audit. `vscode/` moved to `reference/anthropic-claude-code/` (2.1.251) with eight path-only doc edits. Trap promoted: `git mv -k` on an untracked path exits 0 without moving.
- **2026-08-29 (seventh)** — links open in the SYSTEM browser (blank PhpStorm windows were `target=_blank` on an OSR JCEF browser); fixed in three layers (JS delegate → `browse`, `onBeforePopup`, `onBeforeBrowse` cancelling main-frame http(s)), bare URLs autolink. Effort selector became a pill slider over four geometry rounds (fixed 12px stop slots; proven headless then in JCEF). Side-question hint matched to the composer. Fixture 67 with two controls, harness 575, tests 134.
- **2026-08-29 (sixth)** — goal reached a day early: checklist 82 ✅ · 46 ➖, all 17 headings ✅. 7.6 `/clear` REMOVED from the panel (user pick C; typed `/clear`/`/new`/`/reset` refused like `/model`), 8.7 + §14 decided by the git-owns-undo principle (14.1/14.3 later as a worktrees bundle). Fixtures 46/52 re-pointed; harness 566, tests 134. Marketplace: plugin.xml description fixed, five screenshots reshot via the new committed `tools/marketplace_shots.py`, hand-synced copy removed (the Marketplace takes the description from the plugin now). **0.12.0 released** (`0e1af47`) — full gate walk, marketplace-upload green. Later same day: reference re-extracted to 2.1.251, change notes trimmed to last-three-versions.
- **2026-08-29 (fifth)** — 8.14 (page-reload transcript heal) declined by the user: `refresh`/reopen covers the renderer-crash case; checklist 8.14 ➖, Next up narrowed to 8.7.
- **2026-08-29 (fourth)** — §15 closed (15.5 debugger tools → backlog [LG], 15.6 by design), 8.8/8.10 deferred; 8.11 side question measured FIRST (live probe: no `/btw` in roster, `control_request_progress{started}` → `control_response{response, synthetic}`, nothing persisted) then built mockup-first; fixture 66's free pre-feature control ran 21/23 red; side input one line at rest, panel centred on `#inputcard`. Harness 566, tests 134.
- **2026-08-29 (third)** — section marks (`## N. ✅|⬜`, one glyph, no counts) + the 2026-08-30 all-✅ goal; 1.21/1.23/1.24 built, 1.22 ➖ (`tool_progress` measured absent on stream-json), 1.25 ➖; §6/§9/§12 closed by decision, §13 via the SchemaStore-URL provider (nothing bundled) hand-checked; 1.23's 8/0 spacing bug → `.card .card-h + .t-note`. Harness 541, tests 134.
- **2026-08-29 (second)** — §11 closed: 11.5 `elicitation` answered `{action:"decline"}` (form deferred, backlog § Someday), 11.6 declined. 🚫 mark retired after 7 of 27 closed rows drifted between ➖/🚫 — ONE mark ➖, the row body says terminal's-half / declined / deferred; the By design / Declined / Deferred split still governs release prose.
- **2026-08-29** — 11.3 kill-a-task built (`stop_task{task_id}`, hover-✕ via the `.hist-del` idiom, REPLACE-only removal) and hand-tested ×4 incl. a suspended Explore sub-agent resuming; stopping-id memory pruned per roster frame. 2.1.250 roster `ambient` filtered, `window.__ambientSeen` as the measurement hook. 11.4 declined on a wire tape (`task_notification{completed}` for a FAILED agent — lifecycle ≠ verdict); kill vocabulary drift (`killed`/`stopped`) fixed, fixture 45. Traps: a sub-agent's Bash raises the parent's permission card and a probe waits on it; `cdp.py` prints `# target` on stderr so `tail -n +2` eats the first JSON line. Harness 514.
- **2026-08-28 (fourth)** — 3.6 files-changed review built: baselines from the autosave PreToolUse hook (`Autosave.handle` `snapshot` callback) → `TurnChanges` at `result`; `get_workspace_diff` probed and documented but NOT used (HEAD vs working tree, user edits included). `.files` line + `DiffReview.openChain`. Fixture 60 tap uses fixture 48's tape-and-restore idiom (a wrapper around whatever `__bridge` was at that moment fails in the FULL run). Font-size literals (63) → `--fs-*` tokens, card note 6px → `--attach-gap` (conventions § Code & assets). Tests 130, harness 490.
- **2026-08-28 (third)** — 3.5 tweak-travel built (VS Code = whole-file `accept({old_string, new_string})`; probed our stdio path: CLI applies a whole-file updatedInput, transcript keeps the ORIGINAL tool_use, `userModified:false`). `EditProposals.tweakedInput`, `DiffReview.open(current=)`, `RenderLimits.TWEAK_NOTE`. Hand test failed first on read-only `DiffContentFactory.create` → `DiffContentFactoryEx.createEditable`; whole-file card could not fold → `wholeFileHunk`. Trap: `LIM.tweakNote` is spliced from KOTLIN so it is no webview-build witness; `control.sh`'s runIde BLOCKED on the gradle client. Tests 124, harness 480.
- 2026-08-28 (second) — re-audit 2.1.246→2.1.250: only new row 1.25 (usage-limit grace banner, later deferred); `/workflow-authoring` joined the roster; four @internal cloud-worker subtypes ignored; runbook's re-audit procedure extended.
- **2026-08-28** — four docs retired (`verifier-matrix`, `renderer-parity`, `client-parity`, `manual-test`; `git show 9bd1683:docs/<name>.md`), knowledge promoted first (gotchas § Build/§ Replay, protocol § 11/§ 12, checklist § 17). Bird's-eye checklist restructure reverted as "complete mess" → conventions § Docs (reformat = show ONE section first); the accepted shape: `**id** mark [effort] **Name** — gist`, At a glance block, re-audit paragraphs in `<details>`. 802 → 601 lines.
- **2026-08-26 (third)** — 0.11.1 released (`979326c`): effort slider into the model menu, PATCH bump. verifyPlugin ×7 read by path; feed had no CDN lag; Marketplace Approved. Notes used a 🧭 Changed section. Near-miss: the feed-bump script asserted after writing build.gradle.kts (gotchas § Build).
- **2026-08-26 (second)** — checklist re-audited 2.1.241 → 2.1.246: contributions and 12-tool roster identical, CLI +`upload_device_hook_template`, `initialize` +`analytics_disabled`; row 9.10 🚫 added. Roster unchanged 2.1.233 → 2.1.246, so "sync slash-commands.md" is a label edit.
- **2026-08-26** — effort slider moved into `#modelFooter` (header "Models"); the level's chip suffix went bracket → middot → NOTHING (user: "better is to hide effort"; six candidates rendered as live `.chip-btn` nodes in `#inputbar`). Fixture 51 retargeted to `51-model-menu-effort-rail.json`, three complementary controls run. Article claim triaged: Haiku half REFUTED by the user's screenshot (no gating on capability flags), Fable thinking-switch INERT confirmed by probe — documented, not fixed. Harness 467, unit 116.
- **2026-08-25 (third)** — 0.11.0 released (`4c8899b`, tag `v0.11.0`): effort confirmation line, custom-model ✓/× fixes, /model-/effort resume parity. verifyPlugin ×7 — the FIRST attempt never ran (masked exit; verdict files still said 0.10.0 → the version in the verdict path is part of the check, gotchas § Build). Marketplace Approved incl. the 2026.2.2 EAP check.
- **2026-08-25 (second)** — model-menu polish: custom rows get the ✓ (fixture 57; `#inputbar` ID
  rules falsified two rounds of hand-derived offsets); chip `set_model` writes a `/model` trio to the
  transcript → hidden on resume (`cleanInjected`); `/effort` now shows the CLI's confirmation like a
  model change (fixture 58, supersedes the 2026-07-30 audit-trail acceptance). Harness 462, test 116.
- **2026-08-25** — 0.10.0 released (`v0.10.0`, `55fdcb1`): model-menu footer switches, 1M
  reconciliation, API-error single-render. `verifyPlugin` BEFORE the gate (7 IDEs Compatible) — the
  user interrupted to ask "was verify done?": keep the verdict table ready. Marketplace Approved after
  a few minutes' lag (same as 0.9.0).
- **2026-08-24 (second)** — model-menu footer shipped: 1M / fast / thinking switches (9.9/9.4/9.5),
  every protocol fact probed first (no 1M flag — `[1m]` is the marker; `set_model` never rejects;
  `apply_flag_settings{fastMode}` both ways; `set_max_thinking_tokens` 0/null live). User cut
  client-side validity logic and conversation markers. 1M switch reconciles from
  `result.modelUsage[].contextWindow`; API-error double-render deduped by exact text (hardened
  after "could this swallow a message?"). Fixtures 55/56 with four negative controls.
- **2026-08-24** — phantom Enter attributed to IJPL-161111 (fixed upstream; sandbox bumped to
  2024.2.6) — "it was not plugin but PHPStorm". Pending-plan replay honesty: `undecided` flag,
  abort-prefix filter, `stopForReplay()` gated on pending permissions after the unconditional
  wait slowed every reload. Context skill rebuilt around a briefing TIER (~41k → ~8k at load).
  0.9.0 released; `verifyPlugin` skipped then made mandatory (release.md 3b); an unasked
  commit reverted — authorization does not carry forward.
- **2026-08-23 (third)** — 5.6 polish committed (`92363ac`); live/replay anchor highlights share
  `highlightAnchors` (50-blocks.js) and carry an `occurrence` when a match is ambiguous. The
  phantom-Enter saga: three speculative guards on an unreproduced bug, one broke Enter, user
  ordered a full revert (lesson → conventions § Workflow; JCEF-OSR key-loop evidence and the
  `pkill -f runIde` orphan trap → gotchas).
- **2026-08-23 (second)** — plan-card comments shipped in the panel (`c0df900`): measured from
  VS Code screenshots + transcript (plans are files in `~/.claude/plans/`, comments ride the deny
  tool_result as `[Re: "anchor"] note` lines); user's call: full approve surface stays available
  with comments pending (VS Code collapses). Fixture 53 = 37 asserts over eight control runs;
  harness 361→398, gradle 109→113. Keyboard-only pill → backlog.
- **2026-08-23** — checklist re-audited 2.1.233 → 2.1.241: no new surfaces either side; what moved
  was measurement — `stop_task`, `side_question`, `apply_flag_settings{effortLevel,fastMode}`,
  `set_max_thinking_tokens`, `rename_session` all ANSWER over stdio (probes pre-paid for 8.11/9.4/
  9.5/11.3); `/clear` grew a `[name]` hint (7.6 decision opened); the missing 2.1.233 baseline was
  downloaded as a gzip-wrapped vsix carrying the CLI binary (runbook). Trap promoted: a binary's
  `new Set([...subtypes])` is the relay whitelist, not the stdio accept list (gotchas § Protocol).
- **2026-08-21 (second)** — replay drew "Conversation compacted" ABOVE its `/compact` bubble: the
  CLI writes boundary+summary at compaction END, physically before the command records — file order
  lies, timestamps don't. Fixed, then generalized as `DisplacedAnchor` (the retry-storm reorder
  refactored onto it, byte-identical by `probe --json` + cmp); +2 order tests with a neutered-guard
  control (107 → 109). Live-only "Distilled" footer / resume-only summary box KEPT as deliberate
  divergences (renderer-parity Audit 2). Lessons in gotchas (§ Replay, § Testing) and decisions.
- **2026-08-21** — "File not found" on a live path: `LocalFileSystem.findFileByPath` reads the VFS
  SNAPSHOT, not disk (not Windows-specific). Reproduced against the user's LIVE PhpStorm via its own
  MCP bridge using read-only `checkDocumentDirty`; fixed with `findVFileOnDisk` (refresh) on the two
  "open this path" callers only — read-locked callers must not refresh (deadlock). Lessons live in
  gotchas (VFS staleness, sandbox CDP port ≠ the port you asked for) and decisions 2026-08-21.
- **2026-08-19 (second)** — 0.8.0 released (`dce3600`: aliases, autosave hook, roster reload,
  close_tab, lock sweep; webview split rides unadvertised); verifier ×7 clean, Approved same day;
  plugin.xml description un-staled (web description stays hand-edited — user errand); Marketplace
  API lag seen a fourth time (the plugin PAGE is the truth); JetBrains' live IDE-run check debuts.
- **2026-08-19** — the webview split ships (`41f24f9`: chat.html → markup + 14 js files spliced by
  WebviewAssets, RenderLimitsTest asserts the ASSEMBLED page; controls run); "Very High" wrap fixed
  (JCEF-only flex base — nowrap on `.ef-label`); restart-on-update diagnosed as a download-cache
  race and ACCEPTED by the user; Kotlin nested-comment and Registry-port traps promoted to gotchas.
- **2026-08-17 (seventh)** — open-low mark 🟨 → ⬜ (user can't tell yellow from orange; their pick),
  lifting the register decision's own ⬜ ban with a decisions entry saying so. Load-time
  verification caught state.md citing 8.5/8.9/8.13 for rows that are 8.7/8.11/8.14 and "eight"
  [DECIDE] rows when there were nine → the re-derive-ids-from-the-register rule (conventions).
- **2026-08-17 (sixth)** — 7.7 aliases score like names in the / menu (+`canonicalCmd` before the
  allowlist gate) and 7.10 the roster survives reload (ChatPanel replays the newest raw
  `commands_changed` after the init seed). Negative control re-earned its keep: the first
  "discriminator" passed pre-fix (substring already ranked) and was re-expressed as /reset → /clear.
- **2026-08-17 (fifth)** — checklist 2.10/2.11: autosave moved onto the SDK hook lane
  (`PreToolUse Edit|Write|MultiEdit|Read`, the reference's own mechanism, no toggle) and stale
  `~/.claude/ide/*.lock` files are swept on every lock write, dead pid only — 15 corpses had
  accumulated because `delete()` only runs on an orderly dispose. Traps: `hook_callback` is a
  BLOCKING control request so every path must answer; PhpStorm saves on frame deactivation, which
  silently invalidates any "before Claude reads" test run by alt-tabbing out (both in gotchas).
- **2026-08-17 (fourth)** — the checklist re-audited against both reference clients on one version
  and 2.4 finished (`close_tab` closes ONE review by name, both close tools reply reference-exact).
  Ours had swept every diff, so with two proposals open, closing one resolved both.
- **2026-08-17 (third)** — **Released 0.7.2** (`40bc060`): / menu insert-vs-send rule + Effort
  label rail; patch bump, gate held at step 6, Approved same day.
- **2026-08-17 (second)** — the "7px that was never 7px": the probe page lacked the `#inputbar`
  ancestor so headless numbers were real for the probe page and wrong for the panel — align a
  probe page's ancestor chain with the real DOM (gotchas). Fixture 51 pins the ef-label rail.
- **2026-08-17** — slash-hint watch-item closed by measurement: bare `initialize` → 51 entries,
  keys `{name, description, argumentHint, aliases?}`, no `immediate` flag on the wire (the binary
  carries one — /goal has TWO records there, the wire sends the hintless one). state.md's 🟥 ids
  re-derived from the register after paraphrase drift (conventions).

- **2026-08-17** — fixtures 49+50 green (harness 337): all three findings came from RUNNING the
  negative controls (`3c86aa2~1` not HEAD-minus-session; assertions re-expressed through
  `data-takesarg` after a ReferenceError aborted the pre-fix run; per-step state reset after
  fixture 44's masking trap). Unasked 0.7.2 release prep reverted — the "release only when asked"
  convention entry. Port-9222-wins re-confirmed; builds verified by content.
- **2026-08-16 (fifth)** — effort label aligned to the mode rows' 20px icon rail (headless probe
  numbers later corrected: no `#inputbar` ancestor on the probe page); `chat.css.bak` control lied
  (file:// Chrome refuses non-.css) — renamed, showed the real −7. `cmdNeedsArg` → `cmdTakesArg`
  (any hint inserts) after `/context` ran bare on a click; skills ride the same roster as command
  files; composer sends on Ctrl+Enter.
- **2026-08-16 (fourth)** — `/model` on a NEW conversation looked struck through: `#fade-top`
  crossing the first block because `body.at-top` was only set by scroll/replay paths; fixed via
  `updateTopFade()` in `maybeScroll()` + `clearLogUI()`. **Released 0.7.1** (`91a6ba5`), Approved
  same day.
- **2026-08-16 (third)** — **Released 0.7.0** (`59d94fc`): plan feedback + split Approve, custom
  commands, 16 built-ins. Verifier Compatible ×7 without skip. Traps: read the verifier's
  verification-verdict.txt not the tail; quote Marketplace API URLs (zsh globs `?`).

- **2026-08-16 (second)** — plan-feedback field restyled mockup-first over three user iterations
  into one shared `--warn-field` token (`.plan-fb` + `.ask-other input`) plus a `.plan-sep`
  hairline removed with the input so live and replay agree; fixture 48 +5, harness 308. Five fresh
  2400×1520 Marketplace screenshots, rendered from the spliced chat.html in headless Chrome
  because JCEF OSR tiles its paints under an emulated viewport — that trap, the force-shown-popup
  positioning trap and the `\uXXXX`-escape patching trap all live in gotchas.

- **2026-08-16** — plan feedback ships: deny text = verbatim tool_result message; approve note
  appended under `PLAN_NOTES_MARKER` via `updatedInput.plan` (probed: a `feedback` field is
  schema-dropped, stdin steering races the model call); mode rows park in `pendingPlanMode` until
  the CLI's restore broadcast. The user's manual sweep beat the harness twice — the lesson lives in
  gotchas (plan-probe traps) and decisions (three 2026-08-16 entries).
- **2026-08-15 (third)** — three user reports: the bg chip was right (roster reset at the CLI
  boundary), the stuck popup highlight was not, and one file was being named two ways (card vs
  tool line) → paths project-relative + middle-ellipsised everywhere, absolute kept on
  `dataset.path`/`title`. `<local-command-stderr>` and mid-turn steered messages fixed the same day.
- **2026-08-15 (second)** — 16 built-ins enabled (user-picked set) and every one driven through the
  LIVE panel, not the headless smoke: `/context` rendered nothing (bare whole-message `assistant`
  frame, zero stream events → `msgStreamed`), `/security-review` dropped its `<local-command-stderr>`
  (string-content `user` frame). The fix then double-rendered thinking turns — the CLI emits an
  `assistant` frame PER CONTENT BLOCK — and fixture 47's own guard caught the over-correction within
  the hour. Lesson kept: tape the wire before blaming either side. Harness 256, Kotlin 103, register 0.
- **2026-08-15** — custom commands / skills / MCP prompts auto-enable in the / menu (3.1 + 9.10 →
  register 0 open). The approved Kotlin disk-scan plan died in Phase 0, correctly: the wire marks
  every custom entry with a " (project)"/" (user)" suffix, so a webview-only parse won. The CLI
  watches the PROJECT commands dir itself (~2.5s drop / ~1s delete); `~/.claude/commands` is not
  watched — a first probe conflated its push with `/reload-skills`' inside the debounce window.
- **2026-08-14 (second)** — 0.6.0 released + Approved (verifier Compatible ×7, asset identical,
  upload green in 14s). Two stale doc premises fell out of the run: `updatePlugins.xml` still said
  "no Marketplace", `overview.md` used the xmlId listing URL that 404s. Approval-lag lesson
  promoted to gotchas § Release.
- **2026-08-14** — `CliFileSync` + `Vfs.refreshFromDisk` shipped: edits land in open editors and
  new files appear without "Reload from disk". Scoping it to Write/Edit/MultiEdit was measurably
  not enough — the first real turn did both writes in ONE Bash call — so a turn-end root sweep
  covers what names no path. Verified through the plugin's own MCP bridge (`openFile` refreshes
  nothing, so it reports exactly what the VFS knows); 100 Kotlin tests with the negative control
  run, harness 217/217. Lessons in gotchas.
- **2026-08-13 (sixth)** — the in-flight gutter dot shipped (white/pulsing → green/red, `--dot-c`,
  `--pulse-period`, first `prefers-reduced-motion` block). Five real-panel bugs the green harness
  could not see, root cause: fixtures fed bare blocks in `#log` with no `.turn-body` — the harness
  gained a per-step `setup` hook. Halo, containment lift and the sub-task work-outcome dot all
  built and withdrawn the same day. Traps (launch ack, `<tool_use_error>`, containment) in gotchas.
- **2026-08-13 (fifth)** — 0.5.3 released + Approved; verifier Compatible on all seven branches
  242→262. Nearly shipped a stale zip (edit after `buildPlugin`) — settled by extracting the bytes
  and reading exact mtimes. Docs truth pass: README/release.md still said "not the Marketplace"
  two weeks after listing; six dead `CLAUDE.md` links. `verifier-matrix.md` was NOT stale — read
  a doc before "correcting" it.
- **2026-08-13 (fourth)** — `--block-gap` (18px) / `--attach-gap` (8px, user-picked from a
  side-by-side probe) replaced five drifted margins; the flex-gap-plus-margin vs collapsing-margin
  arithmetic is in gotchas. 29 pairs measured in real JCEF, negative control via `git show HEAD:`
  stylesheet injected into the live page.
- **2026-08-13** — the runIde manual sweep closed; the lesson that outlived it is that a fixture
  which never runs its own assertion is indistinguishable from a passing one (now in conventions).
- 2026-08-13 (third): title header lagged a whole turn — once-per-turn probe at message_start + seedUi() on every load; measured WHICH frame can carry the title (init too early); the negative control nearly ran against the fixed build (TaskStop left the sandbox alive) — lesson in gotchas.
- 2026-08-13 (second): 0.5.2 released + Marketplace-Approved via the automated workflow; the release session was never journaled and /context load's git-vs-journal diff caught it; machine drift caught again → state.md now leads with "check which machine".
- **2026-08-12 (fifth)** — the IN/OUT box took the diff's geometry: padding and overflow must sit on
  ONE element or the scrollbar insets; fixing that exposed three things the markers had papered
  over. All four traps are in gotchas.
- **2026-08-12 (fourth)** — three user reports; the third taught the method now in
  conventions ("do not fix what you cannot reproduce"): both busy-state defects reproduced by
  replaying REAL captured wire frames (kept at `_local/wire.jsonl` / `wire-short.jsonl`), which
  also killed the stated mechanism — the live stream sends NO user frame for a shell completion,
  so `message_start` is the only busy hook. Also: rename outside-click needed capture phase
  (every header control stopPropagations), and `"function"` moved DESC_KEYS→IN_KEYS.
- **2026-08-12 (third)** — the replay window kept the OLDEST blocks instead of the newest; fixed
  with an aligned cut at a turn boundary, plus the "N earlier blocks not loaded" top marker.
- **2026-08-12 (second)** — the reported rename bug did not exist (the CLI's own `ai-title` was
  overwriting the user's); the last manual release step died when marketplace-upload.yml went in.
- **2026-08-11/12** — UI-polish session: five user-reported defects fixed (tool-line path
  shortening, IN/OUT geometry, fold fade, history edge, permission card), then the plugin
  rename to `io.github.amitsidhpura.claude-brains`. Lessons promoted to gotchas/conventions.
One line per digested session; lessons were promoted to gotchas/decisions/conventions first.

- **2026-08-09 (seventh)** — 0.4.0 release prep, and the zip smoke test earning its keep on its
  first outing: playwright MCP "failed to start", the first real firing of the banner shipped three
  days earlier. Root cause was a GUI-session PATH without nvm — 0.3.3 had failed identically but
  SILENTLY (proven by an empty env-code diff, not assumed). The first fix, an `EnvironmentUtil`
  overlay, was a NO-OP on Linux because its shell loading is mac-only in the bytecode; the real one
  is `ShellEnv.kt` capturing `$SHELL -l -i -c "command env -0"` once per IDE run. Verifier clean on
  all seven IDEs, run three times as the APIs landed. Lessons in gotchas + the ShellEnv KDoc,
  including the literal NUL byte a heredoc put into a Kotlin char literal.

- **2026-08-09 (sixth)** — composer phantom spacing + delete-current-conversation. The 6px dead
  space above the composer was `#queue { display:flex }` outranking the UA's `[hidden]{display:none}`
  on specificity, so an EMPTY hidden queue still painted its margin; fixed by re-asserting
  `#queue[hidden]`, the `.chip-btn[hidden]` idiom (trap in gotchas). Delete-current-conversation
  shipped as leave-first: the refusal had existed because the CLI reopens the transcript per write,
  so ChatPanel now does the "new" reset, waits bounded off-EDT for the old process to actually die
  (`ClaudeCli.awaitExit(5s)` — `stop()` only SENDS the signal and a dying CLI can still flush a
  resurrecting write), then deletes and re-pushes the list.

- **2026-08-09 (fifth)** — the editor accept/reject v2 BUTTONS half, through four user-driven
  iterations each measured against 242 AND 262 bytecode before coding: toolbar icons (rejected on
  sight — unidentifiable), a top notification banner (wrong position, too loud), then the accepted
  shape, a plain bar UNDER the diff via `FileEditorManager.addBottomComponent`, polished to card
  parity (JButton client properties, because the LAF ignores setBackground; our own Lucide SVGs,
  because no platform icon is actually a tick). Added the COMBINED suggestion grant as a third
  button (`FILE_SAVED_ALL` — a permission-flow extension; the bridge verdict set stays the
  reference). Lessons all promoted: the GlobalMenuLinux launch noise, the `--`-in-an-SVG-comment
  XML failure that silently killed an icon, and `displayTextInToolbar()` having no warning-free
  path across 242→262.

- **2026-08-09 (fourth)** — 9.1 + 10.5 + editor accept/reject + 10.1/10.3, register 6 → 2 open,
  pushed as `f001e0b`. 9.1 needed both halves: live, the api_retry `error` is a five-code ENUM read
  out of the binary and the stream translator double-emits every retry (dedupe in chat.html); in
  replay, the CLI writes the concluding error record BEFORE flushing the buffered retries, so
  SessionStore reorders by timestamp. 10.5's premise was corrected by measuring the reference
  client — the IDE never writes on accept, the CALLER does — which produced the three-verdict
  DiffReview contract now in docs/ide-mcp-protocol.md § 4 and decisions.md, and the dual-surface
  edit permission (card + editor diff, first answer wins). 10.1/10.3 re-scoped to upstream policy,
  not a regression. Trap promoted to gotchas: `FileEditorManager.openFiles` does not report diff
  editors, so every find-then-close of a diff tab was a silent no-op.

- **2026-08-09 (third)** — 7.4 and 8.2+8.7 fixed, register 9 → 6 open. Both 7.4 payloads exist
  only live, so they were read VERBATIM out of the CLI binary — which also showed the harness
  envelope is longer than descMax, crowding the real summary off the line; fixed with a
  CONTENT-keyed `isInternalResult()` (RESULT_SKIP's name-keying could not express "this tool's
  COMPLETED result is still worth reading"). 8.2's phantom summary turned out to BE 8.7's root
  cause, so 8.7 closed with zero 8.7-specific code — measuring first is what found that. Fixtures
  07 + 08 each proven to pin the defect against pre-fix chat.html. Trap promoted to gotchas:
  assert on `#log`, never `document.body`, whose textContent includes chat.html's own script
  source and therefore the literals under test.

- **2026-08-09 (later)** — fixing round two, register 13 → 9 open, committed as `4a64433` +
  `fe620ef`. 4.4 live edit diffs built optimistically from the tool_use INPUT, because under
  acceptEdits the CLI never sends `can_use_tool` and the permission card had been the only live
  diff producer (MultiEdit's empty preview fell out of the same fix — multi-hunk edits were being
  approved blind); 5.9 plumbing strip and 5.14 scroll pin keyed off scroll DIRECTION; 6.4
  split-button caret wire-probed first, then hit two defects only live testing finds
  (`content-visibility` containment clipping the menu AND defeating synthetic-click assertions).
  Two lessons promoted: when live and replay disagree, establish WHICH is right before designing
  the fix; and copy a working idiom WHOLE (the conversations list's hover gutter is stable only
  because its panel is a fixed width).

- **2026-08-09 (first)** — the fixing session, register 19 → 13 open. All three webview keyboard
  chords removed (the plugin binds NO shortcuts); 1.7's Escape-reopen proved a SANDBOX artifact but
  two real Escape defects found instead; 2.8 @-mention menu had been opening INVISIBLY (zero CSS,
  so `position: static` ignored the viewport coords) and gained a full dismissal contract shared
  with the slash menu; 2.14 logged (JCEF-Linux Delete inserts 0x7F tofu); 7.3 bg chip fixed
  (`[hidden]` specificity defeat) and confirmed end-to-end against a real background task; 2.9
  drag-drop needed an AWT `DropTarget` layer because JCEF never delivers OS drags to the DOM. The
  spliced-chat.html headless harness was invented here — it is in gotchas and has been used in
  every session since.

- **2026-08-07/08** — the full 92/92 manual-test pass, 19 ISSUE notes logged. Hard-to-trigger states
  were manufactured, not skipped (network cut, auth failure, exit-2 hook, broken `.mcp.json`, CDP
  fixture injection); stitched synthetic sessions from real donor records exercised replay depth.
  Technique lives in gotchas; the two corrected beliefs (image chips, gauge on model switch) too.
- **2026-08-07** — `.claude/context/` initialized as the project's portable memory; the root
  `CLAUDE.md` (427 lines) migrated into it and deleted per the no-CLAUDE.md policy (last in git at
  `ee7e9fc`), global auto-memory folded into conventions.md, `.gitignore` un-ignoring `context/`.
