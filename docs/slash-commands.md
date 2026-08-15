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

Picking a command from the menu **runs it immediately** (a command menu should act, not just
type); to pass an argument, type the command by hand and the menu autocompletes as you go.

Command detection requires the name to be the **entire first token** — whitespace or
end-of-message right after it. A message starting with a path (`/home/x …`) is ordinary text:
it is sent as a turn, and the autocomplete menu closes on the second `/` (or whenever the
filter has no hits, so a dead "No matching commands" popup can't swallow Enter). The one
ambiguous shape left is a bare single token like `/home` with nothing after it — that still
reads as a command attempt and is refused, same as the CLI's own TUI.

`/model` and `/effort` are intentionally Hidden: the composer already has dedicated controls
for them - the model chip + dropdown (search / custom models) and the effort slider (which
sends `/effort <level>` directly) - so slash entries would be redundant. The slider's silent
`/effort` turn was verified in `runIde` 2026-07-30; a resumed session shows the `/effort` turn
(the transcript records it and replay doesn't filter) - accepted as an honest audit trail.

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
- **`aliases` is display-only today.** The menu filter matches `name` and `description` only;
  typing `/review` will not surface `/code-review`. Known non-feature, not a bug.

BUILT-INS remain a hand-maintained allowlist in `chat.html` (`CMD_NATIVE` + `CMD_ALLOWED`):
we start minimal and reveal one command at a time as each is verified in `runIde`.
**To enable a built-in:** verify it works, then tell Claude the name - it is added to the
allowlist and starts appearing in the menu. Custom commands, skills and MCP prompts need none
of this.

Roster captured from the CLI `initialize` response 2026-08-15 (CLI 2.1.228, 50 built-in/bundled
commands; suffix-marked custom entries are excluded - they are auto-enabled, not listed).
Descriptions over 140 chars are truncated with `…`. Only the 3 marked Enabled are visible in
the menu today; the rest are hidden pending verification. Note: a project skill can SHADOW a
built-in name - this repo's `/context` skill replaces the built-in "Show current context
usage" entry in its own roster, marked "(project)" like any custom entry.

| Command | Status | Verified | Aliases | Description |
|---|---|---|---|---|
| `/__remote-workflow` | Hidden | [ ] |  | Run the workflow script delivered in this session environment (server-launched sessions only) |
| `/agents` | Hidden | [ ] |  | (removed) Ask Claude to create/manage subagents, or edit .claude/agents/ |
| `/artifact-capabilities` | Hidden | [ ] |  | Runtime capabilities a published Artifact page can be granted — behavior static HTML cannot provide on its own, such as the page reading… |
| `/artifact-design` | Hidden | [ ] |  | Design guidance and fundamentals for Artifacts. |
| `/artifact-diagramming` | Hidden | [ ] |  | Diagramming know-how for Artifacts — when a picture earns its place, how to draw one that shows the real mechanism, and the inline-SVG me… |
| `/auto-mode-setup` | Hidden | [ ] |  | Set up and customise auto mode — environment context, plus optional rule tweaks |
| `/autocompact` | Hidden | [ ] |  | Configure the auto-compact window size |
| `/batch` | Hidden | [ ] |  | Research and plan a large-scale change, then execute it in parallel across 5–30 isolated worktree agents that each open a PR. |
| `/claude-api` | Hidden | [ ] |  | Reference for the Claude API / Anthropic SDK — model ids, pricing, params, streaming, tool use, MCP, agents, caching, token counting, mod… |
| `/clear` | Enabled - native (IDE) | [x] | `/reset`, `/new` | Start a new session with empty context; previous session stays on disk (resumable with /resume) |
| `/code-review` | Hidden | [ ] | `/review` | Review the current diff, or a PR number/branch/path target, for correctness bugs and reuse/simplification/efficiency cleanups at the give… |
| `/color` | Hidden | [ ] |  | Set the prompt bar color for this session |
| `/compact` | Enabled - sent to CLI | [x] |  | Free up context by summarizing the conversation so far |
| `/config` | Hidden | [ ] | `/settings` | Set a setting by key |
| `/dataviz` | Hidden | [ ] |  | Use this skill whenever you are about to create ANY chart, graph, plot, dashboard, or data visualization, in ANY output medium — an HTML… |
| `/debug` | Hidden | [ ] |  | Enable debug logging for this session and help diagnose issues |
| `/deep-research` | Hidden | [ ] |  | Deep research harness — fan-out web searches, fetch sources, adversarially verify claims, synthesize a cited report. (dynamic workflow) |
| `/design` | Hidden | [ ] |  | Grant or revoke Claude agent access to your Design projects |
| `/design-consent` | Hidden | [ ] |  | Grant Claude agent access to your Design projects |
| `/design-revoke` | Hidden | [ ] |  | Revoke Claude agent access to your Design projects |
| `/design-sync` | Hidden | [ ] |  | Push a React design system to claude.ai/design. This runs a converter that bundles the real component code (from Storybook or a bare pack… |
| `/doctor` | Hidden | [ ] | `/checkup` | Health-check the user's Claude Code setup and fix issues: diagnose installation health — what the `claude doctor` terminal diagnostics co… |
| `/effort` | Hidden | [ ] |  | Set effort level for model usage |
| `/extra-usage` | Hidden | [ ] |  | Renamed to /usage-credits |
| `/fast` | Hidden | [ ] |  | Toggle fast mode (Opus 5) |
| `/fewer-permission-prompts` | Hidden | [ ] |  | Scan your transcripts for common read-only Bash and MCP tool calls, then add a prioritized allowlist to project .claude/settings.json to… |
| `/goal` | Hidden | [ ] |  | Set a goal — keep working until the condition is met |
| `/heapdump` | Hidden | [ ] |  | Dump the JS heap to ~/Desktop |
| `/import` | Hidden | [ ] |  | Import config from another AI coding agent |
| `/init` | Hidden | [ ] |  | Initialize a new CLAUDE.md file with codebase documentation |
| `/insights` | Hidden | [ ] |  | Generate a report analyzing your Claude Code sessions |
| `/list-agents` | Hidden | [ ] | `/peers` | List subagents and other Claude sessions you can message |
| `/loop` | Hidden | [ ] | `/proactive` | Run a prompt or slash command on a recurring interval (e.g. /loop 5m /foo). Omit the interval to let the model self-pace. |
| `/mcp` | Hidden | [ ] |  | Manage MCP servers |
| `/model` | Hidden | [ ] |  | Set the AI model for Claude Code |
| `/recap` | Hidden | [ ] |  | Generate a one-line session recap now |
| `/reload-skills` | Enabled - sent to CLI | [x] |  | Pick up skills added or changed on disk during this session |
| `/rename` | Hidden | [ ] | `/name` | Rename the current conversation |
| `/run` | Hidden | [ ] |  | Launch and drive this project's app to see a change working. Use when asked to run, start, or screenshot the app, or to confirm a change… |
| `/run-skill-generator` | Hidden | [ ] |  | Author or improve the run-<unit> skill — a per-project skill that tells agents how to build, launch, and drive this project's app. Use wh… |
| `/schedule` | Hidden | [ ] | `/routines` | Create, update, list, or run scheduled cloud agents (routines) that execute on a cron schedule. |
| `/security-review` | Hidden | [ ] |  | Complete a security review of the pending changes on the current branch |
| `/simplify` | Hidden | [ ] |  | Review the changed code for reuse, simplification, efficiency, and altitude cleanups, then apply the fixes. Quality only — it does not hu… |
| `/team-onboarding` | Hidden | [ ] |  | Help teammates ramp on Claude Code with a guide from your usage |
| `/ultrareview` | Hidden | [ ] |  | Start a cloud agent that finds and verifies bugs in your branch (~5-10 min, $5-$25 USD) · Runs in Claude Code on the web. See https://cod… |
| `/update-config` | Hidden | [ ] |  | Use this skill to configure the Claude Code harness via settings.json. Automated behaviors ("from now on when X", "each time X", "wheneve… |
| `/usage` | Hidden | [ ] | `/cost`, `/stats` | Show session cost, plan usage, and what's contributing to your limits |
| `/usage-credits` | Hidden | [ ] |  | Configure usage credits or request them from your admin when you hit a limit |
| `/verify` | Hidden | [ ] |  | Verify that a code change actually does what it's supposed to by exercising it end-to-end and observing behavior — drive the affected flo… |
| `/workflow-launch-exec` | Hidden | [ ] |  | Execute a server-launched workflow handoff (workflow_launch event sessions only) |
