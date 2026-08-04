# Renderer parity audit — live vs resume vs mockup

Three audits of the chat UI's three render paths:
- **live** — `chat.html` streaming renderer (`onStream`/`onResult`/`renderPermission`/`renderAsk`)
- **resume** — `chat.html` replay (`renderBlocks`/`replayCard`/`replayAsk`) fed by `SessionStore.kt`
- **mockup** — `design/mockup.html`, the static design fixture that mirrors the generated DOM

This is the INTERNAL audit — our own paths against each other. For what the official clients show
and we don't, see `docs/client-parity.md`.

First run: 2026-07-29. Legend: `[x]` done · `[ ]` open · `[~]` deliberate / documented, no action intended.

When an item is fixed, tick it and add a one-line note (what + where). Keep the finding text so the
history is legible.

---

## Audit 1 — Formatting: resume vs live

Same-content blocks that render differently between the streaming path and the replay path.

- [x] **1. Live answered/cancelled ask card never got `.ask-done`.** Replay builds `el('ask ask-done')`;
      the live `go.onclick`/`cancelAsk` disabled inputs and swapped the badge but never added the class,
      so a just-answered live card kept pointer cursor + option hover (`chat.css:278–280`) while its
      replayed twin went inert.
      **Fixed 2026-07-29** — `card.classList.add('ask-done')` in both `renderAsk` submit and `cancelAsk`
      (`chat.html`). Verified in Chromium: answered → `.ask-done` + "✓ Answered"; cancel → `.ask-done` +
      "✗ Cancelled".
- [x] **2. Live diffs had no row cap; replay capped at `MAX_DIFF_ROWS` (400).** A whole-file Write/Edit
      rendered every row live (the perf-dangerous case) but 400 + "… diff truncated" on resume.
      **Fixed 2026-07-29** — `renderEditDiff()` counts rows and stops at 400 + marker (also closes the
      uncapped `replayDiff` old/new fallback, same function); `previewHtml()` Write path mirrors
      `replayDiff`'s content cap. Verified in Chromium (1000-line Edit + Write → 400 rows + marker;
      small diffs untouched; boundary correct). `docs/limits.md:41` already claimed "live + replay" — now true.
- [x] **3. Zero-token completion summaries.** Kotlin skipped the whole `✻ … for Ns · ↓ N tokens`
      line when `reqTokens == 0`; live `onResult` always drew it, incl. a noisy "↓ 0 tokens". But the
      **time is always real** even when tokens are 0 — only the token segment is meaningless.
      **Fixed 2026-07-29** — both paths now share a `doneHtml(durMs, tokens)` helper: the time always
      shows, the "↓ N tokens" segment is appended only when tokens > 0. Kotlin `flushSummary` dropped
      the `reqTokens > 0` guard (keeps `reqWork` — a truly empty turn still emits nothing). So a
      0-token request reads `✻ Baked for 2s` on both paths. Verified: JS `doneHtml`/replay in Chromium;
      Kotlin unit test `a zero-token request still summarises its elapsed time` (synthetic transcript,
      since the shared fixture ends on an interrupt); plus `interrupt … no summary` now asserts the
      interrupt-suppression invariant.
- [x] **4. Thinking-line wording.** Live always renders "Thought for Ns" (min 1s) and draws a
      `think no-body` line for an empty stream; replay rendered bare "Thought" for sub-second /
      timestamp-less durations and **dropped empty-body thinking entirely** (~2/3 of stored blocks
      persist only a signature), so thinking visible live vanished on resume.
      **Fixed 2026-07-29** (user chose: match live). Replay `case 'thinking'` now mirrors
      `finishThinking` exactly — clamps to a 1s floor when a duration exists (bare "Thought" only when
      there is no timestamp gap), and branches on empty body → non-expandable `think no-body` vs
      collapsible `<details>`. Kotlin `readTranscript` now emits thinking items even when the body is
      blank (was `takeIf { isNotBlank() }`). Verified: four render cases in Chromium (bodied /
      empty+duration / sub-second→1s / no-gap→bare) all match live; Kotlin test
      `empty-body thinking is still emitted…` (synthetic signature-only block); existing thinking
      test now selects the bodied block.
      · **Follow-on for Audit 3:** the mockup's replayed section could gain a no-body thinking example
        (its only `no-body` sample is in the live turn) — tracked under Audit 3 "in plugin not mockup".
- [x] **5. Bash OUT provenance.** Live shows the `tool_result` block **content** (model-facing text);
      replay read the structured `stdout`+`stderr` fields FIRST (content only as fallback), so a
      resumed OUT box could differ in text / stderr ordering / truncation boundary.
      **Fixed 2026-07-29** — `applyToolResult` now prefers `resultText(block["content"])` (identical
      source + join semantics to live's `onUserEvent`), with `stdout`/`stderr` as the fallback only
      when the content block is empty. Kotlin test `Bash OUT prefers the tool_result content over raw
      stdout and stderr` (content + differing stdout/stderr → OUT is the content). Note: live still
      emits one OUT row *per* `tool_result` and caps each at 2000, while replay keeps a single OUT
      capped at 2000 — the multi-result row-count edge is left as-is (rare for Bash; overwrites to the
      last result on replay).
- [x] **6. Replayed image chips are generic.** `imagesOf()` hardcoded `name: "image.png"` and carried
      no w/h, so replayed user-box chips showed a wrong-format name and lost the `260×130` meta.
      **Fixed 2026-07-29** (partial — the filename is inherently unrecoverable, see below):
      · **Name** (`SessionStore.kt`) — `imageName(mediaType)` derives the extension from the transcript's
        `media_type` → `file.jpg` / `file.png` / `file.webp` / `file.svg` (from svg+xml) / …, defaulting
        to `file.png` for an odd/absent type. Base stays generic ("file") because the real filename is
        never sent to the CLI (`sendTurn` ships only `{media_type, data}`), so it isn't in the transcript.
        Test `replayed image name takes its extension from the media_type` (jpeg/png/webp/svg+xml/bogus).
      · **Size** (`chat.html`) — the chip meta shows the file **size** via the shared `fmtSize()`
        (B / KB / MB, binary thresholds: B under 1 KB so a tiny file reads "17 B" not "0 KB"; MB at
        1024 KB), computed from the base64 byte count, for every attachment with data (image + pdf +
        text). Superseded the first attempt (an image-only `w×h` dimensions probe) per the user's
        preference — size is uniform across all file types and needs no async probe. Verified in Chromium.
      · **Not fixed (can't be):** the real original *image* filename — never transmitted, so `file.<ext>`
        is as honest as the transcript allows. (PDF/text keep their real name via the `document` title.)
- [x] **7. Random completion verb.** Both paths picked a random `DONE_VERBS` entry, so a resumed
      session said "Ruminated" one time and "Distilled" the next — the *unstable* half of this is a
      bug, and it is fixed: `flushSummary` ships the request's start epoch-ms as `seed` (unique per
      request, frozen in the JSONL) and `doneVerb()` hashes it (djb2 — `seed % len` clusters, since
      consecutive requests share their high digits). A reopened session now renders the exact same
      words every time. Measured over 14.6k real request timestamps from 40 local sessions: all 22
      verbs used, per-verb counts 620–725 against 663 expected, 20 distinct first-verbs across the 40
      sessions — deterministic, still scattered.
      · **Adjacent repeats** (found in the sandbox 2026-07-30, resume then send: `Finagled for 9s`
        replayed, `Finagled for 2s` live). Not a seeding leak — live is still `Math.random()` — just
        the 1-in-22 collision two independent picks are entitled to. It reads as a bug regardless, so
        it is now structurally impossible: `lastDoneVerb` holds the verb above and `doneVerb()` bumps
        by one on a match (bump, not re-roll, so replay stays deterministic). Windowed replay renders
        earlier chunks LAST, so document order isn't render order — hence `prevSeed` on the item for a
        chunk's first summary, and `renderEarlier` nulls `lastDoneVerb` for the chunk then restores the
        tail's. `clearLogUI` resets it so the previous session can't constrain the next one's first.
        Re-measured: 0 adjacent repeats in 14.6k summaries, 0 across 23 chunk boundaries, 0 over 200
        resume-then-5-live-turns runs, and byte-identical verbs across repeat renders.
      · **Live↔replay match — done 2026-07-30, after two rounds of getting this wrong.** Twice
        recorded here as unreachable ("the live verb is never persisted"). It is reachable: the CLI
        puts a `uuid` on its live `assistant` event and writes *that same uuid* into the transcript
        record — verified byte-identical (`4e42341a-…`, timestamps too) by running the CLI and diffing
        the stream against the JSONL it wrote. So both paths now hash the request's FIRST assistant
        uuid: `case 'assistant'` in `onClaudeEvent` captures `reqSeed` (reset in `sendTurn` +
        `clearLogUI`), `flushSummary` reads the same field, epoch-ms stays as the fallback. Verified
        end-to-end against a live CLI run: same seed, same verb, same token count as the transcript,
        then **confirmed in the sandbox** — a turn rendered live keeps its verb across a resume. (Turns
        written BEFORE this change can't: their live verb was random and never recorded, so they settle
        on whatever their uuid hashes to.)
        **Lesson: "the CLI can't tell us that" is a claim to test against the CLI, not to assert.**
      · Residual imprecision: at a chunk boundary the collision bump compares against the raw hash of
        `prevSeed` rather than the verb actually displayed, so a summary whose predecessor was itself
        bumped can differ there — ~1/484, boundary-only.
      · Rejected: one hardcoded verb. A long session shows one summary per request, so twenty
        identical lines stop reading as separate events.
- [x] **11. Replayed summaries over-counted tokens ~2x** (found 2026-07-30 from a sandbox screenshot:
      the same turn read `↓ 53 tokens` live and `↓ 106 tokens` resumed). The CLI persists one API
      message as one record PER CONTENT BLOCK — `['thinking']`, `['tool_use']`, `['text']` — and every
      one of those records repeats the same *cumulative* `message.usage`. Summing records therefore
      multiplied the count by the block count. Both sum sites now count each `message.id` once:
      `readTranscript` (the per-request summary) and `tokensOf` (the history panel's per-session
      total, which was equally wrong and nobody had checked). Measured across 42 local sessions:
      9.28M → 3.78M output tokens, i.e. **2.45x over-report overall, worst session 3.63x**; the
      screenshot's session drops 2.06–2.08x per request. Safe because the split records never
      disagree: 2546 split messages checked, 0 with differing `output_tokens`, so taking the first is
      the whole truth. Live was always right (`message_delta.usage` fires once per message), which is
      why only the resumed number looked odd. **Confirmed in the sandbox**: the turn from the original
      screenshot now replays as `↓ 53 tokens`, matching what it showed live.
- [x] **8. Focus ring "cut" live, "full" resumed** (discovered 2026-07-29 while reviewing #4). The
      thinking-summary (and any focusable element in a turn) showed the browser's default *outward*
      `outline: auto`, with no CSS focus rule at all. The `.turn-body`'s `content-visibility: auto`
      applies paint containment CONTINUOUSLY — on-screen too, not just at the viewport edge (proven in
      Chromium: an identical flush child clips in a `content-visibility:auto` box but not a plain one).
      An outward ring sticks out ~4px, so it is shaved where the element reaches the turn-body's
      paint-clip edge — and the thinking line is the FIRST child of its turn-body (flush to the top)
      on both paths. **Root cause of the live/resume asymmetry (pinned down 2026-07-29):**
      `renderTranscript` — resume only — un-skips the last 8 turn-bodies with an inline
      `contentVisibility = 'visible'` (chat.html ~794, done so the bottom-landing math gets honest
      heights). That override also removes the paint containment, so the resumed tail can't clip the
      ring; live turns from `newTurn()` keep the stylesheet's `content-visibility: auto` and clip it.
      Reproduced side-by-side in the mockup: identical turn, cv:auto → ring-top cut, inline
      cv:visible → full ring — matching the user's screenshots exactly. So live = always clipped,
      resumed tail (last 8) = never clipped; a resumed turn OLDER than the tail would clip like live.
      The inset ring is immune either way, so the fix holds regardless of which state a turn is in.
      **Fixed 2026-07-29** — shared inset focus rule scoped to a turn (`chat.css`): `.turn :focus {
      outline:none }` + `.turn :focus-visible { outline: 2px solid var(--accent); outline-offset:
      -2px }`, plus `.ask-opt:has(input:focus-visible)` to surface the transparent radio/checkbox's
      focus on the visible row. An inset ring can't exceed the element box, so no containment / sticky
      box / clamped overflow can clip it — full ring on summary, ask tabs, card buttons, links,
      inputs, live and resumed alike. Verified in the mockup (all focusables resolve to offset -2px
      accent) incl. a forced `contain:paint` turn-body → full ring. **Verified in a real `runIde`
      sandbox 2026-07-30** (user) — the inset approach holds in JCEF as designed.

---

## Audit 2 — Live vs resumed: behavioral / content differences

Mostly deliberate; listed so nothing is silently forgotten.

### Deliberate (working as designed)
- [~] Card wording changes: "Claude wants to run **Edit** on…" + ✓ Accepted → "**Edit** on…" + ✓ Applied
      (transcript can't tell manual approval from auto-mode); ✗ Rejected / ✗ Kept planning / ✗ Cancelled
      from `toolDenialKind`.
- [~] Non-diff permission cards vanish on resume: `replayCard` only renders with a plan or diff body,
      so a resolved **Bash permission card** leaves only the tool line + IN/OUT.
- [~] ⏹ Stopped reconstructed from `interruptedByShutdown`; "Resumed" marker appended; `cleanInjected()`
      strips CLI bookkeeping (matches live, which never renders those frames).
- [~] Windowing: newest ~250 blocks at a turn boundary; earlier chunks stream on scroll; 4 MB image
      budget → name-only chips.

### Lossy — gone on resume, worth knowing
- [~] ~~Undo buttons and "↩ Reverted file changes" status lines~~ — MOOT 2026-07-30: the revert
      feature was removed outright (UI, control protocol, `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING`,
      and the per-turn `uuid`). Nothing to lose on resume any more.
- [x] API-error records replayed as plain assistant text — no `.error` block, no Retry line.
      **Fixed 2026-07-30** — a record flagged `isApiErrorMessage` now emits role `error` instead of
      `assistant` (`SessionStore.readTranscript`), rendered by a `case 'error'` in `renderBlocks`
      that builds the same `SVG_ALERT + <div>` markup as live's `onResult`. Verified: the replayed
      block is byte-identical to the live one in Chromium (same colour/border/bg, 38px both); Kotlin
      test `an API error replays as an error block, not assistant prose` — which also asserts the
      ordinary reply in the same transcript STAYS an assistant block — mutation-checked; and run
      over the real transcripts (2 local sessions carry the flag, both reconstruct).
      · **No Retry line, deliberately** — `addRetryLine()` needs `lastUser`, which a replay never
        seeds, so it no-ops. That is the next item; until it lands a resumed failed turn genuinely
        cannot be retried.
- [x] Retry lines: `lastUser` wasn't seeded from a transcript, so a resumed failed last turn couldn't
      be retried. **Fixed 2026-07-30** — `renderBlocks` seeds `lastUser` from every replayed user
      block, and a transcript that ENDS on an `error` block gets `addRetryLine()`. Only the last
      block qualifies: an error mid-transcript was already recovered from, so its Retry would resend
      a later message. Attachments trimmed past `IMAGE_BUDGET` carry no bytes and are dropped by
      ChatPanel on resend, so a retry after a trim sends the text plus whatever survived.
- [~] Thinking durations are record-gap approximations; live times the stream.
- [x] `IMAGE_BUDGET` spent in file order (early unshipped images starved the visible tail).
      **Fixed 2026-07-30** — attachments are attached whole during the parse and trimmed afterwards
      by `trimAttachments()`, walking NEWEST-first; over-budget ones degrade to name-only chips as
      before. Test `the image budget is spent newest-first…` (two 3 MB images, 4 MB budget → newest
      keeps bytes, oldest degrades but keeps kind+name), mutation-checked. Note the parse now holds
      all attachment bytes transiently before trimming; real sessions carry ~2.4 MB of images at
      most, so the spike is bounded in practice.
- [~] DOM/browser find only sees loaded blocks — inherent to windowed replay, documented in
      `docs/limits.md`. No action: the alternative is shipping the whole transcript again.
- [~] **Silent `/effort` turns reappear on resume** (found 2026-07-30 auditing the office batch,
      `1e3a31b`). Live swallows the whole turn — `effortMuted` drops its stream/echo/summary in
      `onClaudeEvent` — but the CLI still writes the turn to the transcript, and NOTHING on the
      replay path filters it: `cleanInjected()` collapses `<command-name>/effort…` to a visible
      `/effort <level>` user box, so a resumed session shows turns that live deliberately hid.
      Inverse of the usual lossy-on-resume shape. **Accepted 2026-07-30** — user verified in the
      `runIde` sandbox and chose to keep the visible `/effort` box on resume as an honest audit
      trail; no replay filter will be added.
- [ ] **CLI spill metadata is replay-only** (probed in `runIde` 2026-08-05, `seq 1 200000`). When a
      Bash result is too big the CLI writes it to a file and the transcript record carries
      `toolUseResult.persistedOutputPath` / `persistedOutputSize` — so REPLAY's truncation marker
      reads `1.2 MB total, not shown here — open full output`. The LIVE stream event carries no
      `toolUseResult` at all, so live falls back to our-cut-only (`⋯ +58 lines · 251 B not shown`).
      Same record, two different markers.
      · The information IS present live, but as PROSE inside the `tool_result` content: the CLI
        substitutes a `<persisted-output>\nOutput too large (402.7KB). Full output saved to: <path>
        \n\nPreview (first 2KB):\n…` wrapper (2253 chars in the sampled record, hence our 2000-char
        cut nibbling its tail). Parsing that wrapper live would close the divergence AND stop the
        raw `<persisted-output>` tag rendering to the user — it is an injected wrapper of the same
        family `cleanInjected()` already strips. Not yet done; see docs/client-parity.md item 10.
- [x] Sidechain/subagent record ordering untested (no local fixtures). **Tested 2026-07-30** —
      neither path branches on `isSidechain`, so subagent records replay in FILE ORDER, interleaved
      with the parent turn; the parent's Task result still attaches to the Task tool line, not to a
      subagent tool. Pinned by `sidechain records replay in file order, interleaved with the parent
      turn` (synthetic fixture — still no real session has any).
      · **Finding, left deliberate:** a sidechain `user` record is the SUBAGENT'S PROMPT, yet it
        replays as an ordinary blue user block, so a resumed session appears to contain a message
        the human never typed. Live behaves the same way (it renders stream events in arrival
        order), so this is consistent rather than a resume bug — revisit if real subagent sessions
        show it is confusing in practice.

---

## Audit 3 — Mockup vs plugin coverage

### In the mockup but NOT in the plugin (fixture ahead of / diverged from reality)
- [x] **1. Attach menu had a third item, "Browse the web"** — removed from the mockup 2026-07-29 (user
      didn't want it); the mockup now matches the plugin's two items (*Upload from computer* + *Add context*).
- [x] **2. Working line shows "· thought for 4s"**; `updateWorkTokens()` never emits that segment.
      **Fixed 2026-07-30** — segment dropped; mockup meta now reads `(6s · ↓ 88 tokens)`.
- [x] **3. Auto-mode description differs.** **Fixed 2026-07-30** — mockup adopts the plugin's
      "Claude will approve actions automatically — no prompts".
- [x] **4. Composer placeholder differs.** Mockup promised an unimplemented Ctrl+Esc shortcut.
      **Fixed 2026-07-30** — now the plugin's "Ask Claude…  (Ctrl+Enter to send, Enter for newline)".
- [x] **5. Model chip label.** **Fixed 2026-07-30** — mockup shows "Default (Opus 4.8)", matching
      `chipName()`'s Default-resolves-to-a-real-model form.
- [~] Devbar, resizable frame/grip, static menus, `ph-img` placeholder thumbs, caption-only lightbox —
      fixture-only scaffolding, by design.

### In the plugin but NOT in the mockup (states the fixture never shows)
- [x] **1. Clamped blocks** — **Fixed 2026-07-30**: a 12+-line `.msg-user.clip` with the bottom-border
      tab. Verified clamped (234px, not full height) and still `position: sticky` — the exact pairing
      that regressed before (`.clip`'s `position: relative` beat `.msg-user`'s sticky).
- [~] ~~**2. "↩ Reverted file changes"** status line~~ — MOOT 2026-07-30, revert feature removed.
      The emoji-glyph `s-ic` path now has no live user (⏹ Stopped and Resumed both use SVGs).
- [x] **3. @-mention popup** — **Fixed 2026-07-30**: static `#mention` with four `.mi` rows (first
      `.sel`), hidden by default and toggled from the devbar, positioned above the composer the way
      `openMenu()` places it.
- [x] **4. Name-only image chip** — **Fixed 2026-07-30**: an `.att` with neither thumb nor size,
      showing the derived `file.jpg`, i.e. a replayed image past `IMAGE_BUDGET`.
- [x] **5. Multiple OUT rows** on one Bash io box (3-row box: IN + two OUTs, the live-only shape from
      Audit 1 #5) and **"Thought"-without-duration** — both added 2026-07-30.

### Orphan
- [x] **Dead CSS:** `.status .undo` — resolved 2026-07-30, deleted along with the rest of the
      revert feature.

---

## Handoff — open follow-ups (2026-07-29, next session)

Not renderer-parity, but the live threads to resume:

- **Session title fix (just landed, this commit):** `titleOf()` now skips `isMeta`/caveat first
  messages and runs `cleanInjected()`, so a session no longer titles as `<local-command-caveat>…`.
  Test: `title falls through a local-command-caveat to the first real message`.
- [x] **File-attachments — verified in `runIde` 2026-07-30** (user): the native save dialog opens
  and writes on download, and the CLI accepts `document` blocks in stream-json input (PDF/text).
  The inset focus ring (Audit 1 #8) was confirmed in the same pass.
- [x] **Code block with no language label** — **Done 2026-07-30** (option A, label `code`). Also fixed
  the fence regex while there: `(\w*)` matched word chars only, so `” ```c++ ”` parsed as lang "c" with
  "++" left in the code body; now `([^\s`]*)`. ACCEPTED (user, 2026-07-30): an EMPTY fence still
  renders a 48px block — header "code" + a copy button that copies nothing. No action intended.
- [x] **MCP tool line NAMES** — **Done 2026-07-30**: one `toolLabel()` rule for every tool — drop an
  `mcp_`/`mcp__` prefix, then Pascal-case on underscore runs. `mcp__playwright__browser_click` →
  `PlaywrightBrowserClick` (server kept for provenance). Applied at all four DISPLAY sites (live and
  replayed tool lines, replayed and permission cards); `openTool.name`/`ev.tool` comparisons keep the
  raw id. Built-ins pass through untouched. Mockup has fixtures.
  · **desc keys (option B) — Done 2026-07-30.** Chain extended on BOTH sides to
  `… query → url → element → filename → target`, verified identical key-for-key by comparing the
  two implementations. Settled against the REAL Playwright schemas (the server became available
  in-session), which corrected the guesses recorded here: there is no `selector` and no `ref` —
  both are `target`. `element` is the schema's own "human-readable element description" so it reads
  best; `target` ("exact target element reference from the page snapshot") is the last resort.
  Test `MCP tool lines describe themselves with element, filename, then target` covers all four
  precedences, incl. `url` still winning for `browser_navigate`.
- [x] Audit 3 mockup-coverage gaps — all closed 2026-07-30 (clamped block, @-mention popup, name-only
  chip, multi-OUT, no-duration thinking; reverted-status line and `.status .undo` moot with the
  revert removal).
