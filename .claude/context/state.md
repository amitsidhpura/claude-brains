# State

## Current focus
**Nothing in flight.** The 2026-08-13 header-title work is finished, verified and committed to
`main`. Next work is whatever is picked from the list below or backlog.md.

## Released: 0.5.2 (2026-08-13), Marketplace-Approved
`plugin/build.gradle.kts`, `updatePlugins.xml`, tag `v0.5.2` and the GitHub release asset
`claude-brains-0.5.2.zip` all agree. The Marketplace listing shows **Approved** — uploaded 13 Aug,
2.6 MB, compatibility range 242.0+, both verification rows green (IDE run "no issues occurred";
verifier 1.408 "Compatible" on IntelliJ IDEA 2026.2.1). `release: published` →
`.github/workflows/marketplace-upload.yml` ran with no manual step; sixth release, still unsigned
(the Marketplace signs its own copy — see backlog "Someday").

**Everything since 0.5.2 is UNRELEASED and rides the next version** — currently the 8.16 header-title
fix. User-facing wording for shipped versions is the `changeNotesHtml` block in
`plugin/build.gradle.kts`, which `buildPlugin` refuses to let go stale.

## Which machine — check this FIRST, both are real
Two consecutive loads found the recorded machine was the wrong one. **This save was written on
Windows** (`D:\sites\claude-brains`). Full paths for both boxes are in overview.md § External
references. What actually differs and bites:
- The sibling test repo is `D:\sites\claude-brains-test` (Windows) vs
  `~/Sites/claude-brains-testing` (Linux) — different name, and neither is in git.
- **The 3.1/9.10 fixture does not travel.** `.claude/commands/dummy-cmd.md` exists in the Linux
  test repo; the Windows one holds only `.idea`. One file to recreate, but recreate it on
  whichever box you are on before starting 3.1 — do not trust a note saying it exists.

## Defect register (docs/manual-test.md)
**2 open ISSUE / 23 RESOLVED, and zero unticked checklist items.** The 2 open are 3.1 + 9.10, which
the user wants worked TOGETHER. Verification standing at the last close: `./gradlew test` **87
green**, live harness **154/154** in real JCEF, fixture 44 at 17/17 and proven to fail on BOTH its
defects against pre-fix `89f1714`.

8.16 was added and resolved on 2026-08-13: the header read "New conversation" beside a titled
`current` history row for the whole of a conversation's first turn. Both halves of the fix
(`titleProbed` probe at `message_start`, `seedUi()` on every page load) live in
`plugin/src/main/kotlin/.../ui/ChatPanel.kt`; reasoning in decisions.md, traps in gotchas.md, the
wire measurement in `_local/title_timing.py`.

## Next steps
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing). `cmdKind` in chat.html has
      no custom-command detection, so everything outside {clear, compact} greys as 'tui'.
      Constraint: `system/init`'s `commands` payload has NO type field, so the slash allowlist is
      the only lever. Recreate the `dummy-cmd.md` fixture on this machine first (above).
- [ ] VFS refresh after CLI writes (backlog "Next up") — accepted edits still need "Reload from
      disk"; fix shape already worked out.

## Known gaps (deliberately left)
- A reloaded webview recovers its chrome but NOT its log (backlog "Next up", 2026-08-13).
- **No way to kill a background process from the panel** — `interrupt()` only stops the in-flight
  response, and the roster rows are display-only. In backlog.
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: tweak-travel is the remaining v2 half (backlog).
- A filename longer than the whole tool line is hard-clipped with no ellipsis (offered, declined).
