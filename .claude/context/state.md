# State

## Current focus
**Unreleased work in the tree, all verified: the slash-command surface is finished.** Since 0.6.0
(2026-08-14) three things landed on 2026-08-15: custom commands in the `/` menu (3.1 + 9.10), an
IDE-development set of 16 enabled commands, and the local-command rendering fixes those exposed.
Defect register: **0 open / 27 resolved**. Next real step is a version bump.

## The slash-command surface, as it now stands
- **Custom commands auto-enable from a wire marker, not a disk scan.** The CLI suffixes every
  custom entry's description with " (project)"/" (user)"; `markCustom()` (chat.html) strips it into
  `c.src` + the `customCmds` map, `cmdKind` returns 'text' for map hits and `mcp__` names, and
  `.pi-src` renders the badge. Native/allowlist are checked FIRST so a custom `clear.md` cannot
  shadow `/clear`. No Kotlin in the detection path.
- **16 built-ins enabled** (`CMD_ALLOWED` in chat.html), grouped in docs/slash-commands.md by
  IDE-development relevance. `/usage` stays Hidden — that is the 2026-08-06 *declined* decision,
  not an omission.
- **The CLI watches the PROJECT commands dir** (drop ≈2.5s, delete ≈1s → `commands_changed`, no
  turn needed); `~/.claude/commands` is NOT watched → `/reload-skills` is the manual lever.

## Three rendering rules learned the hard way (all live-path, all fixture-pinned in 47)
1. **A local built-in's answer is a bare whole-message `assistant` frame with ZERO stream events.**
   Rendering was delta-driven, so `/context` printed "Puttered for 1s" and nothing else.
2. **A local built-in that FAILS reports through a `user` frame whose `content` is a STRING**
   (`<local-command-stderr>…`). `onUserEvent` dropped it at `!Array.isArray(content)`, so
   `/security-review` showed a completed turn with nothing in it.
3. **The CLI emits an `assistant` frame PER CONTENT BLOCK, not per message.** So `msgStreamed` is a
   TURN-level fact — set at `message_start`, cleared ONLY at result/sendTurn/clearLogUI. Clearing it
   on the first assistant frame double-rendered every message that thinks first; clearing it at
   `message_stop` double-renders the other way (frames straddle the stop in both directions).
Replay spells all of this differently again — see gotchas.

## Testing — the standing setup
- Live harness: `python tools/live_harness.py` needs `./gradlew runIde` up + tool window open.
  Baseline **256/256**; `./gradlew test` **103**. Wire fixtures need a `.turn-body` via the
  per-step `setup` hook. **Harness runs leave state in the live panel** (fixture 46 ends on an
  empty roster) — reload the webview before eyeballing it.
- **The command-sweep harness is the tool for "does command X render":** scratchpad
  `sweep.py`/`runner.py`/`matrix.py` (not committed). Its one non-obvious part is a **wire tape**
  that monkey-patches `onClaudeEvent` and wraps the original in try/catch — ChatPanel calls the
  handler inside `executeJavaScript`, so a throw there is swallowed by JCEF and the frame vanishes
  silently. Without the tape a blank turn cannot be told apart from "the CLI sent nothing", which
  is the whole diagnosis. Read `busy` directly (all chat.html globals are reachable over CDP).
- Manual re-test fixtures live in `~/Sites/claude-brains-testing/.claude/commands/`:
  `dummy-cmd.md` + `sub/nested-cmd.md`. **That repo is disposable** (user, 2026-08-15) — reset with
  `git reset --hard 64fa85a && git clean -fdxq -e vendor -e .idea`, then recreate those two files.

## Next steps
- [ ] **Release the next version** (`docs/release.md`). New since 0.6.0: custom-command menu,
      the 16-command enabled set, and the three rendering fixes above. `changeNotesHtml` in
      `plugin/build.gradle.kts` must be updated or `buildPlugin` refuses.
- [ ] Then backlog order: reloaded-webview log replay, kill-background-process from the panel,
      editor accept/reject v2 tweak-travel.

## Known gaps (deliberately left)
- `/batch`'s fan-out verified at **N=2**, not 5–30 (the sandbox had two class files). Machinery is
  the same at any N; only the background roster would carry more rows.
- Everything was swept against ONE sandbox project (a PHP CLI repo). **`/run` branches on project
  type** and never had to start a server here.
- The queue path (`.q-row`) test in the sweep was inconclusive — the first turn finished before the
  second submit landed, so nothing queued.
- A webview reload replays the INITIALIZE-time roster, so mid-session commands vanish until the
  next `commands_changed` (backlog).
- The **mcp** source badge is fixture-pinned only — no MCP server with prompts on this machine.
- `aliases` is display-only: the menu filter does not match them.
- Sub-agent WORK outcome not surfaced and cannot reliably be; reloaded webview loses its log; no
  way to kill a background process from the panel; `prefers-reduced-motion` unverified against the
  real OS setting; sidechain replay ordering untested; editor tweak-travel is the remaining v2 half.

## Which machine — check FIRST, both are real
2026-08-15 ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The slash-menu fixtures exist on Linux only.
