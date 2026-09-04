# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-09-04 (sixth) — 4.7 built as VS Code's rule; the row's premise was WRONG and the user's screenshot caught it
- Explaining 4.7 in plain language is what broke it open: the user sent a VS Code screenshot
  showing FOUR modes — exactly ours — against the row's claim that "VS Code's picker lists six
  incl. bypass". Read the extension's own builder (`webview/index.js` `c4()`): the picker is
  assembled per session — base default/acceptEdits/plan, `auto` when available,
  `bypassPermissions` only under `allowDangerouslySkipPermissions`, and `dontAsk` ONLY while it is
  already current. `dontAsk` is never offered, only displayed.
- Five stdio probes on 2.1.260 (throwaway scratchpad dir, never the user's config): no flag → init
  `auto` (the CLI's own default now); `--permission-mode dontAsk` → `dontAsk` VERBATIM, no alias;
  settings `defaultMode:"dontAsk"` alone → `dontAsk`; same file + `--permission-mode manual` →
  `default` (the FLAG BEATS THE FILE); settings `bypassPermissions` with no dangerous flag →
  `default` (so no guard was needed).
- That inverted my own earlier claim that our chip could lie: it could not, because we ALWAYS
  passed the flag. The real defect was broader — `permissions.defaultMode` was ignored entirely,
  so a user who set `plan`/`auto` in settings got a hardcoded Manual on first run.
- Built both halves (decisions.md): `PermissionModes.resolveStored` (null = never picked → no
  flag), `ChatPanel.pushInitMeta` seeds `__mode` from the initialize response's
  `current_permission_mode`, and a `Don't ask` row `[hidden]` unless current in `syncModeUI`.
- **A live check found the seed I had missed**: `system/init` only repeats the mode with the FIRST
  TURN, so without the initialize seed a never-picked chip sat on Manual while the CLI was on
  `auto`. Found by reading the running panel, not by any fixture.
- Fixture 76 (11 asserts): negative control on the still-running pre-fix sandbox — every
  DISCRIMINATING assert failed, and one guard THREW on a null node and aborted the run, so it was
  made null-safe (gotchas § Testing). Kotlin 143, harness 653.
- User hand-tested all six steps in the sandbox: first run Auto → settings dontAsk shows the row →
  Write+Bash denied with no cards → picking Auto removes the row → the pick persists and beats the
  file → 6.5's three right-clicks (file, folder+files `@docs/`, editor popup) all first and correct.
  6.5 closed at the same time.
- Trap: writing ANY `.claude/settings*.json` is blocked by the permission classifier (Bash and
  Write alike), so the settings-file cells ran in a scratchpad dir and the user made the real edit.

## 2026-09-04 (fifth) — full-surface audit (ten rows); 6.9 and 6.5 built; mention facts measured
- User: "detailed complete audit of vscode claude extension and tui, whether we are not missing
  any feature". Inventoried EVERYTHING at 2.1.260 (package.json, 288 host `case` labels → 97 RPC
  types, ~100 webview UI features via two background agents, 98 control + 46 `system` subtypes
  with `describe()` text, the binary's 128-name command map, CHANGELOG 2.1.200→260), grepped
  every identifier against the docs, hand-judged the misses. Verdict: no missing feature AREA;
  ten small gaps → rows 1.26–1.28, 2.12, 3.7–3.8, 4.7–4.9, 6.9, all [DECIDE]; terminal's-half
  verdicts recorded in a "Full-surface audit" details block so they are never re-judged.
  Measured while judging: `dontAsk` accepted by `set_permission_mode`; `control_cancel_request`
  has five emission sites and NO handler here; a plain one-tool turn emits none of the
  banner-class `system` frames; click-to-compact on our gauge already existed (agent miss).
- User: "finish all even if small — do 6.9 first." Built: `mentionHtml` (50-blocks.js) wraps
  path-shaped `@tokens` in `.mention` capsules (attachment-chip surface), textContent unchanged,
  `data-path` → the delegated open-in-editor handler; composer half cut by design (textarea).
  Fixture 74: 3/3 discriminating fails on the pre-change sandbox (free control), 6/6 after.
- User's screenshot request: Project-view right-click → first entry adds the selection as
  mentions. Built 6.5: `MentionAction` (first in `ProjectViewPopupMenu` + `EditorPopupMenu`,
  claude icon), pure `MentionPaths.tokens` (+4 tests), `ChatPanel.insertMentions` parks the list
  until `seedUi()`; webview `__mention` inserts `@path ` at the caret. Fixture 75 5/6 fail
  pre-change → 6/6. Harness **642**, Kotlin **141**. Sandbox left up for the user's hand-test.
- Measured for the user's questions: @-mention = CLI attaches the file before the model runs
  (1 turn); plain path = the model Reads it (2 turns). 200 KB file → cut at 2,000 lines
  silently; 2 MB file → nothing attached, model fell back to `wc -l`. Threshold unpinned.
- Trap: `ls -t` over `build/idea-sandbox/` found a stale `PS-2024.2/…0.8.0.jar`; the running
  IDE's jar is under the dir its `-Didea.plugins.path` names (PS-2024.2.6). gotchas § Testing.

## 2026-09-04 (fourth) — 2.1.260 re-audit; 13.3 probed to the wall and deferred
- Re-audit 2.1.251 → 2.1.260 per runbook, all measured (details block in the checklist): VS Code
  session sidebar grew archive/unread/groups/filters (`delete_session` gone), CLI vocabulary
  +`cloud_session_delta` +`update_settings`, roster 54 → 55 (+`/advisor` +`/reload-plugins`
  −`/artifact-design`), fable row now **Fable 5.1** (roster-driven, panel unaffected;
  `/fable/i` checks still match). Nothing else moved: tool set, tengu gates, initialize keys,
  `set_model` response all flat. Checklist now 83 ✅ · 47 ➖ (130 rows); §16 counts swept;
  slash doc updated; `reference/` re-extracted to 2.1.260.
- Method: both vsixes downloaded from the Marketplace — 2.1.260 for the new extension, 2.1.251
  for its `native-binary/claude` as the CLI BASELINE (no longer under `versions/`); runbook
  step 3 updated. User then updated the real VS Code extension → `cmp`-identical to the
  audited copy, no re-audit.
- 13.3 ("implement it") died on measurement: `update_settings` allows ONLY `outputStyle`
  (binary allowlist; `model`/`permissions` refused by name). But the CLI honors `model` and
  `permissions.defaultMode` FROM `.claude/settings.local.json` at spawn (flag beats file).
  Direct plugin file-write offered; user chose "wait for Anthropic" → 13.3 ➖, backlog
  watch-item (grep the allowlist each re-audit), decisions.md has both entries.
- Two same-day audit corrections from the attempt: VS Code doesn't ride `update_settings`
  (its mode memory is extension `globalState`), and empty-merge acceptance ≠ key coverage —
  new trap in gotchas § Protocol.

## 2026-09-04 (third) — 0.12.5 released and Approved
- User's "lets release updates" → steps 1–5 proactively: bump to 0.12.5, notes rewritten
  (0.12.5/0.12.4/0.12.3, 0.12.2 dropped), and the owed "CLI 2.1.200+" line landed in README,
  plugin.xml description AND the updatePlugins.xml feed description. `test buildPlugin` **137/0**
  (own-version notes guard green), zip audited (our jar + OSS deps only), `verifyPlugin`
  **8/8 Compatible** from the verdict files — the IDE ladder grew to 8 (PS-242 → PS-263).
  STOPPED at the approval gate with the complete notes; shipped on "Go ahead please".
- `a77a565`, tag `v0.12.5`; asset HTTP 200 and `cmp`-identical to the local zip, raw feed served
  0.12.5 immediately, `marketplace-upload` run 33859713983 green in 12s. Marketplace **Approved**
  within the hour (user's screenshot: verifier Success on 2026.1.5/2026.2.2/2026.3 EAP plus a
  clean IDE run); a one-minute API poll via a background Monitor caught the listing flipping.
- Notes framing: "first-impressions release" — four fixes as what-now-works, old behaviour in
  trailing dash-clauses; the CLI floor stated in ⚠️ Notes as 2.1.200+ with `claude update` as
  the fix.
- Nothing unreleased on `main`. Marketplace screenshots (01/03/04/05) remain the user's manual
  upload errand on the plugin 33274 edit page.

## 2026-09-04 (second) — early-exit "CLI out of date" hint; `manual` cutoff measured at 2.1.200
- (The "2026-09-05" entry below is this same day's EARLIER session — its commits are stamped
  2026-09-04 12:50; the date was written a day ahead. Left as written for greppability.)
- Backward-compat policy settled: no version floor, no shims — a non-zero exit before the CLI
  ever spoke stream-json renders a muted "Your Claude CLI may be out of date — run `claude
  update` in a terminal." under the ERR box. Kotlin `sawFrame` → `early:true` on `__exit`;
  hint only when early. Update BUTTON designed, deferred by user. Fixture 73 (3 discriminating
  asserts failed on the content-verified pre-fix build); harness 623 → **630**, Kotlin 137.
- The stub e2e exposed TWO latent instant-death bugs, both fixed: (1) `waitFor()` returned
  before the stderr thread drained → ERR box rendered empty (wait thread now joins both readers,
  bounded 1s — also settles sawFrame); (2) `sendInitialize()` threw "Stream closed" on the dead
  stdin, unwound out of `start()`, and left `ClaudeSessionService.cli` UNASSIGNED — exit frame
  read stderr/sawFrame off null (writeLine now runCatching; `cli` assigned BEFORE `start()`).
- Cutoff measured on real binaries by the user: v2.1.200 (2026-07-03) introduced `manual` and
  the whole panel works on it (multi-turn, model switch, permission card); v2.1.199 rejects it
  with the friend's exact error + our new hint. The friend just needs `claude update`.
- Traps: gradle daemon caches its env — PATH-dependent sandbox launches need `./gradlew --stop`
  first; full harness against a stub-CLI sandbox fails 3 fixtures (need real-CLI init state) —
  gotchas § Testing. User's global launcher restored to 2.1.260 after the downgrade tests.

## 2026-09-05 — three first-impression fixes (fixtures 70–72); fold report NOT reproduced
- User's Windows screenshot: a Read OUT box fully expanded, not collapsible. NOT reproduced
  despite live/off-screen/batch/replay-shaped mounts AND a real CLI Read in real JCEF — every
  path folds (72px, "Show more"). Parked per "don't fix what you can't reproduce"; the user has
  a DevTools snippet to run on the Windows box (Chromium version, `lh` support, per-holder
  fold state) + a Help→About ask. Content-visibility does NOT zero foldBlock's rAF measurement
  on Chrome 122 — that hypothesis is dead.
- `/context` drawn as one giant red block (`aafbb1c`): CLI drift — since ~2.1.24x local
  built-ins' output arrives `model:'<synthetic>'`, the tag the 2026-08-24 echo-dedupe treated
  as "API error". Fix: `onResult` drains the stash by the RESULT's `is_error` (success → prose
  `blk`); a real echo says subtype 'success' WITH is_error, so subtype can't discriminate.
  Measured live on 2.1.260 (`/context` + `/list-agents`); fixture 70, free pre-fix control;
  real `/context` re-verified in the panel (md tables, zero .error). Replay was never affected.
- Read range suffix wrapped mid-token ("(lines 1-" / "150)", user's screenshot) (`6acb681`):
  `.t-sfx` had no nowrap, so flex shrank it to min-content. Reproduced live pre-fix at 730px;
  fix nowrap + `flex: 0 0 auto` (the path's middle-ellipsis absorbs all shrink). Fixture 71.
- Friend's fresh install: unauthenticated claude.ai connectors drew ONE red line with real
  failures (`92ede19`). `mcpNotice` now renders per fault: needs-auth muted, failed red,
  disabled/pending/connected silent. Fixture 72; live-tested with a real unauthenticated
  Linear MCP (`https://mcp.linear.app/mcp` → needs-auth on the wire; the old sse endpoint
  reports `failed`), plus a manual sandbox demo for the user. Measured: locally-disabled
  servers are OMITTED from the init roster entirely (gotchas § Protocol).
- Friend's other screenshot DEFERRED: his old CLI rejects `--permission-mode manual` (vocab
  `default`, pre-2.1.220) → exit 1. User rejected self-healing vocab translation as too much;
  direction when picked up: a plain "supported CLI version" error (backlog § Immediate).
- Harness baseline **607 → 623** (fixtures to 72); Kotlin 137 untouched. Three fix commits +
  this save pushed on the user's ask.

## 2026-09-04 — chat.css split into webview/css/*.css (the JS-split mechanism)
- User: "same as js — let's split css also." The 1295-line `chat.css` → 10 tens-numbered files
  under `plugin/src/main/resources/webview/css/`, cut ONLY at existing top-level comment
  boundaries, order preserved; the concatenation diffed byte-identical to HEAD's chat.css modulo
  the reworded top banner. chat.css deleted.
- `WebviewAssets` gained `CSS_FILES` + `css()` (banner line per file); `loadUi` splices it at
  `<!--CSS-->` as before. `RenderLimitsTest` +3: css manifest == directory, mockup `<link>` list
  == `CSS_FILES` in order, no `</style>` in the splice. `./gradlew test` **137/0**.
- `design/mockup.html` and the four `design/*-probe.html` carry ten `<link>` tags now;
  `tools/marketplace_shots.py`'s `js_files()` generalized to `manifest(name, ext)` and reads both
  manifests. Living chat.css comments (JS, Kotlin, limits.md, gotchas) re-pointed; historical
  records (journal/decisions/fixture provenance/checklist) left as written.
- The real file order surprised twice: the history panel sits at the END of `40-cards.css`, and
  `50-side.css` also carries attachment chips, lightbox, todos, status lines and the compaction
  marker — the manifest comments state it honestly.
- End-to-end: marketplace shots 01/03 byte-identical; 02/04/05 showed only antialiasing jitter
  (04/05 differ even between two runs of ONE build → new trap under gotchas § Webview: pixel-
  compare, never byte-compare); jittered PNGs restored to HEAD. Live sandbox over CDP: 10 css
  banners in the page, tokens resolve, gallery states styled. Committed and pushed on the user's
  ask (this save included).

## 2026-09-01 (fifth) — Marketplace shots 01+03 refreshed for the files-changed rows
- User asked to update `design/marketplace/01-conversation.png` for the 0.12.4 files-changed
  review change. The script composes from the live webview sources, so a plain rerun rendered the
  new block — but its extra height overflowed the 600px panel and the capture clipped the left
  scene's "Thought for 6s" mid-row. Measured headless (`#log` scrollHeight − clientHeight = 41px),
  then dropped S1_LEFT's thinkBlock (the right panel keeps a think row); the residual 3px of
  scroll dies in top whitespace.
- `03-control.png` was stale the same way (its caption sells the per-turn review). Regenerating
  pushed the "Bash Run the CSV tests" header off the top — the log is bottom-anchored, so only
  trimming VISIBLE content moves the cut line (promoted to gotchas § Webview). Dropped S3_RIGHT's
  done row and shortened its md sentence; header back, all three file rows clean.
- 02/04/05 confirmed unaffected: no files block, and no scene opens `#bgMenu`, so the 0.12.4
  popup fixes change none of their pixels.
- The standing screenshot-upload errand now covers 01+03+04+05 on `plugins.jetbrains.com/plugin/33274`.
- Committed and pushed on the user's ask (this save included).

## 2026-09-01 (fourth) — 0.12.4 released and approved
- User's "Please release it" → full gate: bump to 0.12.4, notes rewritten (0.12.4/0.12.3/0.12.2,
  0.12.1 dropped), `test buildPlugin` green with the own-version notes guard passing, zip audited
  (our jar + OSS deps only), `verifyPlugin` **7/7 Compatible** read from the verdict files,
  feed + README checked. STOPPED at the approval gate with the complete notes; shipped on
  "Go ahead please".
- `e644dce`, tag `v0.12.4`; GitHub release created, asset HTTP 200 and `cmp`-identical to the
  local zip, `raw.githubusercontent.com` feed served 0.12.4 immediately, `marketplace-upload`
  run 33518632052 green in 9s.
- Marketplace API still listed 0.12.3 as newest approved right after upload (normal moderation
  window) — the user's screenshot minutes later showed **0.12.4 Approved**, verifier Success on
  2025.3.6.1 / 2026.1.5 / 2026.2.2 rc plus a clean IDE run.
- Notes framing that worked: the three fixes stated as what now works, with the old behaviour in
  a trailing dash-clause (Windows full-path run, two-line task name, giant yellow caveat line).

## Digest
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
