# State

## Current focus
**Rename read-back fix + Marketplace upload automation, 2026-08-12.** Both complete and verified;
committed this session. The work sits on top of released **0.5.0** and is NOT yet released — an
installed IDE still runs 0.5.0, so the rename fix needs a 0.5.1 cut to reach the user's own IDE.

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

## Next steps
- [ ] **Dry-run the workflow now that it is on main**: `gh workflow run marketplace-upload.yml
      -f tag=v0.5.0 -f dry_run=true`, then `gh run watch`. Proves the download + filename assertion;
      the upload half cannot be rehearsed (the Marketplace refuses a duplicate version).
- [ ] **Cut 0.5.1** — the rename fix is user-visible and the user's own IDE is still on 0.5.0.
      This will be the automation's first real upload. `changeNotes` in `plugin/build.gradle.kts:98`
      is STALE (still 0.3.3; 0.4.0 and 0.5.0 both shipped those notes) — fix it as step 1b.
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
