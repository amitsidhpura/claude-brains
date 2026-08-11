# State

## Current focus
**Post-0.4.0 UI polish, all shipped and verified 2026-08-12** — six user-reported items, each
raised from a screenshot and each measured before the renderer was touched: queue-row restyle,
tool-returned image sizing, todo strike colour, tool-line path shortening, the context gauge
ring, and conversation rename. Live harness **132 passed / 0 failed** on real JCEF;
`./gradlew test` green. NOT YET RELEASED — this is 0.4.0 + these changes, no version bump.

The defect register in `docs/manual-test.md` is untouched by this session and still stands at
**2 open ISSUE notes / 22 RESOLVED** — the open pair is 3.1 + 9.10, worked TOGETHER per the user.

## What shipped this session (all in one uncommitted batch)
- **Queue rows** wear the attachment chip's surface; the remove control is the shared `.rm`
  idiom (hover-revealed, absolute, fades over the text end); divider below the queue band.
- **Tool-returned images**: 120px cap, natural size below it, `align-items: flex-start` (the
  fix — see gotchas), 4px inset. Gallery sample is now over-cap + under-cap + bytes-dropped.
- **Todo strike** is `currentColor`, so it tracks the row instead of reading as a separate line.
- **Tool-line paths** are project-relative and clamped to one line: `.p-head` shrinks and
  ellipsises, `.p-tail` (filename + parent when they fit `RenderLimits.PATH_TAIL_MAX` = 40)
  never does. Absolute path rides `dataset.path`, so the CLICK target is never the truncated
  string — which also fixed a path past `DESC_MAX` being *opened* truncated. New wire frame
  `__project` (ChatPanel → webview), refreshed by `system/init`'s `cwd`. `SessionStore` now
  emits `fullPath` beside the capped `desc`.
- **Context gauge ring** in front of the percentage: SVG arc, `pathLength="100"` so the dash IS
  the percentage, `--ctx-pct` set beside the digits in `renderContext`. Lucide geometry (24
  viewBox, r=9, stroke 2, 19px box) so it matches the slash icon exactly.
- **Rename** (client-parity item 34's write half): header title, hover pencil, click the title
  or the pencil → the header becomes the editor. Enter saves, Esc cancels.
  `SessionStore.rename` appends the CLI's own `custom-title` record.

## Next steps
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing): fixture
      `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` is in place. `cmdKind`
      (chat.html) has no custom-command detection, so everything outside {clear, compact} is
      greyed as 'tui'. Constraint: the CLI's `commands` payload has NO type field.
- [ ] VFS refresh after CLI writes (backlog "Next up") — accepted edits still need "Reload from
      disk"; fix shape already worked out.
- [ ] Consider a release cut for this batch (version bump + `updatePlugins.xml` per
      `docs/release.md`); Plugin Verifier re-run only if new platform API usage lands.
- [ ] Optional polish offered and declined/not done: a filename longer than the whole line is
      hard-clipped with no ellipsis (rare, ~45+ chars at a narrow panel); the rename pencil is
      19px matching ✓/✕ — no open question, just noted.

## Test fixtures left in place (deliberate)
- `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` (sibling repo) — 3.1/9.10 re-test.
- `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/fa0e2185-….jsonl` — the session
  used for the live rename proof; its title is now "Renamed with the panel shut" and it carries
  two real `custom-title` records. Delete freely.
- The stitched synthetic sessions state.md used to list (`c7a2bf37…`, `b16da214…`, `afe39ca0…`)
  are GONE from disk, as is the whole `-home-syncroze-Sites-peers-woocommerce` project dir that
  held the 8.2/8.7 rate-limit donor. Rebuild if those measurements are needed again.

## Known gaps (deliberately left)
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: Accept / Accept all edits / Reject on a bar under the diff editor;
  panes still read-only (tweak-travel is the remaining v2 half, in backlog).
