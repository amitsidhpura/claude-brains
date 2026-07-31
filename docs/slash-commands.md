# Slash commands

The plugin drives the `claude` CLI over `--input-format stream-json` (headless SDK mode) -
there is no interactive terminal, so a picked command can only ever be sent as a user-message
turn. Consequences:

- **Enabled - sent to CLI**: forwarded as a turn; the CLI expands it (skills, prompts,
  custom/project commands, and prompt-style built-ins like `/compact`).
- **Enabled - native (IDE)**: handled by the plugin itself (`/clear` -> new conversation),
  never forwarded.
- **Hidden**: not shown in the slash menu at all, and refused if typed. Either a
  local/display/settings/harness command with no headless effect, or simply **not yet verified**.

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

The command list carries only `{name, description, argumentHint}` - **no type field** - so the
enabled set is a hand-maintained allowlist in `chat.html` (`CMD_NATIVE` + `CMD_ALLOWED`). We
start minimal and reveal one command at a time as each is verified in `runIde`; only enabled
commands appear in the menu.

**To enable a command:** verify it works, then tell Claude the name - it is added to the
allowlist (`CMD_ALLOWED` for turn commands, `CMD_NATIVE` for ones needing IDE handling) and
starts appearing in the menu.

Roster captured from the CLI `initialize` response (39 commands). Only the 2
marked Enabled are visible in the menu today; the rest are hidden pending verification.

| Command | Status | Verified | Description |
|---|---|---|---|
| `/__remote-workflow` | Hidden | [ ] | Run the workflow script delivered in this session environment (server-launched sessions only) |
| `/agents` | Hidden | [ ] | (removed) Ask Claude to create/manage subagents, or edit .claude/agents/ |
| `/batch` | Hidden | [ ] | Research and plan a large-scale change, then execute it in parallel across 5–30 isolated worktree agents that each open a PR. |
| `/claude-api` | Hidden | [ ] | Reference for the Claude API / Anthropic SDK — model ids, pricing, params, streaming, tool use, MCP, agents, caching, token counting, model migration. TRIGGER — read BEFORE opening the target file; don't skip because it "looks like a one-liner" — whenever: the prompt names Claude/Anthropic in any form (Claude, Anthropic, Fable, Opus, Sonnet, Haiku, `anthropic`, `@anthropic-ai`, `claude-*`, `us.anthropic.*`, `[1m]`); the user asks about an LLM (pricing/model choice/limits/caching) — never answer from memory; OR the task is LLM-shaped with provider unstated (agent/MCP/tool-definition/multi-agent/RAG/LLM-judge/computer-use; generate/summarize/extract/classify/rewrite/converse over NL; debugging refusals/cutoffs/streaming/tool-calls/tokens). SKIP only when another provider is being worked on (overrides all triggers): OpenAI/GPT/Gemini/Llama/Mistral/Cohere/Ollama named in the query; OR `grep -rE 'openai\|langchain_openai\|google.generativeai\|genai\|mistralai\|cohere\|ollama'` over the project hits (run this grep FIRST if no provider named — don't Read the file). |
| `/clear` | Enabled - native (IDE) | [x] | Start a new session with empty context; previous session stays on disk (resumable with /resume) |
| `/code-review` | Hidden | [ ] | Review the current diff for correctness bugs and reuse/simplification/efficiency cleanups at the given effort level (low/medium: fewer, high-confidence findings; high→max: broader coverage, may include uncertain findings). Pass --comment to post findings as inline PR comments, or --fix to apply the findings to the working tree after the review. |
| `/color` | Hidden | [ ] | Set the prompt bar color for this session |
| `/compact` | Enabled - sent to CLI | [x] | Free up context by summarizing the conversation so far |
| `/config` | Hidden | [ ] | Set a setting by key |
| `/context` | Hidden | [ ] | Show current context usage |
| `/dataviz` | Hidden | [ ] | Use this skill whenever you are about to create ANY chart, graph, plot, dashboard, or data visualization, in ANY output medium — an HTML or React artifact, inline SVG, plotting code in any library (matplotlib, plotly, d3, Recharts, …), an image/PNG you will render and upload, or a chart shared into Slack. Read it BEFORE writing the first line of chart code, choosing chart colors, building a stat tile / meter / KPI row, or laying out a dashboard. Produces visualizations that read as one system — elegant, accessible, consistent in light and dark — using a brand-neutral placeholder palette you swap for your own. Teaches a design-system-agnostic method: a form heuristic, a color formula with a runnable validator, mark specs, and interaction rules. A validated default palette is documented in `references/palette.md` — swap that file's values for your brand's. Triggers on: "chart", "graph", "plot", "data viz", "visualization", "dashboard", "analytics", "visualize data", "categorical colors", "sequential / diverging palette", "stat tile", "sparkline", "heatmap", "legend", "axis", "tooltip", "chart colors", "color by series". |
| `/debug` | Hidden | [ ] | Enable debug logging for this session and help diagnose issues |
| `/deep-research` | Hidden | [ ] | Deep research harness — fan-out web searches, fetch sources, adversarially verify claims, synthesize a cited report. (dynamic workflow) |
| `/doctor` | Hidden | [ ] | Health-check the user's Claude Code setup and fix issues: diagnose installation health — what the `claude doctor` terminal diagnostics cover — from local data (duplicate or leftover installs, PATH, unparseable settings files, broken or colliding agent definitions); find unused skills, MCP servers, and plugins versus their context cost and disable dead weight; deduplicate local CLAUDE.md files against checked-in ones; trim checked-in CLAUDE.md files by cutting content a session could derive from the codebase (directory layouts, tech-stack lists, architecture overviews) while keeping gotchas, rationale, and non-standard conventions; migrate always-loaded CLAUDE.md guidance into lazy skills and nested CLAUDE.md files; flag slow hooks and context-heavy extensions; check the installed version is current; make auto mode the default permission mode; and pre-approve frequently denied read-only commands. Use when the user asks for a doctor run, checkup, audit, tune-up, or cleanup of their Claude Code setup or configuration. |
| `/effort` | Hidden | [ ] | Set effort level for model usage |
| `/extra-usage` | Hidden | [ ] | Renamed to /usage-credits |
| `/fast` | Hidden | [ ] | Toggle fast mode (Opus 5) |
| `/fewer-permission-prompts` | Hidden | [ ] | Scan your transcripts for common read-only Bash and MCP tool calls, then add a prioritized allowlist to project .claude/settings.json to reduce permission prompts. |
| `/goal` | Hidden | [ ] | Set a goal — keep working until the condition is met |
| `/heapdump` | Hidden | [ ] | Dump the JS heap to ~/Desktop |
| `/init` | Hidden | [ ] | Initialize a new CLAUDE.md file with codebase documentation |
| `/insights` | Hidden | [ ] | Generate a report analyzing your Claude Code sessions |
| `/loop` | Hidden | [ ] | Run a prompt or slash command on a recurring interval (e.g. /loop 5m /foo, defaults to 10m) |
| `/mcp` | Hidden | [ ] | Manage MCP servers |
| `/model` | Hidden | [ ] | Set the AI model for Claude Code |
| `/recap` | Hidden | [ ] | Generate a one-line session recap now |
| `/reload-skills` | Hidden | [ ] | Pick up skills added or changed on disk during this session |
| `/rename` | Hidden | [ ] | Rename the current conversation |
| `/review` | Hidden | [ ] | Review a GitHub pull request; for your working diff use /code-review |
| `/run` | Hidden | [ ] | Launch and drive this project's app to see a change working. Use when asked to run, start, or screenshot the app, or to confirm a change works in the real app (not just tests). First looks for a project skill that already covers launching the app; otherwise falls back to built-in patterns per project type (CLI, server, TUI, Electron, browser-driven, library). |
| `/run-skill-generator` | Hidden | [ ] | Author or improve the run-<unit> skill — a per-project skill that tells agents how to build, launch, and drive this project's app. Use when the user asks to set up the project, get it running, write run instructions, or verify build/run steps work from a clean environment. |
| `/security-review` | Hidden | [ ] | Complete a security review of the pending changes on the current branch |
| `/simplify` | Hidden | [ ] | Review the changed code for reuse, simplification, efficiency, and altitude cleanups, then apply the fixes. Quality only — it does not hunt for bugs; use /code-review for that. |
| `/team-onboarding` | Hidden | [ ] | Help teammates ramp on Claude Code with a guide from your usage |
| `/update-config` | Hidden | [ ] | Use this skill to configure the Claude Code harness via settings.json. Automated behaviors ("from now on when X", "each time X", "whenever X", "before/after X") require hooks configured in settings.json - the harness executes these, not Claude, so memory/preferences cannot fulfill them. Also use for: permissions ("allow X", "add permission", "move permission to"), env vars ("set X=Y"), hook troubleshooting, or any changes to settings.json/settings.local.json files. Examples: "allow npm commands", "add bq permission to global settings", "move permission to user settings", "set DEBUG=true", "when claude stops show X". For simple settings like theme/model, suggest the /config command. |
| `/usage` | Hidden | [ ] | Show session cost, plan usage, and what's contributing to your limits |
| `/usage-credits` | Hidden | [ ] | Configure usage credits or request them from your admin when you hit a limit |
| `/verify` | Hidden | [ ] | Verify that a code change actually does what it's supposed to by exercising it end-to-end and observing behavior — drive the affected flow, not just tests or typecheck. Run before committing nontrivial changes; bootstraps this repo's project verify skill if none exists yet. Don't invoke it on a diff that only touches tests, docs, or other code with no runtime surface to drive (a change to product source always has one) — there's nothing to observe. |
| `/workflow-launch-exec` | Hidden | [ ] | Execute a server-launched workflow handoff (workflow_launch event sessions only) |
