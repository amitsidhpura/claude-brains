# Slash commands

The plugin drives the `claude` CLI over `--input-format stream-json` (headless SDK mode) -
there is no interactive terminal, so a picked command can only ever be sent as a user-message
turn. Consequences:

- **Enabled - sent to CLI**: forwarded as a turn; the CLI expands it (skills, prompts,
  custom/project commands, and prompt-style built-ins like `/compact`).
- **Enabled - native (IDE)**: handled by the plugin itself (`/clear` -> new conversation),
  never forwarded.
- **Enabled - custom (automatic)**: project/user command files, project/user skills, and
  MCP-server prompts — detected from the wire (below), shown with a muted source badge
  ("project" / "user" / "mcp"), and sent as a turn. Never hand-listed.
- **Hidden**: not shown in the slash menu at all, and refused if typed. A built-in that is
  either local/display/settings/harness-only with no headless effect, or simply **not yet
  verified**.

Picking a command from the menu **runs it immediately if it takes no argument** (a command menu
should act, not just type). A command that advertises an `argumentHint` — required (`<x>`) or
optional (`[x]`) alike — is **inserted into the composer as `/name ` instead**, so the sub-mode or
argument can be typed before sending; this matches the terminal, where picking a command completes
it into the prompt and you decide when to send. The rule is `cmdTakesArg()` in chat.html: any
non-empty hint inserts. It used to ask whether an argument was REQUIRED, which fired `/context`
bare when the hint `[init | load | save]` was really a menu of sub-modes (user report 2026-08-16;
rationale in .claude/context/decisions.md, pinned by fixture 50).

Which of the enabled commands that actually means, measured off the wire 2026-08-17 (a bare
`initialize` control request to CLI 2.1.233 — the roster's keys are exactly `{name, description,
argumentHint, aliases?}`, with no `immediate` flag despite the binary carrying one internally):

| picked → inserted (has a hint) | picked → runs now (no hint) |
|---|---|
| `/compact` `/context` `/code-review` `/simplify` `/loop` `/batch` | `/reload-skills` `/verify` `/run` `/security-review` `/init` `/recap` `/goal` `/deep-research` `/list-agents` |

Re-measure after a CLI upgrade rather than reading hints out of the binary: `strings` holds TWO
records for `/goal`, one of them with a hint, and the roster sends the hintless one.

Command detection requires the name to be the **entire first token** — whitespace or
end-of-message right after it. A message starting with a path (`/home/x …`) is ordinary text:
it is sent as a turn, and the autocomplete menu closes on the second `/` (or whenever the
filter has no hits, so a dead "No matching commands" popup can't swallow Enter). The one
ambiguous shape left is a bare single token like `/home` with nothing after it — that still
reads as a command attempt and is refused, same as the CLI's own TUI.

`/model` and `/effort` are intentionally Hidden: the composer already has dedicated controls
for them - the model chip + dropdown (search / custom models) and the effort slider (which
sends `/effort <level>` directly) - so slash entries would be redundant. Both changes now show
the same way (user decisions 2026-08-25, superseding the 2026-07-30 "silent /effort, visible on
resume" acceptance): live and resume alike render ONLY the CLI's confirmation line ("Set model
to ..." / "Set effort level to ..."), never a command bubble. Mechanics: the chip's `set_model`
control request and the slider's `/effort` turn each make the CLI write a command trio to the
transcript (CLI 2.1.245); replay drops both `<command-name>` wrappers (scoped to exactly these
two), live draws the /effort confirmation from the CLI's synthetic assistant frame through the
`effortMuted` gate, and neither wrapper can become a derived session title any more
(renderer-parity.md has the full records).

## How custom commands are detected (measured 2026-08-15, CLI 2.1.228)

The command entry schema is `{name, description, argumentHint, aliases?}` - still **no type
field**. But every entry sourced from `.claude/commands/**.md` or `.claude/skills/*/SKILL.md`
(project or user base, even a description-less file) arrives with its description suffixed
**" (project)"** or **" (user)"** - in the initialize response AND in `commands_changed`
payloads, with zero false positives across 107 built-in entries in two captures. `markCustom()`
in chat.html strips that suffix into a source badge and auto-enables the command; MCP prompts
are recognised by their `mcp__<server>__<prompt>` name instead. Nested command files name as
`sub:nested-cmd` (read off the wire). A custom file named like a built-in cannot shadow
`/clear` or the allowlist - `cmdKind` checks native/allowlist first.

Two consequences worth knowing:
- **The CLI watches the commands dir itself.** A bare file drop pushes `commands_changed`
  ~2.5s later and a deletion ~1s later, with NO turn in between (both measured headless
  2026-08-15 with a 45s quiet wait) - the menu follows automatically. An earlier same-day
  probe concluded "nothing fires on a file drop"; that was a measurement error (the
  `/reload-skills` turn was sent inside the watcher's debounce window, so its push and the
  watcher's were conflated). **The watch covers the PROJECT dir only**: a file added under
  `~/.claude/commands/` produced no push (user manual pass, 2026-08-15) — user-level changes
  need `/reload-skills` (headless-safe, verified, Enabled), which also remains the manual
  re-sync lever if a project watcher event is ever missed.
- **`aliases` are first-class (2026-08-17).** The menu filter scores an alias like the name it
  stands for (`/review` surfaces `/code-review` at rank 0, `/peers` → `/list-agents`), rows show
  aliases muted beside the name, and a TYPED alias is resolved to its command before the allowlist
  gate and sent under the canonical name — so `/new` and `/reset` reach `/clear`'s native branch.
  `canonicalCmd()` in chat.html; pinned by fixture 52.

BUILT-INS remain a hand-maintained allowlist in `chat.html` (`CMD_NATIVE` + `CMD_ALLOWED`):
we start minimal and reveal one command at a time as each is verified in `runIde`.
**To enable a built-in:** verify it works, then tell Claude the name - it is added to the
allowlist and starts appearing in the menu. Custom commands, skills and MCP prompts need none
of this.

**Scope of this table:** it is the complete set the CLI advertises to a HEADLESS stream-json
client - which is everything the panel can ever see or send - NOT the CLI's full command
surface. TUI-only commands (`/login`, `/resume`, `/help`, `/add-dir`, `/rewind`, `/diff`,
`/update`, ...) are registered as interactive/local commands in the binary and never appear in
the initialize response at all; they are absent by construction, not Hidden rows. By the
project philosophy they are the terminal's half. The list is also machine-specific: bundled and
user-installed skills ride the same roster, so it drifts with CLI updates and skill changes.

Roster captured from the CLI `initialize` response 2026-08-15 (CLI 2.1.228; re-verified against
2.1.233 the same day: identical names, same entry schema, suffix marker intact). Suffix-marked
custom entries are excluded - they are auto-enabled, not listed. 51 rows: 50 from the main-repo
capture plus the built-in `/context`, restored from the testing-repo capture because this repo's
own /context project skill shadows it (see the shadowing note above).
Descriptions over 140 chars are truncated with `…`. The tables are grouped by IDE-development
relevance (user-picked 2026-08-15); alphabetical within each group. The 16 in the first group
are visible in the menu; every other built-in is hidden.

**What `Verified [x]` means, and what it used to mean.** It now means **the command was driven
through the LIVE panel and its output rendered** — all 16 swept 2026-08-15 (harness in the
session scratchpad; matrix in the 3.8 register entry). It previously meant only "the CLI accepted
it headlessly", which is a strictly weaker claim and one that hid a real defect for hours:
`/context` passed the headless check and rendered nothing in the panel. Two commands were found
broken by the stronger test and fixed the same day; the rest passed. Do not re-mark a command
`[x]` on headless evidence alone. Note: a project skill can SHADOW a
built-in name - this repo's `/context` skill replaces the built-in "Show current context
usage" entry in its own roster, marked "(project)" like any custom entry.

### Enabled — the IDE development set

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/clear` | Enabled - native (IDE) | [x] | `/reset`, `/new` | Start a new session with empty context; previous session stays on disk (resumable with /resume) |
| `/compact` | Enabled - sent to CLI | [x] |  | Free up context by summarizing the conversation so far |
| `/context` | Enabled - sent to CLI | [x] |  | Show current context usage |
| `/code-review` | Enabled - sent to CLI | [x] | `/review` | Review the current diff, or a PR number/branch/path target, for correctness bugs and reuse/simplification/efficiency cleanups at the give… |
| `/simplify` | Enabled - sent to CLI | [x] |  | Review the changed code for reuse, simplification, efficiency, and altitude cleanups, then apply the fixes. Quality only — it does not hu… |
| `/verify` | Enabled - sent to CLI | [x] |  | Verify that a code change actually does what it's supposed to by exercising it end-to-end and observing behavior — drive the affected flo… |
| `/run` | Enabled - sent to CLI | [x] |  | Launch and drive this project's app to see a change working. Use when asked to run, start, or screenshot the app, or to confirm a change… |
| `/security-review` | Enabled - sent to CLI | [x] |  | Complete a security review of the pending changes on the current branch |
| `/init` | Enabled - sent to CLI | [x] |  | Initialize a new CLAUDE.md file with codebase documentation. **Note:** in THIS repo the /context workflow replaces CLAUDE.md — don't run /init here. |
| `/recap` | Enabled - sent to CLI | [x] |  | Generate a one-line session recap now |
| `/goal` | Enabled - sent to CLI | [x] |  | Set a goal — keep working until the condition is met |
| `/loop` | Enabled - sent to CLI | [x] | `/proactive` | Run a prompt or slash command on a recurring interval (e.g. /loop 5m /foo). Omit the interval to let the model self-pace. |
| `/batch` | Enabled - sent to CLI | [x] |  | Research and plan a large-scale change, then execute it in parallel across 5–30 isolated worktree agents that each open a PR. |
| `/deep-research` | Enabled - sent to CLI | [x] |  | Deep research harness — fan-out web searches, fetch sources, adversarially verify claims, synthesize a cited report. (dynamic workflow) |
| `/list-agents` | Enabled - sent to CLI | [x] | `/peers` | List subagents and other Claude sessions you can message |
| `/reload-skills` | Enabled - sent to CLI | [x] |  | Pick up skills added or changed on disk during this session |

### Hidden — redundant with panel UI, or declined

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/model` | Hidden | [ ] |  | Set the AI model for Claude Code Composer has the model chip + dropdown. |
| `/effort` | Hidden | [ ] |  | Set effort level for model usage Composer has the effort slider. |
| `/rename` | Hidden | [ ] | `/name` | Rename the current conversation The panel header has inline rename (same custom-title record). |
| `/usage` | Hidden | [ ] | `/cost`, `/stats` | Show session cost, plan usage, and what's contributing to your limits **Declined 2026-08-06** as a panel surface — stays Hidden by decision, not omission. |

### Hidden — configuration & diagnostics (the terminal's half)

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/agents` | Hidden | [ ] |  | (removed) Ask Claude to create/manage subagents, or edit .claude/agents/ Description says "(removed)". |
| `/auto-mode-setup` | Hidden | [ ] |  | Set up and customise auto mode — environment context, plus optional rule tweaks |
| `/autocompact` | Hidden | [ ] |  | Configure the auto-compact window size |
| `/color` | Hidden | [ ] |  | Set the prompt bar color for this session |
| `/config` | Hidden | [ ] | `/settings` | Set a setting by key |
| `/debug` | Hidden | [ ] |  | Enable debug logging for this session and help diagnose issues |
| `/doctor` | Hidden | [ ] | `/checkup` | Health-check the user's Claude Code setup and fix issues: diagnose installation health — what the `claude doctor` terminal diagnostics co… |
| `/extra-usage` | Hidden | [ ] |  | Renamed to /usage-credits |
| `/fast` | Hidden | [ ] |  | Toggle fast mode (Opus 5) |
| `/fewer-permission-prompts` | Hidden | [ ] |  | Scan your transcripts for common read-only Bash and MCP tool calls, then add a prioritized allowlist to project .claude/settings.json to… |
| `/heapdump` | Hidden | [ ] |  | Dump the JS heap to ~/Desktop |
| `/import` | Hidden | [ ] |  | Import config from another AI coding agent |
| `/insights` | Hidden | [ ] |  | Generate a report analyzing your Claude Code sessions |
| `/mcp` | Hidden | [ ] |  | Manage MCP servers |
| `/run-skill-generator` | Hidden | [ ] |  | Author or improve the run-<unit> skill — a per-project skill that tells agents how to build, launch, and drive this project's app. Use wh… |
| `/schedule` | Hidden | [ ] | `/routines` | Create, update, list, or run scheduled cloud agents (routines) that execute on a cron schedule. |
| `/team-onboarding` | Hidden | [ ] |  | Help teammates ramp on Claude Code with a guide from your usage |
| `/ultrareview` | Hidden | [ ] |  | Start a cloud agent that finds and verifies bugs in your branch (~5-10 min, $5-$25 USD) · Runs in Claude Code on the web. See https://cod… |
| `/update-config` | Hidden | [ ] |  | Use this skill to configure the Claude Code harness via settings.json. Automated behaviors ("from now on when X", "each time X", "wheneve… |
| `/usage-credits` | Hidden | [ ] |  | Configure usage credits or request them from your admin when you hit a limit |

### Hidden — bundled task skills (work as turns; enable on request)

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/artifact-capabilities` | Hidden | [ ] |  | Runtime capabilities a published Artifact page can be granted — behavior static HTML cannot provide on its own, such as the page reading… |
| `/artifact-design` | Hidden | [ ] |  | Design guidance and fundamentals for Artifacts. |
| `/artifact-diagramming` | Hidden | [ ] |  | Diagramming know-how for Artifacts — when a picture earns its place, how to draw one that shows the real mechanism, and the inline-SVG me… |
| `/claude-api` | Hidden | [ ] |  | Reference for the Claude API / Anthropic SDK — model ids, pricing, params, streaming, tool use, MCP, agents, caching, token counting, mod… |
| `/dataviz` | Hidden | [ ] |  | Use this skill whenever you are about to create ANY chart, graph, plot, dashboard, or data visualization, in ANY output medium — an HTML… |
| `/design-sync` | Hidden | [ ] |  | Push a React design system to claude.ai/design. This runs a converter that bundles the real component code (from Storybook or a bare pack… |

### Hidden — internal / special session types

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/__remote-workflow` | Hidden | [ ] |  | Run the workflow script delivered in this session environment (server-launched sessions only) |
| `/workflow-launch-exec` | Hidden | [ ] |  | Execute a server-launched workflow handoff (workflow_launch event sessions only) |
| `/design` | Hidden | [ ] |  | Grant or revoke Claude agent access to your Design projects |
| `/design-consent` | Hidden | [ ] |  | Grant Claude agent access to your Design projects |
| `/design-revoke` | Hidden | [ ] |  | Revoke Claude agent access to your Design projects |
