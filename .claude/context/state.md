# State

## Current focus
**Nothing in flight.** Two sessions on 2026-08-12 shipped four webview fixes, all on `main` and
pushed, with **no version bump** — they ride the next release. 0.5.1 remains the released version.
Next work is whatever is picked from backlog.md.

The fourth fix (the IN/OUT box matching the permission card's diff, plus the foldable-block
contract) was **confirmed by the user in `runIde`** — "showing perfect" — which is what the
scrollbar geometry needed, since headless is unreliable for exactly that.

The defect register in `docs/manual-test.md` still stands at **2 open ISSUE notes / 22 RESOLVED**
(the open pair is 3.1 + 9.10, worked TOGETHER per the user). Three unchecked `[ ]` items were
ADDED this session — 8.13, 8.14, 8.15 — which are live-CLI confirmations of this session's work,
not defects.

## The machine: Linux (corrected 2026-08-12)
Earlier saves claimed Windows `D:\sites\claude-brains`; this session ran on **Linux**, repo
`/home/syncroze/Sites/claude-brains`, home `/home/syncroze`, Java 21.0.12 (Temurin), `claude` at
`~/.local/bin/claude` (versions under `~/.local/share/claude/versions/`, 2.1.228 current).
Transcripts at `~/.claude/projects/-home-syncroze-Sites-<project>/`. The sibling test repo is
`~/Sites/claude-brains-testing` (**not** `-test`), and its fixture
`.claude/commands/dummy-cmd.md` **exists** — so 3.1/9.10 is unblocked, contrary to older notes.
Both machines are real; check which one you are on before trusting a path.

## What shipped in the 2026-08-12 (fourth) session
Three user-reported issues, each measured before it was touched. See decisions.md for the
reasoning, gotchas.md for the traps.
1. **Rename editor closes on an outside click** — discards, like Escape/✕. Listens on the
   **CAPTURE** phase (the only such dismissal in the file) because the header controls next to the
   title all `stopPropagation`.
2. **MCP tool lines** — `"function"` moved `DESC_KEYS` → `IN_KEYS` in `RenderLimits.kt`, so
   `browser_evaluate`'s JS body renders in the IN box like a Bash command and its line is blank by
   design. Plus `.t-desc` clamped to one line with an ellipsis + `title` tooltip.
3. **Busy state vs background tasks** — `message_start` now sets busy for a turn the panel did not
   send, and `pendingBgTasks` excludes background shells. Also `ClaudeCli.kt` now guards
   `if (!stopped) onEvent(line)`.
4. **The IN/OUT box now has the diff's geometry** (2026-08-12, fifth session). `.io-row` is the
   scroll box and carries the padding, so the scrollbar is flush on the border and full width;
   `.io-k` is sticky and owns the left 10px; the cut/note markers moved OUT of the row (they are
   siblings inside `.io`, mounted via a DocumentFragment from `ioRow`); `foldBlock` is given the
   ROW, so a folded row crops instead of scrolling. All five foldable surfaces now obey one
   contract written above the `.fold` rules in chat.css.

## Verification standing at the end of that session
- `./gradlew test --rerun-tasks` — **87 tests** green (86 before; +1 for the browser_evaluate IN
  box, whose fixture is multi-line and ~2,200 chars because the 21-char one-liner it replaced
  could not express the bug).
- Every fix had its **negative control RUN**, not just written — against `git show HEAD:` of the
  file in each case.
- Browser-measured through the REAL spliced `chat.html` (not just the mockup) at 420px and default
  width; the busy-state fix was verified against **real captured CLI wire frames**.
- **`runIde` sweep DONE 2026-08-13**, except one item. User confirmed the IN/OUT + fold work and
  ticked **8.13** and **8.14**. `python tools/live_harness.py` ran in real JCEF: **154/154**, so
  the io-box restructure regressed nothing (fixture 04's bg chip, 01b, 40's tool-line paths all
  still green), and fixture 44 passes 17/17.
- **Fixture 44 gained a step during that run, and the reason matters.** Its first all-green run was
  partly vacuous: replayed against 89f1714 (pre-fix) it failed on the stuck-busy defect but PASSED
  on the CLI-initiated-turn defect, because the first bug leaves busy already true — the two mask
  each other in the fixture exactly as they do in the panel. A forced `setBusy(false)` step now
  sits before the notification turn, and the fixture fails on BOTH against 89f1714 (5 assertions).
- **8.15 confirmed 2026-08-13 against a real CLI**, driven and recorded over CDP (compose +
  `submit()` from `tools/cdp.py`, with `window.onClaudeEvent` wrapped to timestamp every frame
  against `busy`/button state). Full evidence is in the 8.15 note in `docs/manual-test.md`; the
  decisive number is **0 content deltas rendered while the button read Send**. The technique is
  worth reusing — see gotchas.
- Register: **2 open ISSUE notes / 22 RESOLVED**, unchanged (8.13-8.15 are checklist items, not
  defects). The whole `runIde` sweep is now closed.

## Next steps
- [ ] **3.1 custom commands + 9.10 together** — see below; the fixture exists on this machine, so
      it is unblocked. This is the top of the queue now.
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing). `cmdKind` (chat.html) has
      no custom-command detection, so everything outside {clear, compact} greys as 'tui'.
      Constraint: the CLI's `commands` payload has NO type field. Fixture already in place.
- [ ] VFS refresh after CLI writes (backlog "Next up") — accepted edits still need "Reload from
      disk"; fix shape already worked out.

## Known gaps (deliberately left)
- **No way to kill a background process from the panel** — found this session, now in backlog:
  `interrupt()` only stops the in-flight response, and the roster rows are display-only.
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: tweak-travel is the remaining v2 half (backlog).
- A filename longer than the whole tool line is hard-clipped with no ellipsis (offered, declined).
