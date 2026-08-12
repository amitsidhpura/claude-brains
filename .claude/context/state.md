# State

## Current focus
**Nothing in flight. 0.5.1 is released** (2026-08-12): the rename read-back fix, shipped through the
new automatic Marketplace upload, which worked on its first live run. The Marketplace listed 0.5.1 as
"Under review" with JetBrains' own verifier reporting Compatible on every build 2024.2.6 → 2026.2.1 —
check it has flipped to approved/listed. Next work is whatever is picked from the list below.

The defect register in `docs/manual-test.md` is untouched by this session and still stands at
**2 open ISSUE notes / 22 RESOLVED** — the open pair is 3.1 + 9.10, worked TOGETHER per the user.

## The machine changed: Linux → Windows
Everything before 2026-08-12 was done on a Linux box (`/home/syncroze/Sites/…`, `.zshrc`). Work now
happens on **Windows 11**, `D:\sites\claude-brains`, home `C:\Users\Supple-7`. Consequences that bit:
- Transcripts are `C:\Users\Supple-7\.claude\projects\D--sites-<project>\` — the encoding rule is
  unchanged (non-alphanumerics → `-`), so `D:\sites\x` becomes `D--sites-x`.
- The sibling test repo is `D:\sites\claude-brains-test`; the OLD fixture
  `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` (for 3.1/9.10) is GONE and must be
  recreated there before that work resumes.
- Java 21 + Gradle 8.10.2 are on PATH here; `./gradlew` from `plugin/` needs no prefix.
- `~/.claude/ide/` holds several stale lockfiles (the known hot-reload dispose gap, backlog).

## The release path, as it now stands
`docs/release.md` is the procedure and it is current. Two things changed on 2026-08-12 and both are
already exercised: step 10 (Marketplace upload) is automatic, and step 1b (changeNotes) is enforced
by `buildPlugin` rather than remembered. A red `marketplace-upload` run sitting in the Actions
history is EXPECTED — run #2 is the deliberate `v9.9.9` negative control, not a broken release.

## What shipped this session
- **Rename looked broken and was not** (user report, real project `D:\sites\metrobuildsuppliers`):
  four `custom-title` records were on disk and correct; `SessionStore.computeTitle` only scanned the
  first 400 lines and they sat on 10455-10458 of a 10,458-line / 35 MB transcript. It now scans the
  whole file with a substring pre-filter past `TITLE_HEAD_LINES`. See decisions.md + gotchas.md.
  Two new tests in `SessionStoreRenameTest`; the regression one was RUN as a negative control
  against the pre-fix reader and failed exactly as it should.
- **Marketplace upload is automatic**: `.github/workflows/marketplace-upload.yml` (the repo's first
  workflow) fires on `release: published`, downloads the asset `gh release create` attached, and
  POSTs it to `https://plugins.jetbrains.com/api/updates/upload`. `docs/release.md` gained step 10
  and step 1b (changeNotes); the "uploads are manual via the web form" bullet is rewritten.
  The `JETBRAINS_MARKETPLACE_TOKEN` repo secret was set BY THE USER on 2026-08-12 (value never seen
  here; `gh secret list` shows the name only).
- **0.5.1 released** through that path end to end: asset `cmp`s identical to the local zip, feed
  serves 0.5.1, and the upload ran itself in 8s (API returned update id 1135613, Stable channel).
- **`changeNotes` is now enforced, not remembered**: `buildPlugin` fails if `changeNotesHtml` in
  `plugin/build.gradle.kts` has no `<b>X.Y.Z</b>` entry for the version being built. Both directions
  were run. The notes also caught up — 0.5.1, 0.5.0 and 0.4.0 are all listed, since Marketplace users
  had never seen the last two.

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
