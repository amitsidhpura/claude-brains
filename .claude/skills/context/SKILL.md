---
name: context
description: Manage portable project context/memory stored in the project's .claude/context/ folder. Use to initialize project memory, load a briefing at the start of a session, or save session state before ending. Triggers on /context, /context init, /context save, "catch me up", "save our progress", "remember where we left off".
argument-hint: "[init | load | save | <note to record>]"
---

# Project Context Manager

Maintain the project's persistent memory in `<project-root>/.claude/context/`. The files live inside the project, so the memory travels with the repo — across machines, sessions, and developers.

Modes, from the arguments:

- **`init`** → first-time setup.
- **`load`**, no arguments, or "catch me up" → load and brief.
- **`save`** or "save progress" → persist the session's state.
- **Any other text** → a note: record it in the fitting file (decision → `decisions.md`, task → `state.md`, fact → `overview.md`), confirm in one line what went where.

## File structure

All files live in `.claude/context/` at the project root:

| File | Purpose |
|---|---|
| `overview.md` | What the project is: stack, architecture, key directories, how to run/build/test, external services and where credentials live (never the secrets). Mostly stable. |
| `state.md` | **Current focus**: what is being worked on now with exact file paths, next steps as a checklist, known issues/blockers. |
| `decisions.md` | Decision log, newest first: `## YYYY-MM-DD — <decision>` + *why* + *alternatives rejected*. Never delete; mark superseded. |
| `journal.md` | Dated session log, newest first, one compact entry per session: done / learned / next. |
| `conventions.md` | User preferences: code style, naming, workflow rules, never-dos. |
| `gotchas.md` | Hard-won traps: flaky behaviors, misleading errors, workarounds and *why* — anything that would cost real time to relearn. |
| `runbook.md` | Operational procedures: deploy, migration, recovery, test accounts. Credential locations only, never secrets. |
| `backlog.md` | Parked work, one line per item — worth remembering, not current focus. |
| `glossary.md` | Domain vocabulary and the distinctions a fresh session gets subtly wrong. |

`overview.md`, `state.md`, `decisions.md`, `journal.md` exist after `init`; create the rest only when there is real content — never as an empty stub.

**Briefing tier**, read whole at every `load`: `state.md`, `overview.md`, `conventions.md`, plus `journal.md`'s newest entry. **Reference tier**, grepped at the moment work touches its subject and never read whole (it grows with the project): `decisions.md`, `gotchas.md`, `backlog.md`, `glossary.md`, `runbook.md`.

## `init`

1. If `.claude/context/` already has content, say so and offer to update instead of overwriting.
2. Explore the project (README, manifests, layout, recent git log) and write an accurate `overview.md`.
3. Create `state.md` — ask nothing; infer the current focus from git history or uncommitted changes, else mark *(not yet set — will fill on first save)*.
4. Create `decisions.md` and `journal.md` with a heading and a one-line format note.
5. In a git repo: ensure `.claude/context/` is NOT ignored, and recommend committing it so the memory travels.

## `load`

1. Read the briefing tier only: `state.md`, `overview.md`, `conventions.md` in full, and the newest `journal.md` entry (stop at the second `## ` heading). Do not read the reference tier here.
2. Brief compactly:
   - One paragraph: what the project is.
   - **Where we left off:** current focus from `state.md` + the newest journal entry.
   - **Next steps:** the open checklist from `state.md`.
   - Active blockers and the traps/preferences bearing on the next steps (from `state.md`, `conventions.md`).
3. Verify before trusting: spot-check that files/branches `state.md` names still exist; if git has commits newer than the last journal entry, flag that the context may be behind and offer to reconcile.
4. Proceed with the top next step if the user asked to continue; otherwise stop after the briefing.

## `save`

1. From the conversation and working state (git status/diff if available), update:
   - `state.md` — rewrite to reflect *now*: focus, in-progress work with file paths, updated checklist (tick done, add new), enough for a session with zero conversation history to resume. Carry forward the few traps and constraints that bear on the next steps, one line each pointing at the reference file — what isn't carried here isn't seen at `load`.
   - `journal.md` — prepend `## YYYY-MM-DD` + 3–8 bullets: what happened, discoveries, gotchas hit.
   - `decisions.md` — prepend (the file is newest-first) this session's decisions with rationale.
   - `gotchas.md` — add new traps under the topic section they belong to, not a dated section at the end.
   - `backlog.md` — add deferrals; remove items picked up (they move to `state.md`).
   - `overview.md` / `conventions.md` / `runbook.md` / `glossary.md` — only if something durable changed.
2. **Consolidate:** journal entries older than ~10 sessions → first *promote* what is still valuable (recurring trap → `gotchas.md`, stable fact → `overview.md`, standing preference → `conventions.md`), then compress into a one-line-per-session `## Digest` at the bottom. `decisions.md` the same on a slower clock: entries older than ~2 weeks → a digest of *outcome · why · key rejection*. Detail may die; lessons must survive.
3. Write for a reader with **no memory of this conversation**: full paths, exact names, no "as discussed above".
4. Enforce the Retention model — promote or compress first; cut facts last.
5. Confirm in one line what was saved; remind the user to commit `.claude/context/` if it has uncommitted changes.

## No CLAUDE.md policy

`.claude/context/` is the **sole** location for project context; no `CLAUDE.md`, `CLAUDE.local.md`, or `claude.md` may exist anywhere in the project.

- On **every** mode, first glob for `**/CLAUDE.md`, `**/CLAUDE.local.md`, `**/claude.md`. Migrate each hit into the fitting context file (project facts / how-to-run → `overview.md`, rules → `conventions.md`, procedures → `runbook.md`, traps → `gotchas.md`), delete it, and confirm in one line.
- Never create one. Asked to add something "to CLAUDE.md" → record it in the right context file and say where it went.

## Auto memory

`.claude/context/` replaces Claude's automatic memory directory (`~/.claude/projects/.../memory/`) for this project. Record would-be auto-memory facts here instead: `user`/`feedback` facts → `conventions.md`; `project` facts → `state.md` or `overview.md`; `reference` links → `overview.md` under `## External references`. Never write this project's facts to the global directory or its `MEMORY.md`; migrate any already there on the next `save`.

## Retention model

100% preservation is not the goal — signal per token is. The two tiers have two budgets:

- **Briefing tier** is read whole at every `load`, so every line competes for context window. Soft caps: `state.md` ≤ ~150 lines; `overview.md`, `conventions.md` ≤ ~100 each; one journal entry ≤ ~30. Over cap → move detail to a reference file, leave a one-line pointer.
- **Reference tier** is grepped on demand, so its budget is density, not length: no duplicated entries (a trap written in two sections WILL drift), no narrative retelling — each entry is the rule or outcome plus the minimum evidence to trust it. It may grow with the project; compress oldest into digests rather than deleting facts.
- **Never store what the repo can answer** — code structure, file contents, and git history are re-derivable with a Read or `git log`. Spend the budget on what lives only in conversation: the *why* behind decisions, hard-won traps, user preferences.
- `state.md` is working memory, rewritten every save. `journal.md` is the episodic buffer — detailed for the last ~10 sessions, digested beyond. `backlog.md` is one line per item.

## General rules

- Dates are absolute (`2026-08-07`), never relative ("yesterday").
- Never store secrets or tokens — store *where* they live (e.g., "API key in `.env` as `STRIPE_KEY`").
- These files are the source of truth for cross-session memory; when they and the conversation disagree, the conversation (newer) wins — update the files.
- `load`/`save` on a project with no `.claude/context/` → run `init` first, then continue.
