---
name: context
description: Manage portable project context/memory stored in the project's .claude/context/ folder. Use to initialize project memory, load a briefing at the start of a session, or save session state before ending. Triggers on /context, /context init, /context save, "catch me up", "save our progress", "remember where we left off".
argument-hint: "[init | load | save | <note to record>]"
---

# Project Context Manager

Maintain the project's persistent memory in `<project-root>/.claude/context/`. These files live **inside the project**, so the memory is fully portable: it travels with the repo across machines, sessions, and even other developers.

Determine the mode from the arguments:

- **`init`** → Initialize the context structure (first-time setup).
- **`load`**, no arguments, or phrases like "catch me up" → Load and brief.
- **`save`** or phrases like "save progress" → Persist the current session's state.
- **Any other text** → Treat it as a note: record it in the most fitting file (a decision goes to `decisions.md`, a task to `state.md`, a fact to `overview.md`), then confirm in one line what you recorded and where.

## File structure

All files live in `.claude/context/` at the project root:

| File | Purpose |
|---|---|
| `overview.md` | What the project is, tech stack, architecture, key directories, how to run/build/test, external services and credentials locations (never secrets themselves). Mostly stable. |
| `state.md` | **Current focus** — what is being worked on now, in-progress work with exact file paths, next steps as a checklist, known issues/blockers. |
| `decisions.md` | Log of decisions with rationale, newest first: `## YYYY-MM-DD — <decision>` followed by *why* and *alternatives rejected*. Never delete entries; mark superseded ones. |
| `journal.md` | Dated session log, newest first. One compact entry per session: what was done, what was learned, what's next. |
| `conventions.md` | Project-specific preferences the user has expressed: code style, naming, workflow rules, things to never do. |
| `gotchas.md` | Hard-won traps: flaky behaviors, misleading errors, workarounds and *why* they exist, "don't touch X without doing Y first". A lesson belongs here when it would cost real time to relearn. |
| `runbook.md` | Multi-step operational procedures: deploy steps, migration process, recovery steps, test-account setup. Locations of credentials only, never secrets. |
| `backlog.md` | Parked work: ideas, deferred features, "someday" refactors — anything worth remembering that is *not* the current focus. One line per item. |
| `glossary.md` | Domain vocabulary: business terms, entity names, abbreviations, and distinctions specific to this project (the things a fresh session gets subtly wrong). |

`overview.md`, `state.md`, `decisions.md`, and `journal.md` always exist after `init`. The rest are **lazy**: create each file only when there's real content for it — never as an empty stub.

## `init` — first-time setup

1. If `.claude/context/` already exists with content, say so and offer to update instead of overwriting.
2. Explore the project (README, manifest files like package.json/composer.json, directory layout, recent git log if it's a repo) and write an accurate `overview.md`.
3. Run the No CLAUDE.md policy check (below): migrate and delete any `CLAUDE.md`-family files found.
4. Create `state.md` — ask nothing; infer the current focus from recent git history or uncommitted changes if possible, otherwise leave a `## Current focus` section marked *(not yet set — will fill on first save)*.
5. Create `decisions.md` and `journal.md` with just a heading and a one-line format note.
6. If the project is a git repo, ensure `.claude/context/` is NOT ignored (check `.gitignore`); recommend committing it so the memory travels with the repo.

## `load` — session start briefing

1. Read every file in `.claude/context/`. Run the No CLAUDE.md policy check (below) — if a `CLAUDE.md` has appeared, migrate its content and delete it before briefing.
2. Give a **compact briefing** in this shape:
   - One paragraph: what the project is.
   - **Where we left off:** current focus and in-progress work from `state.md`, plus the most recent journal entry.
   - **Next steps:** the open checklist from `state.md`.
   - Any active blockers, recent decisions, or `gotchas.md` entries that affect the next steps.
3. Verify before trusting: if `state.md` references files or branches, spot-check they still exist; flag anything stale. If git shows commits newer than the last journal entry, note that the context may be behind and offer to reconcile.
4. End the briefing by proceeding with the top next step if the user asked to continue, otherwise stop after the briefing.

## `save` — session end snapshot

1. Review the current conversation and working state (git status/diff if available) and update:
   - `state.md` — rewrite it to reflect *now*: current focus, in-progress work with file paths and enough detail that a fresh session with zero conversation history could resume, updated next-steps checklist (check off done items, add new ones).
   - `journal.md` — prepend one entry: `## YYYY-MM-DD` + 3–8 bullet summary of what happened this session, discoveries, and gotchas hit.
   - `decisions.md` — append any decisions made this session with their rationale.
   - `gotchas.md` — append traps discovered this session that would cost time to rediscover.
   - `backlog.md` — add items deliberately deferred this session; remove items that were picked up (they move to `state.md`).
   - `overview.md` / `conventions.md` / `runbook.md` / `glossary.md` — only if something durable changed (new dependency, new procedure, new term, new user preference).
2. **Consolidate:** if `journal.md` has full entries older than ~10 sessions, first *promote* anything still valuable out of them (recurring trap → `gotchas.md`, stable fact → `overview.md`, standing preference → `conventions.md`), then compress those old entries into a one-line-per-session digest under a `## Digest` heading at the bottom. Detail may die; lessons must survive.
3. Write for a reader with **no memory of this conversation**: full paths, exact names, no "as discussed above".
4. Enforce the size targets from the Retention model below — when a file is over target, promote first, then cut.
5. Confirm with a one-line summary of what was saved and remind the user to commit `.claude/context/` if it's a git repo with uncommitted context changes.

## No CLAUDE.md policy

`.claude/context/` is the **sole** location for project context. The project must contain no `CLAUDE.md`, `CLAUDE.local.md`, or `claude.md` files anywhere — root, nested directories, or `.claude/`.

- On **every** mode (`init`, `load`, `save`, note), first glob for `**/CLAUDE.md`, `**/CLAUDE.local.md`, and `**/claude.md`. For each file found: migrate its content to the fitting context file (project facts / how-to-run → `overview.md`, style and workflow rules → `conventions.md`, procedures → `runbook.md`, warnings and traps → `gotchas.md`), then delete the file and confirm in one line what was migrated and removed.
- Never create a `CLAUDE.md`. If the user asks to add something "to CLAUDE.md", record it in the appropriate context file instead and say where it went.

## Auto memory

This project's `.claude/context/` folder **replaces Claude's automatic memory directory** (the per-project folder under `~/.claude/projects/.../memory/`). Whenever you would save an auto-memory fact, record it here instead so the memory stays portable with the repo:

- `user`-type facts (who the user is, preferences) and `feedback`-type facts (corrections, workflow rules) → `conventions.md`.
- `project`-type facts (goals, constraints, ongoing work) → `state.md` or `overview.md`, whichever fits.
- `reference`-type facts (URLs, dashboards, tickets) → `overview.md` under an `## External references` section.

Do not write to the global memory directory or its `MEMORY.md` index for this project; if entries for this project already exist there, migrate them into the files above on the next `save`.

## Retention model

100% preservation is not the goal — the **reload budget** is the constraint: every saved line competes for context window at the next `load`. Maximize signal per token, not volume.

- **Never store what the repo can answer.** Code structure, file contents, and git history are re-derivable with a Read or `git log` — don't copy them into context files. Spend the budget on what lives only in conversation: the *why* behind decisions, hard-won gotchas, user preferences.
- **Fidelity tiers:** `state.md` is working memory — full detail, tiny, rewritten every save. `decisions.md`, `gotchas.md`, `conventions.md`, `glossary.md`, `runbook.md` are long-term memory — permanent but compact. `journal.md` is the episodic buffer — detailed while recent, digested when old. `backlog.md` is one line per item.
- **Soft size targets:** `state.md` ≤ ~150 lines; every other file ≤ ~100 lines; `journal.md` keeps full entries only for the last ~10 sessions (older ones live in the digest). Over target → promote first, then cut.

## General rules

- All project context lives in `.claude/context/`; `CLAUDE.md` files must not exist in this project (see the No CLAUDE.md policy above).
- Dates are absolute (`2026-08-07`), never relative ("yesterday").
- Never store secrets, tokens, or passwords — store *where* they live instead (e.g., "API key in `.env` as `STRIPE_KEY`").
- These files are the source of truth for cross-session memory; when the conversation and the files disagree, the conversation (newer) wins — update the files.
- If the project has no `.claude/context/` and the user runs `load` or `save`, run `init` first, then continue with the requested action.
