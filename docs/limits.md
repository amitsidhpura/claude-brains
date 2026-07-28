# Size limits and caps

Every place the UI or the parser bounds something, and by how much. Written 2026-07-29.

Three different kinds of bound, which matter differently:

- **Clamped** — collapsed behind a "show more" tab; nothing is lost.
- **Scrolled** — height is capped, content is intact and reachable.
- **Truncated** — data is actually dropped and cannot be recovered without reopening the source.

---

## Clamped (collapse + tab, fully recoverable)

Long blocks collapse to a fade with a tab on the block's bottom border rather than getting their
own scrollbar — a nested scroll area inside `#log` traps the mouse wheel.

| block | limit | fade colour | set in |
|---|---|---|---|
| user message `.msg-user` | 12 lines | `--user-bg` | `chat.html` `addUserMessage` |
| permission card command `.card .cmd` | 14 lines | `--code-bg` | `chat.html` `renderPermission` |
| code blocks `.codeblock pre` | 16 lines | `#202226` | `chat.html` `clampCode` |

Line height is `1.5em`, so the pixel height is `lines × 1.5em`. `clampBlock` measures after a frame
and removes the affordance entirely when the content fits, so short blocks get no tab and no fade.

## Scrolled (height capped, content intact)

| block | limit |
|---|---|
| Bash IN/OUT `.io-v` | 200px |
| diffs `.diff` | 240px |
| history list `.hist-list` | 340px |
| slash-command menu `#slashList` | 320px |
| composer textarea `#input` | 200px (~10 lines) |

## Truncated (data is dropped)

| what | limit | where |
|---|---|---|
| diff rows | 400 | `MAX_DIFF_ROWS`, live + replay; appends "… diff truncated" |
| Bash command | 2000 chars | `chat.html` + `SessionStore.toolItem` |
| Bash output | 2000 chars | `chat.html` + `SessionStore.applyToolResult` |
| card command preview | 4000 chars | `previewHtml` |
| tool description | 140 chars | `chat.html` + `SessionStore.toolItem` |
| session title fallback | 80 chars | `SessionStore.titleOf` |

## Volume (how much is loaded at all)

| what | limit | note |
|---|---|---|
| replay blocks per session | 4000 | `readTranscript(max)`. A 5.7 MB session yields ~876, so rarely reached. |
| transcript images | 4 MB of base64 | `IMAGE_BUDGET`; past it, chips render name-only. The whole transcript ships as one `executeJavaScript` string. |
| history list | 40 sessions | `SessionStore.list(limit)` |
| title scan | first 400 lines | `titleOf` |
| @-mention file index | 3000 files | `ClaudeSessionService.listProjectFiles` |
| @-mention suggestions shown | 20 | |
| slash-command suggestions shown | 40 | |

---

## Known rough edges

**Duplicated constants.** The 2000-char Bash caps and the 140-char description cap exist twice —
once in `chat.html` for the live path and once in `SessionStore.kt` for replay. They are the same
number in two languages with nothing tying them together, so live and replayed renderings of the
same tool call can silently diverge if only one side is changed. The `description → file_path →
path → pattern → query → url` chain has the same problem and carries a comment on both sides.

**Preview shows more than the transcript keeps.** The permission card previews 4000 chars of a
command, but only 2000 are stored for the IN box — so after approving, the record of what ran is
shorter than what was approved.

**Token/size scans are cached, not free.** `SessionStore.tokensOf` scans the whole transcript to
sum `message.usage.output_tokens`, rejecting lines on a substring before parsing. Cached by
`(mtime, size)`, so a 177 MB session is scanned once (~0.5s) rather than on every panel open.
`lastActivityOf` reads only the trailing 256 KB.
