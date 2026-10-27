# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Entries older than ~2 weeks are compressed into the **Digest** at the bottom — outcome, why, and the
key rejection, one entry each. Never delete; mark superseded.

## 2026-10-28 — 0.17.1, a PATCH: the deprecation fix alone, shipped the same day
One user-invisible fix since v0.17.0 → patch, by release.md's progression. Shipped promptly rather
than held for the next feature so the Marketplace's 263 warning stops showing on the current version.
Notes say plainly that nothing visible changes. **Rejected:** folding it into a later feature release
(the warning would sit on the listing meanwhile).

## 2026-10-28 — Editor tabs open through `OpenFileDescriptor.navigate`, editors taken from `fileOpened`
**Decision:** `openInEditor(project, file, onEditor?)` (bridge/DiffReview.kt) replaces
`FileEditorManager.openFile(file, focus)` at all three call sites. **Why:** on 263 every synchronous
FileEditorManager open (openFile ×3, openEditor, openFileEditor, openTextEditor) is deprecated for
suspend functions that do not exist on 242 (javap of both IDEs' jars); `navigate` is clean on both.
The verdict bar needs the FileEditors, and 263 may open the tab after `navigate` returns, so a
`fileOpened` subscription made BEFORE navigating supplies them, with a `getEditors` lookup after for
the synchronous case. **Rejected:** `openFile(file, focus, searchForOpen)` / `openFileEditor` (also
deprecated on 263); calling the suspend API by reflection on 263+ (two code paths, a coroutine scope
to own); reading `getEditors` right after `navigate` only (an async open would silently drop the bar).

## 2026-10-28 — 0.17.0, a MINOR bump, shipped WITH PS-263's three deprecation warnings
Three features since v0.16.0 (Bookmarks, saved messages, copy image) → minor. The verifier's PS-263
verdict "Compatible. 3 usages of deprecated API" (`FileEditorManager.openFile(VirtualFile, boolean)`)
was presented at the gate and shipped: warnings do not block publication, the method still runs on
263 (the Marketplace's live IDE run was clean), 0.16.0 already carried them, and changing platform
calls at the gate would have needed a fresh verify against 242 too. Fix is 0.17.1, on ask.
**Rejected:** fixing at the gate (unverified replacement on the 242 baseline); 0.16.1 (features in it).

## 2026-10-28 — Saved messages (7.11) are ordinary CLI command files, written by a native dialog from a hover control on the sent prompt
**Decision:** "Save as command" on a sent prompt's bubble → `SaveCommandDialog` (name, optional
description, All projects / This project, the text editable) → `<name>.md` in `$CLAUDE_CONFIG_DIR`
(else `~/.claude`)`/commands` or `<project>/.claude/commands`; frontmatter only for what is set, plus
`argument-hint "[text]"` when the text holds `$ARGUMENTS`/`$1`-`$9`. Names `[a-z][a-z0-9_-]{0,63}`
(what submit()'s regex can route); built-ins/aliases/`/btw` refused (the page sends them as `taken`).
**Why:** the CLI already has saved prompts — the panel's / menu lists custom commands (7.3) — so the
gap was only creating one; the same file works in the terminal and VS Code and the CLI does the
expanding. The user picked hover button + native dialog from AskUserQuestion. **Rejected:** a
panel-only snippet store (a second list that drifts, terminal can't use it — the Philosophy); a
right-click menu (the panel has none, less findable); an inline card form (more webview code);
subfolder `sub:name` names (submit()'s `/name` regex excludes `:`).

## 2026-10-28 — Recent first (7.12): recency breaks ties INSIDE each match tier; app-level store fed by the page
**Decision:** `renderSlash` sorts by score, then last-sent time desc (stable, so never-sent keep the
CLI's order). The page reports `cmdUsed` (canonical name, aliases resolved, refused commands not
counted, `/btw` counted) and updates its own copy at once; Kotlin keeps `{name: epochMs}` newest 100
in `claudeCode.commandRecents` (application `PropertiesComponent`), seeded as `__cmdRecent`.
**Why:** an exact name must still win (user's pick "Recent first in every list"); app-level because
user commands span projects. **Rejected:** a separate "Recent" group on a bare `/`; most-used
(frequency) ranking — slow to adapt; webview `localStorage` (no origin under `loadHTML`).

## 2026-10-28 — Marketplace screenshots: 420px panels, the measured roster, fixed 10:42 AM stamps; eight scenes
Panels widened 394 → 420 because "Default (Opus 5.5)" wrapped beside the gauge; the roster is
fixture 97's 2.1.293 rows (no stale "Opus 5 / 1M"); every prompt stamped `at(10, 42)` with 05's
history on the same clock; context sizes for a 200K window. New 06 bookmarks + saved commands, 07
selection pill / @-files / date lines / copy, 08 background pane + Run in background; 02 shows option
previews. **Rejected:** the export popup in 07 (covered the date lines it was there to show).

## 2026-10-28 — Bookmarks (8.15): VS Code's parts, drawn as a card above the composer
**Decision:** a card riding the composer (the side card's shell) with minimize/maximize, rows +
age, a second click on the open row closes it (list only), detail with Show in conversation /
remove / the reply. Keyed by the assistant record's uuid; text read back from the transcript;
store = VS Code's file shape `{updatedAt, bookmarks:[{uuid, addedAt, writtenAt}]}` per
conversation in OUR folder under `PathManager.getConfigPath()`. **Why:** the user works through a
reply's parts one at a time; a side panel would halve a narrow tool window; a copied text goes
stale and duplicates the transcript; config (not system) survives Invalidate Caches.
**Rejected:** VS Code's side panel (cramped); sharing VS Code's `globalStorage` folder (private
format); `PropertiesComponent` with a markdown snapshot (the first plan — stale copy, settings
store not meant for growing data); tick-off checkboxes in the pinned copy (offered, not picked);
auto-selecting the newest row on reopen (made moot by the toggle — list only is the rest state).

## 2026-10-26 — Copy image's check flashes on SEND (after decode), not on click
**Decision:** `copyImage(src, btn)` calls `flashCopied(btn)` from the canvas `onload`, after the
`copyImage` bridge frame; callers pass the button. **Why:** the tick should mean "sent"; an undecodable
picture shows no tick. **Rejected:** keeping the flash on the click (simpler, but claims success
before the work happened).

## 2026-10-26 — Popup menus paint ONE highlight (the cursor `.sel`); hover moves it only after a real pointer move
**Decision:** a capture `keydown` disarms hover; a `mousemove` at new screen coordinates re-arms it
and selects the row under the pointer; `mouseover` selects only while armed. The `:hover` background
is removed from `.popup-item` and `.card-menu .popup-item` — the pointer still lights the row it
points at because a real move puts `.sel` there. **Why:** a menu redrawing under a resting pointer
gets a mouseover per keystroke and NO mousemove (measured over CDP), so `/con` with the mouse parked
selected `/goal`; then the user saw two identical highlights (cursor + :hover) and could not tell
which one Enter would run. **Rejected:** a separate dimmer :hover tint (still two highlighted rows);
ignoring hover for a fixed time after typing (a timer guesses, a move is the real signal);
`pointer-events:none` on the list until a move (breaks the first click).

## 2026-10-26 — The ask card's preview box holds the TALLEST option preview (invisible sizers in one grid cell)
**Decision:** `askPrevHtml()` adds every option's preview and the no-preview sentence as
`visibility:hidden` fold-capped `pre.ask-prev-sz` in the same grid cell as the painted `pre`, which
stretches to the cell; live and replay share it. **Why:** the box resized per hovered option and,
with the card anchored to the log's bottom, the list slid under the pointer and the hover flipped
(reproduced: list 365→328→365→346 px). Pure layout — follows panel width and the fold contract with
no JS measuring. **Rejected:** measuring heights in JS and setting `min-height` (re-measure on every
resize/fold change); pinning the box to a fixed line count (clips or wastes); scroll-anchoring the log
(the card still changes height, only the jump moves).

## 2026-10-26 — Copy image: a hover corner button on tool-returned images + a round Copy beside the lightbox ×; PNG through Kotlin
**Decision:** user's picks T2 (hover button on the image's top-right; beside the picture when it is
under 64px wide) and P1 (round button beside ×, popup stays open), chosen from candidates rendered in
the real panel. JS redraws the image on a canvas and sends PNG base64 (`copyImage`), Kotlin decodes
with ImageIO and sets an image Transferable on `CopyPasteManager` — the same single clipboard route as
text (decision 2026-10-10). **Why:** a tool can return JPEG/GIF/WebP and ImageIO has no WebP;
`navigator.clipboard` is out for the same reasons as text. **Rejected:** a right-click menu (T1,
offered, not picked); a caption-row text button (P2); a copy button on the user's own attachment
chips (too small, would sit beside the composer's ×, and the chip already opens the popup — user
agreed 2026-10-26; a right-click item stays on offer).

## 2026-10-26 — The date line is a soft wave (pick G: 2px amplitude, 24px period)
--border painted through a repeating SVG mask so the colour stays a token. Chosen from seven rendered
candidates (B-G); the first round was "very aggressive curls, have it subtle". **Rejected:** a
straight hairline (the old look), tighter periods (read as stitching).

## 2026-10-26 — Harness fixtures never send a real prompt unless the CLI's answer is what they test
Fixture 04's chip check called `sendTurn` through the REAL bridge, so every full run cost a turn and
its reply streamed into later fixtures (fixture 102's intermittent failure; the "Session status
inquiry" conversations). The bridge is stubbed around the call (fixture 96's tape pattern).
**Rejected:** loosening fixture 102 to tolerate log growth (would hide a real regression).

## 2026-10-11 — 0.16.0, a MINOR bump; a halted release restarts from step 1, with the bump and feed kept OUT of git until the asset exists
One feature (6.6 + Include open file) plus two fixes since v0.15.0 → minor. When the user halted
at the gate for a styling fix, the prep stayed in the working tree uncommitted and the fix was
committed on its own — a pushed `updatePlugins.xml` without its asset 404s every custom-repo
update check. "Do the release steps from start" meant re-asserting steps 1-5 and a fresh
`test buildPlugin verifyPlugin`, not trusting the earlier green run (the zip had changed).
*Rejected*: 0.15.1 (a feature is in it); committing the bump early "to keep the tree clean".

## 2026-10-11 — Copy response control: a FLOAT at the first line, opacity-hidden until hover, no plate — the one reserved-space exception
The hover-only plate pinned absolute at the block's corner sat over the text column and clipped a
long first line (user's screenshot; rendered side by side in the real panel). Now inserted as the
block's FIRST child and floated right: the first line wraps around it, nothing is ever covered.
Hidden by OPACITY, not display — a float that comes and goes reflows the line on every hover — so
one icon's width stays reserved at the end of one line: the deliberate exception to "never reserve
space for hover affordances" (conventions), taken over covering or jumping the text. `.codeblock` /
`.diff` clear it (a block child would run under it and meet its own header copy); `.blk > .blk-copy
+ *` drops its top margin (the dot/first-line alignment the user caught). Hover on the icon only
brightens it (user: no plate). *Rejected*: always visible ("so many copy buttons"); a footer row
under the reply (a row per reply); padding the whole block's right edge (narrows every reply).

## 2026-10-11 — A zero-turn result the page never waited for draws no summary; the orphaned-task notification stays undrawn
The CLI's empty wake on `--resume` (a background task of the previous process reported as
"stopped": task_notification → init → result num_turns 0) is machinery, not a turn: `onResult`
returns when `num_turns === 0 && !busy && !workStart`. Not `!reqTokens` — it holds the last
request's count until the next sendTurn (fixture 100 step 3). The "stopped" notification is NOT
drawn: it names a task this panel session never showed, so a status line would be noise of another
kind (agreed with the user). *Rejected*: hiding the summary by `duration_ms` (a real 0-token turn
can be fast); drawing the notification as an 11.7 roster event (nothing to open, no row to end).

## Digest — decisions 2026-10-10 (compressed 2026-10-28; full text via `git show d86a9c7:.claude/context/decisions.md`)
- **6.6 "Include open file"** — a second switch (OFF) beside "Include selection" (ON); highlight wins, selection OFF + file ON sends content over a highlight; the page sends a FLAG, ChatPanel reads the buffer at send time, 50,000-char cap, >4 MB not read · rejected content on by default, pushing content through the tracker, 20 K / no cap.
- **6.6 the PANEL writes the selection tag** · the CLI's own `selection_changed` route is dead in stream-json (4 variants on 2.1.296, haiku saw nothing; consumer is a TUI hook needing `ws-ide`/`sse-ide`) · rejected `ws-ide` in the mcp-config (filtered), `FORCE_CODE_TERMINAL`.
- **6.6 design** — LIVE pill following the editor, × per message, paperclip entry always visible, app-level persistence · user: "know what is going to be sent and not after send" · rejected snapshot at send, hiding the entry when off.
- **6.6 wire shape** — one text block, prompt first then the CLI's own tag wording; a tag-only text stays the TUI's dropped record · titles / `last-prompt` read the prompt · rejected a separate content block, parsing tag-only texts.
- **0.15.0 minor bump** — six capabilities since v0.14.0; notes name behaviour only, the 1M switch's removal listed as a fix · rejected 0.14.1.
- **9.9 the 1M switch RETIRED** — the CLI serves 1M with or without `[1m]` (measured 4 picks), the switch only broke the gauge denominator; the gauge follows the CLI's window · rejected hiding it, keeping it with a lookup fix (snaps back every turn).
- **Model list capped by MEASURED fit** — the whole roster, scrolling between header and composer, `capToRows` clamps by the popup's overflow · rejected a row count, folding, CSS max-height.
- **15.4 export** — a header popup with the CLI's own `export_conversation` text, asked once per open, Save sends back the shown text; Copy response per text block, markdown source · rejected an actions row, a DOM-built export, Kotlin re-asking on Save, typed `/copy` `/export`.
- **Every copy through Kotlin's `CopyPasteManager`** · `navigator.clipboard` needs focus + user activation a tool-window webview lacks; `xclip -o` verifies · rejected an `execCommand` fallback, two mechanisms.
- **Day labels relay at local midnight on ONE timer** (`placeDaySeps`, pure DOM) · rejected a minute timer, absolute dates.
- **11.7 task output on the ROSTER ROW, polled 1 s while visible; "Run in background" gated by `task_started{is_backgrounded:false}`; a pane dies with its row** · the timeline already says how a task ended · rejected output under the Bash line, a `task_type` allowlist, keeping ended panes.
- **4.10 built from the schema without a real frame; declare exactly `auto_mode_server_fallback`; undeclared kinds logged and unanswered; `continue|interrupt` allowlisted in Kotlin** · the frame can only arrive after declaring, so waiting is circular · rejected waiting for a frame, replay support, any-URL Learn more.
- **1.31 answered ask card keeps tabs + adds a summary; previews are plain text, hover > pick > option 0; no auto-advance on a panel with previews** (user picked B from a render) · rejected markdown previews, a compact answered card, hover-only previews.
- **1.30 timestamps** above the prompt and the turn's FIRST reply text; date lines in `#log` between turns; always on; relative labels · rejected a tooltip (never shows on Linux JCEF), `toLocale…`, a stamp per API message, a switch.
- **Re-audit scope** — skip the changelog's `[Cloud sessions]`/`[Claude Tag]`/`[Code Review]`; open [DECIDE] rows rather than deciding; probe cleanup the classifier refuses goes to the user, never worked around.
- **Push status line stays "Pushed <branch>"** — the frame carries only kind/branch/cwd; richer detail declined by the user (backlog § Deferred).
- **Spaced mention paths QUOTED (`@"a b/c.txt"`) in the webview by the official `OL0()` rule** · a bare spaced mention attaches nothing (2.1.295) · rejected backslash escaping (measured: no attachment), quoting every mention.
- **Bold admits single `*` inside and stops at the first `**`** (fixture 90) · rejected a CommonMark delimiter-run parser, a lazy `[\s\S]+?`.

## Digest — decisions 2026-09-27 → 2026-10-08 (compressed 2026-10-26; full text via `git show fccc30b:.claude/context/decisions.md`)
- **2026-10-08 — The thinking body is trimmed in `thinkBlock()`** · the CLI's thinking ends `\n\n` (13/13) and `.think .body` is the one pre-wrap block; one trim in the shared builder fixes live/replay/gallery/redacted (fixture 89) · rejected rendering thinking as markdown (second renderer path), a CSS negative margin (hides the data), trimEnd only, a Kotlin-side trim.
- **2026-10-08 — The Default selection follows the model the CLI actually serves; only Default is relabelled** (chip "Default (Fable 5.1)", no `set_model`, nothing persisted; named selections never relabelled; gauge matches the effective id) · the roster's default row never reflects a settings `model` / `ANTHROPIC_MODEL` / terminal `/model` (measured 2.1.283 + 2.1.293), the user burned Fable under a chip saying Opus; FOLLOW the CLI, never push · rejected moving the tick to the real row, relabelling named rows on mismatch, clearing the override per roster push. Fixture 88, checklist 9.1.
- **2026-09-27 — `claude` is resolved on the SHELL PATH (`ShellEnv.path()/which()`), the same overlay the CLI is spawned under** · the panel ran the VS Code extension's bundled 2.1.270 for weeks while the terminal ran 2.1.283 (lookup used the IDE's bare PATH, spawn used the shell overlay); negative control proved it · rejected newest-binary-wins, documenting `-Dclaude.executable`, dropping the VS Code fallback.
- **2026-09-27 — The picker shows the CLI's whole roster, pinned versions included, no folding** · the TUI's own `/model` lists all 11 rows on the same CLI · folding under "Previous versions" offered, not asked → backlog § Someday.

## Digest — decisions 2026-09-13 (compressed 2026-10-08; full text via `git show efa89aa:.claude/context/decisions.md`)
- **2026-09-13 — The ordinary card's reject note sits ABOVE the buttons, placeholder "Tell Claude what to do instead · applies to Reject"** (supersedes the 2026-09-05 inline placement) · the 34px field as a flex item stretched every 26px button on Bash/Write cards while the plan card kept 26px — two heights for one control family (user's screenshot); above the buttons, three cards are one structure · rejected shrinking only the inline field (inconsistent with plan/ask fields), 34px footers everywhere, `align-items: center` (uneven row).
- **2026-09-13 — A plan-comment draft is settled by the next selection, never blocks it** · `if (composing) return` made a second selection silently do nothing; committing is recoverable (every row has ✕), ignoring is not discoverable · rejected several open composers, a hint/flash, commit-on-blur, a labelled Add button.
- **2026-09-13 — 1.29 Bash edit diff draws as resolved edit cards, no panel toggle for the CLI's gate** (`fillAppliedCard` per file, `MAX_BASH_DIFF_FILES` 10, drawn on error results too, one `appendBashDiff` for live and replay) · the 4.4 auto-approved-edit surface already says "the edit ran"; the gate (`bashEditDiffEnabled`) is a settings key — configure in the terminal · rejected one combined card, a custom compact block, hiding on `is_error`.
- **2026-09-13 — The public changelog is a re-audit LEAD source, never evidence** (`reference/claude-code-log/`, runbook step 3b) · label/subtype diffs miss behaviour changes that reuse identifiers; By-design items need only existence · boundary: a note creates a candidate row or probe target, steps 4-7 still measure · rejected the blanket "nothing from release notes" rule (no war story behind it).

## Digest — decisions 2026-09-01 → 2026-09-09 (compressed 2026-09-27; full text via `git show 34bc25c:.claude/context/decisions.md`)
- **2026-09-09 — 0.13.1 ships the two renderer fixes alone, as a patch** · fixes to behaviour the user hit daily; plain semver · rejected bundling the four known renderer gaps (each needs its own fixture; named as known in the notes).
- **2026-09-09 — Lists parse by CommonMark structure** (`mdList` in `20-markdown.js`: content indent, loose/tight, `<ol start>`, lazy continuation, items re-enter `mdBlocks`) · numbered lists restarted at `1.`; the user refused the prompting workaround — the renderer takes what the model writes; fixture 86, control 17/38 · rejected `start`-only, splitting on a marker change, indented code blocks and `~~~` (backlog).
- **2026-09-08 — A fence is 3+ backticks and closes only on a run at least as long** · a ````markdown fence split at three and a whole table vanished behind a `B0 \`` leak; fixture 85, control 8/15 · rejected line-start anchoring (drops inline ```x```), `~~~`.
- **2026-09-05 — Checklist rows fold**: gist line + "Read more…" (`<!-- --><details>`, no blank lines, no nested lists) · 2,300-char rows; §3 shown first, approved, then the file · rejected uniform folds, dates on the first line, sub-bullets in folds (the marked defect).
- **2026-09-05 — 1.26 banner family through ONE status renderer, REPL-only subtypes included, per-kind glyphs** (three candidates rendered side by side in the real panel) · only `vcs_state_changed`/`notification` on the wire at 2.1.261; a branch per public schema costs nothing; first frame per subtype kept in `window.__bannerSeen` · rejected bare lines, drawing `turn_duration`, icons for text-only subtypes.
- **2026-09-05 — 1.28 a withdrawn ask settles the card, sends nothing; the CLI's auto-deny box stays** · `control_cancel_request` probed real; Kotlin forgets the entry BEFORE the panel hears, so a racing click sends nothing · rejected VS Code's late-answer fold-in (no wire).
- **2026-09-05 — 1.27 the cut MARKER opens the whole text** (live from the page-lifetime copy, replay via `SessionStore.toolText` by `toolId`; read-only `LightVirtualFile`) · the row's own click is the fold toggle; the wire stays capped · rejected full texts in replay frames, live-only.
- **2026-09-05 — 3.8 editable command: the grant FOLLOWS the edit, split per part** (`EditProposals.splitCommand`; single-rule cards keep Always allow) · a whole-compound rule never matches (the CLI checks parts); the model seeing the original beside the edited output is the CLI's design, user "ok with model's confusion" · rejected hiding Always allow on edit, a whole-string rule, updating the IN box.
- **2026-09-05 — 3.7 reject-note field: DENY only, Enter submits** (inline after Reject — layout superseded 2026-09-13: above the buttons) · deny text reaches the model as the tool_result; an allow has no wire for a note (probed 2.1.233); three side defects fixed the same day · rejected hidden-until-click field, note on Accept.
- **2026-09-05 — 2.12 extra content roots as `--add-dir`, read at launch only** (`WorkspaceRoots.extraDirs`) · measured: a Read in an attached root asks on every touch without it · rejected runtime `register_repo_root`, path rules as the workaround.
- **2026-09-05 — 4.9 number-key card answers DEFERRED; no keyboard shortcuts on any card** · the user's call; extends the 2026-08-16 plan-card rule and the 12.4 no-chords stance · reopen only on the user's ask.
- **2026-09-04 — 4.8 as a SPLIT BUTTON**: main half = the suggestion's own `destination`, caret = session / project-shared / all projects · hot path unchanged with parity kept (the user overruled my decline recommendation) · rejected VS Code's cycling link; precondition: probe which echoed destinations 2.1.260 honours.
- **2026-09-04 — 4.7 as VS Code's rule**: no `--permission-mode` flag when nothing is persisted (chip seeded from `initialize`'s `current_permission_mode`), a `Don't ask` row only while current · the flag had beaten the user's `permissions.defaultMode` on every launch; "six modes" was the picker's maximum, never a session · rejected ➖ unreachable-by-design, offering dontAsk as a fifth pick.
- **2026-09-04 — Full-surface audit: every gap gets built, small ones included** · user "finish all even if small"; terminal's-half verdicts written into the checklist block so they are never re-judged; 6.9 = sent-bubble chips only (a textarea cannot host a chip), 6.5 = context-menu action, list parked until `seedUi()` · rejected a contenteditable composer, IDE-side file reading.
- **2026-09-04 — 13.3 per-project persistence DEFERRED** · `update_settings` allows exactly `outputStyle`; the CLI honours `model`/`permissions.defaultMode` from `.claude/settings.local.json` at spawn, so revival is a precedence change in `ChatPanel` seeding · rejected plugin-side read-merge-write of that file (the CLI writes it too); watch-item: grep the binary for `update_settings keys not allowed` each re-audit.
- **2026-09-04 — Re-audit at 2.1.260** folded into the checklist/slash docs; the Marketplace vsix supplies BOTH the extension and the old CLI baseline (runbook step 3) · corrections: VS Code does NOT use `update_settings`; lesson: subtype acceptance ≠ key coverage.
- **2026-09-04 — CLI backward compat: no version floor, no shims; a muted "may be out of date" hint on early death** (`early:true` from `sawFrame`; fixture 73) · `manual` cutoff MEASURED at 2.1.200 both sides · rejected version probing, stderr matching, vocab translation; "Update Claude Code" button designed and deferred (backlog).
- **2026-09-05 — MCP notice split by severity** (needs-auth muted, failed red; fixture 72) · a fresh install read the combined red line as "the plugin is broken" · rejected one combined line, dropping the notice.
- **2026-09-05 → superseded 2026-09-04 — old-CLI rejection**: the self-healing retry/vocab-translation design stays rejected; became the 2.1.200 hint above.
- **2026-09-04 — `<synthetic>` frames drain by the RESULT's `is_error`** (fixtures 56 + 70) · built-ins and API echoes share the tag since ~2.1.251; only the result separates them · rejected classifying at stash time.
- **2026-09-04 — webview CSS split into 10 manifest files** (`CSS_FILES` = cascade order; mockup `<link>` tags pinned by `RenderLimitsTest`) · rejected regrouping by component (cascade risk), a generated chat.css for the mockup.
- **2026-09-01 (third) — files block: the `Review` span alone is the click target** · stray clicks opened the review · rejected rows opening their file.
- **2026-09-01 (second) — files-changed block: one row per file, project-relative, through the shared `fillPath()`; `#bgMenu` FIXED at 330px** · the comma-run blob (worst on Windows) · rejected a reserved ✕ gutter, a second path-shortener.
- **2026-09-01 — `(note: …)` caveat bounded**: `NOTE_MAX = 400` in `RenderLimits.kt`, a match at position 0 rejected; over the bound DROP, never truncate · large output with a literal `(note:` rendered as a giant amber note · rejected truncation, a single-line rule, capping the card `reason`; residual: a sub-400 trailing parenthetical still renders — the `strings` sweep each re-audit is the tripwire.

## Digest — decisions 2026-08-23 → 2026-08-30 (compressed 2026-09-13; full text via `git show 8831b12:.claude/context/decisions.md`)
- **2026-08-30 first-paint flash, final** — keep the JCEF child visible, `loadUi()` on the browser component's first non-empty resize, `setPageBackgroundColor("#1a1a1a")`; the 0.12.2 hide-until-load made the squashed frame deterministic (an invisible child has no bounds). Rejected: an opaque wrapper behind a hidden child. (The same-day "defer load + DumbAware" entry is SUPERSEDED in its hidden-child half; deferral and DumbAware stand — cost: the CLI starts on first show.)
- **2026-08-30 settings-schema staleness** — wait for SchemaStore, no code; rejected bundling the extension's schema (redistribution) and preferring the local VS Code copy (offered, user chose to wait).
- **2026-08-30 9.11** — optimistic `set_model` chip flip kept, revert on the error answer; rejected a pessimistic switch and a confirmation line of our own.
- **2026-08-30 re-audit at 2.1.251** on the user's ask one version after 2.1.250 — surfaced a real change (`set_model` rejection); lesson: a no-turn headless probe is not the panel's usual state, measure post-turn too.
- **2026-08-29 GitHub Copilot Chat** audited once, not a reference client; kept only "terminal last command/output as attachable context" as a probe idea; rejected keeping its extraction or naming it in procedures.
- **2026-08-29 `reference/<vendor-product>/`** for third-party material (was `vscode/`).
- **2026-08-29 external links** open in the system browser via three guards (JS click delegate → `BrowserUtil.browse`, `onBeforePopup`, `onBeforeBrowse` cancel); rejected in-webview navigation, a JCEF child window, `setOpenLinksInExternalBrowser` (not in 2024.2).
- **2026-08-29 effort selector** is a pill slider in the `.tgl` idiom, CSS only, 12px stops; rejected a bracketed label, a blue track, JS-computed fill.
- **2026-08-29 Marketplace change notes** carry the last THREE versions + a GitHub releases link (was 14).
- **2026-08-29 undo/branching belong to git** — 8.7 rewind NO, 14.2/14.4 host git NO, 14.1/14.3 worktrees later as one bundle; user's principle "de facto git, not Claude"; rejected "rewind → later".
- **2026-08-29 `/clear` removed** (7.6); the New button is the panel's /clear; `CMD_NATIVE = {btw}`; known loss: no keyboard-only new conversation.
- **2026-08-29 8.14 reloaded-webview replay: NO** (declined) — no real reload seen since `seedUi()`.
- **2026-08-29 destructive hover stays red** (roster ✕, history delete), every other hover control white — a deliberate pair, do not unify.
- **2026-08-29 8.11 side question** as a floating panel above the composer opened by `/btw` (panel-supplied `CMD_LOCAL` entry; client-threaded history; page-lifetime row ids); rejected a hidden turn and inline answers.
- **2026-08-29 §15 closed** — 15.5 debugger hand-off later ([LG] backlog), 15.6 MCP toggles NO (terminal's half).
- **2026-08-29 goal: every section ✅** by 2026-08-30 EOD; sections carry one mark; rejected a richer suffix ("let's not complicate it"). Closed: 1.25 later · 6.4/6.5 later · 6.7 no · 12.3 no · 12.6 later · 9.7 later + watch.
- **2026-08-29 1.22 tool_progress declined on measurement** — zero frames on a 12 s Bash under stream-json (2.1.251).
- **2026-08-29 13.2 settings schema** → SchemaStore URL for both `.claude/settings*.json`, nothing bundled; optional dep on `com.intellij.modules.json`.
- **2026-08-29 9.7 Fable overage gate** — watch first (`__modelFallbackSeen` + warning, fixture 65), build after a real frame.
- **2026-08-29 error results** — `errors[]` text before the subtype token (measured `--max-turns 1`).
- **2026-08-29 wrong-value negative controls** accepted for fixtures 62–64 (no pre-fix build available), provenance says so.
- **2026-08-29 checklist marks** — 🚫 retired; ➖ = not implemented, the row says why (terminal's half / declined / deferred); rejected a third mark.
- **2026-08-29 11.6 extensibility view declined; 11.5 elicitation** answered `{action:"decline"}` (bare `{}` was schema-invalid), form deferred behind a real eliciting server.
- **2026-08-29 roster ✕** — no confirm step, never removes a row itself (the `background_tasks_changed` frame does; `stop_task` succeeds for unknown ids).
- **2026-08-29 11.4 sub-agent outcome DECLINED on measurement** — task status is lifecycle, the summary prose the only signal; VS Code shows none either.
- **2026-08-29 `ambient` tasks filtered** on the schema's word, first live one stored as `window.__ambientSeen`.
- **2026-08-28 card note gap = `--attach-gap`** (8px), not a literal; the 2px left nudge stays by eye.
- **2026-08-28 tweak-travel sends a WHOLE-FILE edit** (`EditProposals.tweakedInput`, MultiEdit rides it); measured: VS Code's own shape and the CLI applies it; rejected a minimal-hunk diff.
- **2026-08-28 3.6 multi-file review** — data before UI: baselines from the autosave PreToolUse hook, per-TURN line, live-only; built the same day; rejected `ChangeListManager` (mixes user edits) and adopting sdkMcpServers for `file_updated`.
- **2026-08-28 closed audit docs are deleted**, their facts promoted to the reference tier, a `git show` pointer left.
- **2026-08-26 0.11.1 is a PATCH** with a "Changed" notes section and the Fable thinking caveat.
- **2026-08-26 effort lives in the MODEL menu**, level shown on NO chip (six suffix candidates rendered in the real bar, all rejected: "better is to hide effort"); `.ef-row` ≠ `.tgl-row` on purpose.
- **2026-08-26 Thinking switch stays live on Fable**, no-op documented, not gated (no discriminating roster field).
- **2026-08-26 roster capability flags do NOT gate** effort/Thinking — haiku (no flags) honours `/effort max` visibly; only `supportsFastMode` gates.
- **2026-08-25 chip/slider command turns show ONE confirmation line**, no bubble, live and resumed (`cleanInjected` drops `/model` + `/effort` wrappers; `effortMuted` draws the CLI's line). Supersedes 2026-07-30 and corrects 2026-08-24's premise.
- **2026-08-25 custom row's ×** overlays the ✓ inside `.pi-check`; ID-scoped padding reset; no offset arithmetic near `#inputbar`.
- **2026-08-24 1M / fast / thinking** as `.tgl` switches in the model-menu footer (9.9, 9.4, 9.5).
- **2026-08-24 the 1M switch has NO client-side validity logic** ("no hardcoded logic"); reconciles to `result.modelUsage[].contextWindow` after the first turn.
- **2026-08-24 Thinking ON = `max_thinking_tokens: null`**, not VS Code's 31999; OFF = 0.
- **2026-08-24 effort/model conversation markers dropped** (superseded in part 2026-08-25: /effort draws the CLI's line).
- **2026-08-24 API error draws ONCE**, dedupe by exact text never by kind (fixture 56's second control).
- **2026-08-24 the context skill stays lean** (rules only, shared via gist `b2d033439ba4ca5bcd018f4fe5eef773`, 96 lines) and **`/context load` reads a briefing TIER** — full-folder load measured ~41k tokens with ~29k discarded; rejected trimming reference files or summarising them into state.
- **2026-08-24 a replayed card may not claim a decision the transcript lacks** — third plan-card state `undecided` ("◌ Interrupted — no decision recorded"); `stopForReplay()` waits only while a permission is pending (a correctness wait scoped to its state).
- **2026-08-24 sandbox tracks a PATCHED IDE build** (2024.2.6; IJPL-161111 on .0); `sinceBuild 242` unchanged.
- **2026-08-24 `verifyPlugin` on every release**, docs say so once (0.9.0 skipped it and got lucky).
- **2026-08-24 confirm-card path stays one-line-ellipsised** — a wrap plan was rejected by the user; do not re-propose.
- **2026-08-23 plan comments live ON the plan card**, approval stays open with comments (user's call vs the reference's forced keep-planning); deny wears the VS Code client's byte-exact message; rows through one shared builder.

## Digest — decisions before 2026-08-22

- **2026-08-21** — displaced transcript records reorder by ANCHOR (`DisplacedAnchor`,
  SessionStore.kt: `apiErrAnchor`, `compactAnchor`), never by a global sort. Why: file order is
  the single-pass parser's backbone and the CLI breaks chronology only in measured places — each
  anchor is local and provable. Rejected: a global timestamp sort (records without timestamps;
  same-ts records whose only order IS file order; would need whole-file buffering). New anchors
  must each be earned by a real transcript.
- **2026-08-21** — "open this path" resolves against DISK (`findVFileOnDisk`: snapshot first,
  then refresh), wired into exactly the two `openFile` callers. Why: the VFS snapshot lags files
  written behind the IDE's back — clicks reported "File not found" on real files. Rejected:
  changing `findVFile` itself (sync refresh under `readLocked` deadlocks); an async callback
  (the caller's Boolean decides the balloon).
- **2026-08-19** — the webview JS lives in `webview/js/` (numbered files, load order = prefix),
  concatenated into ONE script scope at `<!--JS-->`; `WebviewAssets.JS_FILES` is the only copy of
  the order, pinned by `RenderLimitsTest`. Why: 4542 lines was unreviewable; the concat splice
  keeps semantics provably unchanged (assembled page byte-identical). Rejected: real ES modules
  (resource-serving layer + deferred-load timing for `window.onClaudeEvent`) — backlog "someday".

## Digest — decisions before 2026-08-18

- **2026-08-17** — a slash alias IS its command: `canonicalCmd()` resolves every roster alias for filter, row, gate and wire, and the turn is sent under the canonical name. Why: the CLI advertises `/review`, `/peers`, `/reset`, `/new`, so refusing one was the plugin contradicting the roster it displays; sending canonical removes any dependence on the CLI's alias expansion. Rejected: display-only aliases (the state that failed the first person to type `/review`); a hand-maintained allowlist (drifts every CLI update).
- **2026-08-17** — autosave rides the SDK hook lane, always on, four tools: ONE host hook on `initialize` (`PreToolUse Edit|Write|MultiEdit|Read → autosave`), answered after saving a dirty document, no toggle. Why: the reference's exact mechanism, and the only pre-tool moment that fires under acceptEdits/auto/saved rules AND for Read (no `can_use_tool` there); the plugin has no settings page by design. Rejected: saving from the permission card (misses pre-approved tools and Read); a Bash matcher (a command names no file); a toggle.
- **2026-08-17** — stale IDE locks are swept on every lock write, dead pid only: `IdeLockFile.write` deletes each `~/.claude/ide/*.lock` whose pid is not running (never its own, never an unreadable one). Why: `delete()` only runs on an orderly dispose, so killed sandboxes left 15 corpses, and the CLI applies the same rule only while enumerating IDEs — which our `--mcp-config` route never triggers. Rejected: matching `ideName`; a port probe; deleting unreadable locks (the CLI's lane).
- **2026-08-17** — the feature checklist is a numbered, colour-tiered register with STABLE `section.row` ids (retire by striking, never renumber), marks ✅/🟥/🟧/⬜/➖/🚫, `[XS|SM|MD|LG]` effort on open rows, meta in the header, measured against BOTH reference clients on one version. Why: the user refers to rows by id and wants importance at a glance with cost before choosing; the old file duplicated state and buried the key. Rejected: a separate open-decisions list (folded into **[DECIDE]**); a priority table (prose rows wrap badly); 🟨 for low. (Marks later simplified to ONE mark ➖ on 2026-08-29; 🚫 retired.)
- **2026-08-17** — `close_tab` closes the ONE review opened under that `tab_name` and replies `TAB_CLOSED` regardless; `closeAllDiffTabs` replies `CLOSED_<n>_DIFF_TABS`. Why: our sweep closed every diff, so with two proposals open closing one resolved both; reference-exact replies keep the CLI's consumer on its written path. Rejected: erroring on an unknown name (reference treats a miss as a benign no-op).
- **2026-08-16** — a menu click asks "does it TAKE an argument", not "does it REQUIRE one": any non-empty `argumentHint` inserts `/name ` and waits. Why: `[init | load | save]` is a menu of sub-modes — a command advertising choices is one you clicked in order to choose (the user picked `/context` to run `/context save` and it fired bare); only `/compact`, `/context`, `/goal` of 16 built-ins change behaviour. Rejected: respelling the skill's hint (makes the roster lie); a click/Shift-click split (a new idiom).
- **2026-08-16** — approve-with-notes rides `updatedInput.plan` under `PLAN_NOTES_MARKER`, not a steered message. Why: the ExitPlanMode tool_result echoes the approved plan, so the model reads the note in the SAME message as the approval, before its first implementation call; also recorded durably in the plan file. Rejected: a `feedback` field on the allow response (schema-whitelisted away, silently); stdin steering (raced the model call cycle in a real run); a queued turn (arrives after the whole turn).
- **2026-08-16** — plan-card mode rows PARK the switch in `pendingPlanMode` until the CLI's post-approval `permissionMode` broadcast. Why: the CLI restores `prePlanMode` when the approved ExitPlanMode executes, always after our immediate request, so an eager switch was overwritten every time. Rejected: immediate bridge (deterministic loss); sending at turn end (too late to cover the implementation).
- **2026-08-16** — the panel's two type-on-a-card fields share one dress (`--warn-field`), and the plan card gained a full-bleed `.plan-sep` hairline removed with the input on decide. Why: one control idiom (the `fillPath` argument applied to fields); colour user-picked over three mockup rounds. Rejected en route: `#1b1b1b` on `--panel`; a fully transparent field.
- **2026-08-16** — the chip aliases the broadcast `default` → Manual before the unknown-mode guard, and `attachment` (`queued_command`) records replay as user bubbles unless a plain user record carries the same text. Why: restoring a manual pre-plan mode broadcasts the CLI's internal `default`, which the guard dropped, stranding the chip on Plan; and measured across transcripts, a MID-TURN steered message persists only as the attachment record (queued-to-next-turn persists as both) — so replay otherwise lost text the model demonstrably acted on, or drew it twice.
- **2026-08-15** — ONE path renderer for every surface: both permission-card headers fill their `<code>` through `fillPath()`, absolute path on `dataset.path` + `title`. Why: the decision surface and the timeline named the same file two different ways, and a second renderer is a second thing to drift. Supersedes the backlog note that card paths were deliberately unclamped. Rejected: a card-only shortener; CSS-only clamping (flex shrinks proportionally, nibbling the parent on short paths).
- **2026-08-15** — `msgStreamed` is a TURN-level fact: set at `message_start`, cleared only at result/sendTurn/clearLogUI. Why: the CLI emits an `assistant` frame per CONTENT BLOCK, so clearing mid-turn let the second re-draw text the deltas had already rendered — every message after the first appeared twice in a `/security-review` run. Rejected: clearing at `message_stop` (tried, reverted the same hour — frames straddle the stop in both directions); a rendered-uuid set (the duplicate carries the drawing frame's uuid).
- **2026-08-15** — a failed local command is surfaced, not swallowed: `onUserEvent` handles STRING `content`, routing `<local-command-stderr>` → error block and `<local-command-stdout>` → answer block, live and on replay. Why: `/security-review` without `origin/HEAD` reports only through such a frame, which the `!Array.isArray(content)` guard dropped — a completed turn with nothing in it. Rejected: extending `cleanInjected`'s drop list (wrong in both directions).
- **2026-08-15** — custom commands are detected by the description SUFFIX (`… (project|user)`), not a disk scan. Why: the entry schema has no type field, but the suffix is a measured wire marker (present on every custom entry, absent from all 107 built-ins across two captures); webview-only, and covers plugin-sourced commands free. Rejected: the PLAN-APPROVED Kotlin disk scan (more moving parts, a rescan per `commands_changed`, blind to unscanned sources) — user approved the pivot mid-plan. Accepted risk: a future respelling hides custom commands (fail-closed).
- **2026-08-14** — the IDE is kept in step with the CLI's writes twice over: `CliFileSync` refreshes the one path when a write tool's `tool_use`/`tool_result` pair completes, then sweeps the project root at every `result` (async refresh; a newly created file walks up to the nearest VFS-known directory, recursively). Why measured, not designed: a real turn answered "create one file and overwrite another" with a SINGLE `Bash` call, so the per-file half the backlog scoped caught nothing — Bash names no file, and the sweep costs about what IntelliJ pays on window focus. Rejected: deriving paths from the Bash command (guesswork); sweeping per Bash call; refreshing at `tool_use` (not written yet); keying off the permission card (pre-approved tools produce none).

Outcome · why · key rejection. Full prose is in git history; these are the parts that still steer work.

**2026-08-13 — Sub-agent and in-flight state.** A running tool's dot is white and breathing and the
colour IS the verdict (`--dot-c` per element, one geometry rule for all four `::before` dots) — green
from the first frame asserted success before anything returned. A sub-agent settles when its TASK ends,
not when its launch ack arrives (`isInternalResult` recognises the ack), and goes red only on a
`failed`/`killed` TASK, never on failed WORK — the work outcome is unknowable across sandbox guards, so
a sub-task status dot was built and removed the same day. *Rejected:* keying on tool NAME (drifts);
colouring from summary prose (a text heuristic over an open-ended sentence).
**2026-08-13 — Three things built and withdrawn**, kept because each looks obviously right until
measured: an outward halo (`.turn-body` containment shaved it into a cut half-rectangle), lifting
`content-visibility` to save it (costs the live turn a real rendering property for decoration), and the
sub-task outcome dot above. Lessons live in gotchas.
**2026-08-13 — One gap for every block that hangs off the line above it.** `--block-gap: 18px` /
`--attach-gap: 8px`; followers use `calc(attach - block)` so the intent is legible and the family cannot
drift again (five had drifted to −6/−2/+2/+2/+2px, putting attached content FARTHER from its tool line
than an unrelated block). 8px user-picked from a probe because `.card-h` already used it. *Rejected:*
putting `.compact-sum` in the shared selector (its parent is an ordinary block — no flex gap to cancel).
**2026-08-13 — An unnamed thread re-reads its name once per turn, at `message_start`.** `pushTitle` had
one live-turn caller (`result`), so a new conversation showed "New conversation" beside a titled row for
its whole first turn — an hour in a real transcript. `message_start` is the earliest frame that can work
(the file does not exist at `system/init`). *Rejected:* an `onSessionId` callback (fires before the file
exists); deriving a provisional title in JS (a second derivation of a one-source value).
**2026-08-13 — The webview is seeded on EVERY load** (`seedUi()` at each `onLoadEnd`, `lastTitle` nulled
first) — everything used to sit behind a `started` guard, so a reload left the DOM at markup defaults
with a live CLI attached. *Rejected:* replaying the conversation into a reloaded page (needs the
transcript pushed without restarting the CLI, reconciled against frames still arriving; no reload has
ever been observed in the wild — parked as 8.14).
**2026-08-12 — One contract for every foldable block:** `8px 10px` padding, and whichever element
carries it ALSO scrolls; foldable AND scrollable ⇒ the scrollbar appears only when expanded (rule 2
falls out of rule 1). *Exception, deliberate:* `.io-row`'s left padding lives on its sticky `.io-k`,
since sticky clamps to the containing block. *Rejected:* `:has()` to reach the row (untestable outside
real JCEF).
**2026-08-12 — Busy is a fact about the STREAM, not about who sent the turn.** `message_start` sets busy
and resets the request-scoped counters, because `setBusy(true)` had one call site inside `sendTurn` — so
a turn the CLI started on its own streamed with the button on Send and Stop was unreachable. Measured: a
notification turn arrives with NO user frame on the wire. `pendingBgTasks` excludes `local_bash`, since
the CLI's own busy set does. *Rejected:* an allow-list filter (an unknown `task_type` must count as
suspending); hooking `system/status` (fires ~3x a turn).
**2026-08-12 — The replay window keeps the NEWEST blocks and says so at its top edge.** The old cap kept
the OLDEST, so a session resumed on the 12th replayed as though it had ended on the 6th. Cap 20,000,
eviction from the front at a `user` boundary, and a `truncated` head block carrying the COUNT. *Rejected:*
a bidirectional paging window (a mid-file range cannot be parsed in isolation — results patch earlier
blocks by id, tasks rebuild from increments, summaries link by parentUuid). Paired rule: a cap that trims
history must not do it silently at EITHER end (`#fade-top` off at `scrollTop <= 1`).
**2026-08-12 — A value that IS the work goes in the IN box, not the description.** `"function"` moved
from `DESC_KEYS` to `IN_KEYS` — measured at 230-2965 chars of multi-line JS, so `DESC_MAX` produced a
mid-token slice. The tooltip carries the post-`DESC_MAX` string deliberately: it reveals what the CLAMP
hid, never what the CAP dropped.
**2026-08-12 — Tool-line paths: project-relative, one line, a CHARACTER budget for the tail.** A budget
needs no layout measurement (the alternative forces a synchronous layout per tool line during a replay
that renders hundreds), and CSS alone cannot express "shrink the parent only once the prefix is gone".
*Rejected:* filename-only tail (loses the disambiguating folder); `direction: rtl` (`unicode-bidi`
cancels it).
**2026-08-12 — The rename editor dismisses on CAPTURE**, the one place the popup idiom does not fit:
every control beside the title `stopPropagation`s, so a bubbling listener never sees those clicks.
Discard rather than commit, since a stray click would append a `custom-title` record with no undo.
*Rejected:* a `blur` dismissal (there is zero blur handling in the webview).
**2026-08-12 — A conversation's title is derived from the WHOLE transcript**, since `custom-title` and
`ai-title` are appended where they happen — a rename on a 10,458-line file sat on the last four lines.
*Rejected:* a tail window (moves the blind spot); caching the name ourselves (a second source of truth).
**2026-08-12 — Rename writes the CLI's own `custom-title` record**, read verbatim from the binary, so a
rename here and a `/rename` in the terminal are one act. Safe on the LIVE session because the CLI opens
O_APPEND per write — unlike delete. *Rejected:* the `rename_session` control subtype (unreachable over
stream-json).
**2026-08-12 — Release notes are enforced by the build, not the checklist** (`buildPlugin` fails without
a `<b>X.Y.Z</b>` entry) and **the Marketplace upload is automated, but only the UPLOAD** — the workflow
re-posts the PUBLISHED asset so the zip users get is the zip that was smoke-tested. *Rejected:* a
configuration-time check (would fail `runIde` mid-feature); building the zip in CI (publishes an
artifact nobody ran).
**2026-08-12 — The context gauge is an SVG arc** with `pathLength="100"` and `currentColor`, drawn on
the composer's Lucide geometry so it measures identically to the glyph beside it. *Rejected:* a
conic-gradient pie (Chromium anti-aliases neither the sweep edge nor the mask).
**2026-08-09 — Deleting the LIVE conversation = leave it first, then delete** (restart on a fresh
conversation, bounded `awaitExit`, then remove the file) — the CLI reopens the transcript per write, so
a live delete truncates rather than removes, and a dying CLI can still flush a resurrecting write.
**2026-08-09 — Edit permissions are dual-surface, first answer wins; editor close is NOT a grant.** The
card and a real editor diff both open; `respondPermission`'s pending map is the arbiter. TAB_CLOSED
deliberately diverges from the bridge flow's accept-as-proposed: a permission must never be granted by a
window being tidied away. Panes are read-only because accept answers with the ORIGINAL input.
**2026-08-09 — openDiff never writes; every review's diff tab closes by a HELD handle.** The CALLER does
the disk write (measured on both halves of the reference), and `FileEditorManager.openFiles` does not
report diff editors, so any find-then-close closes nothing. Verdict UI is a bar UNDER the diff,
card-identical, combined grant only — partial grants stay on the card. *Rejected:* toolbar icons
(unidentifiable), text buttons (no warning-free API 242→262), a top banner.
**2026-08-09 — The model-facing IDE-tool allowlist is upstream policy** (getDiagnostics + executeCode),
byte-identical across three versions. *Rejected:* renaming the bridge server to dodge the prefix filter
— the CLI finds its IDE client BY the literal name `"ide"`.
**2026-08-09 — Wording lives once, codes travel.** Retry reasons and auth errors are chat.html tables;
Kotlin emits the raw CODE and only membership sets live in RenderLimits. An unknown code degrades to
showing itself. *Rejected:* duplicating wording into Kotlin.
**2026-08-09 — Model-facing tool results are suppressed by CONTENT, not tool name** — the same tool's
launch ack is bookkeeping while its completed result is the report, so name-keying cannot express the
split. The `[harness: …]` envelope is stripped at position 0 only, since the CLI escapes line-initial
forgeries.
**2026-08-09 — Live edit diffs render OPTIMISTICALLY from tool input, superseded by the card**, because
the live wire carries no diff data and pre-approved paths never send `can_use_tool`. Cost accepted: the
gutter line must be fetched PRE-apply and degrades to no line numbers.
**2026-08-09 — All keyboard chords removed; the plugin binds NO shortcuts.** All three were dead on this
setup (Ctrl+N swallowed by the IDE, the others never fired inside JCEF), and a shortcut that silently
does nothing is worse than none. Every capability keeps a route. *Rejected:* re-registering as IDE
actions (trades a dead chord for keymap collisions to hunt on every IDE and OS).
**2026-08-08 — Manual-test pass conventions:** tick when the behaviour was OBSERVED, with defects
recorded inline; unforceable events may be verified by CDP fixture injection or direct MCP-over-WS, with
provenance stated in the tick. Accepted, not bugs: `/model <x>` audit records becoming untitled-session
titles; replayed image chips reading "file.jpg <smaller>".
**2026-08-07 — Light theme declined; colour groundwork removed** (the docs duplicated what chat.css
answers, and groundwork for work that won't happen is reload budget spent on nothing).
**2026-08-07 — Project context lives in `.claude/context/`; CLAUDE.md abolished** — one portable
structured location instead of a monolith competing for the whole reload budget. *Rejected:* keeping
CLAUDE.md alongside (two sources that drift).
**2026-08-06 — Declined, not deferred:** cost display, token/usage panel, per-record metadata. A
decision, not a queue position — it keeps the panel on the many-times-an-hour loop.
**2026-08-03 — The mode switcher sends the CLI's own four modes** (`manual`/`acceptEdits`/`plan`/`auto`)
and the bypass machinery is gone: `auto` is safety-checked, while the old "Auto" was
`bypassPermissions` and needed a CLI relaunch.
**2026-07-31 — Renamed to "Claude Brains"; distribution is Path B** (custom repo), *partially superseded
the same day* — the plugin was also submitted to the Marketplace, so both channels ship the same id and
IDEs take the higher version.
**2026-07-30 — The slash menu is an allowlist**, since over stream-json only turn-producing commands
work; `/model` and `/effort` stay hidden because the composer has the chip and the slider. Effort rides
a muted `/effort` turn, and its reappearance on resume is ACCEPTED as an honest audit trail.
**2026-07-30 — Per-turn file rewind removed** — needs a checkpointing env var that bloats every
transcript, plus a git repo and client-supplied uuids. Revival notes in gotchas.
**Founding — "Develop in the IDE. Configure in the Terminal."** The scope rule. A settings page and
non-terminal login are the terminal's half and will NOT be built — "By design" absences, never gaps.
Rebuilding `~/.claude` config in the IDE is a second implementation that can only drift.
