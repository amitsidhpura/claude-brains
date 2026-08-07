# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-07
- Initialized `.claude/context/` as the project's portable memory (`/context init`).
- Migrated the root `CLAUDE.md` (427 lines) into overview / conventions / gotchas / decisions /
  state / backlog / glossary, then deleted it per the no-CLAUDE.md policy (recoverable from git
  history, last at commit `ee7e9fc`).
- Migrated the global auto-memory ("commit only when asked") into conventions.md.
- Un-ignored `.claude/context/` in `.gitignore` (the rest of `.claude/` stays ignored).
- Deliberately skipped `runbook.md` — release procedure already lives in `docs/release.md`.
- Noted: `.claude/skills/` (the `/context` skill itself) is still git-ignored, so the workflow
  doesn't travel to a fresh clone — un-ignore it if portability is wanted (open next step).
- Nothing committed yet; the whole migration awaits one commit (user commits on request only).
