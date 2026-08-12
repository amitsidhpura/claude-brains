# State

## Current focus
**Nothing in flight.** 0.5.1 is released and listed; the 2026-08-12 (third) session shipped the
windowed-replay fixes described below, committed to `main` and pushed, with no version bump — they
ride the next release. Next work is whatever is picked from backlog.md.

The defect register in `docs/manual-test.md` is untouched by that session and still stands at
**2 open ISSUE notes / 22 RESOLVED** — the open pair is 3.1 + 9.10, worked TOGETHER per the user.

## The machine: Windows
Repo `D:\sites\claude-brains`, home `C:\Users\Supple-7`. Transcripts live in
`C:\Users\Supple-7\.claude\projects\D--sites-<project>\` (non-alphanumerics → `-`). Java 21 +
Gradle 8.10.2 on PATH; run `./gradlew` from `plugin/`. Chrome for headless probes is at
`/c/Program Files/Google/Chrome/Application/chrome.exe`. The sibling test repo is
`D:\sites\claude-brains-test`; the fixture `.claude/commands/dummy-cmd.md` (for 3.1/9.10) still
has to be recreated there. Anything in these files reading `/home/syncroze/…` or `.zshrc` predates
the move and refers to a machine that is gone.

## What shipped in the 2026-08-12 (third) session
All from one user screenshot — a session resumed showing work six days stale. See decisions.md for
the reasoning and gotchas.md for the traps.
- **`SessionStore.readTranscript` holds the NEWEST blocks**, evicting from the front instead of
  stopping the read. `MAX_BLOCKS` 4,000 → 20,000. Cut lands on a `user` block via an unbounded
  forward scan; eviction triggers at `max + chunk`, so the retained count lands near the cap from
  either side rather than exactly on it.
- **`truncated` head block + `.status` marker** ("N earlier blocks not loaded") when anything was
  dropped, and none at all when nothing was. Candidate D of four, rendered side by side in
  `design/history-edge-probe.html` (kept, same idiom as `fold-fade-probe.html`) and chosen by the
  user.
- **`#fade-top` switches off at the top** (`body.at-top`, `updateTopFade()` in chat.html, mirrored
  in mockup.html) — it was washing out the marker.
- **`.t-prog` dedupes against the IN box**, closing the second lane of the duplicate-description
  bug; fixture `01b` gained three steps including its own negative control.
- **`-PskipVerifierIdes`** so one unreachable JetBrains host no longer stops the whole project.
  `verifyPlugin` refuses to run under it, and docs/release.md forbids releasing with it.

## Verification standing at the end of that session
- `./gradlew test` — 86 tests green, including `the block window keeps the newest turns, not the
  oldest`, whose negative control was RUN twice (once against the pre-fix parser, once against the
  bounded-scan eviction) and failed correctly both times.
- `./gradlew probe D:\sites\metrobuildsuppliers edac4a84-…` — 6,034 blocks, no `truncated` (under
  the cap), which is the check that the marker does not cry wolf.
- `python tools/live_harness.py` — 137/137 in real JCEF, re-run AFTER the `#fade-top` change.
- `-PskipVerifierIdes` exercised in all three directions: `test` green with and without it,
  `verifyPlugin` refusing under it.

## Next steps
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing): recreate the fixture at
      `D:\sites\claude-brains-test\.claude\commands\dummy-cmd.md` first. `cmdKind` (chat.html) has no
      custom-command detection, so everything outside {clear, compact} is greyed as 'tui'.
      Constraint: the CLI's `commands` payload has NO type field.
- [ ] VFS refresh after CLI writes (backlog "Next up") — accepted edits still need "Reload from
      disk"; fix shape already worked out.

## Known gaps (deliberately left)
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: Accept / Accept all edits / Reject on a bar under the diff editor;
  panes still read-only (tweak-travel is the remaining v2 half, in backlog).
- A filename longer than the whole tool line is hard-clipped with no ellipsis (offered, declined).
