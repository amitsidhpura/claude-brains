# State

## Current focus
**Nothing in flight, and nothing unreleased.** Everything from 2026-08-13 shipped in 0.5.3. Next
work is whatever is picked from the list below or backlog.md.

The spacing contract is worth knowing before touching `webview/chat.css`: `:root` carries
`--block-gap` (18px, independent blocks) and `--attach-gap` (8px, a line and what hangs off it), and
a new block under a line takes the attach gap — see glossary.md for the vocabulary and gotchas.md
for which FORM, since a flex parent needs `calc(attach - block)` and a block parent takes the value
directly. `design/tool-gap-probe.html` renders the candidates that were compared.

## Released: 0.5.3 (2026-08-13), Marketplace-Approved
Commit `f751503`, tag `v0.5.3`. The GitHub asset was verified HTTP 200 and `cmp`-identical to the
local zip, the feed advertises 0.5.3, and the Marketplace accepted it as version id 1137360 —
**Approved**, 2.6 MB, 242.0+, four green verification rows (IDE run clean; verifier 1.408 Compatible
on 2026.2.1 / 2026.1.5 / 2025.3.6.1). Seventh release, still unsigned (the Marketplace signs its own
copy — see backlog "Someday"). It ships the 8.16 header-title fix and the attached-block spacing.

`./gradlew verifyPlugin` for it, run WITHOUT `-PskipVerifierIdes`: Compatible on all seven PhpStorm
branches 242→262, zero warnings. User-facing wording lives in the `changeNotesHtml` block of
`plugin/build.gradle.kts`, which `buildPlugin` refuses to let go stale; the GitHub release body
follows the structure in `docs/release.md` § Release notes.

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
