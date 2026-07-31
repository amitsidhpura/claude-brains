# Colors — what we use and where

Inventory of every colour in the plugin, written 2026-07-31 as the groundwork for a **light
theme** and for making colours configurable. Two audiences: whoever builds theming next, and
anyone touching the CSS who needs to know which token to reach for.

Source of truth is the `:root` block at the top of
`phpstorm-plugin/src/main/resources/webview/chat.css`. `design/mockup.html` links that same file,
so the mockup and the shipped UI can never drift on colour.

**Rule: no new hardcoded colours.** Add a token to `:root` and use `var(--x)`. The 24 hardcoded
values still in the stylesheet (listed at the end) are the debt a theme has to clear first.

---

## Token reference

### Surfaces

| Token | Value | Used for |
|---|---|---|
| `--bg` | `#1a1a1a` | panel background — the page itself |
| `--panel` | `#26282c` | input card, popups (model/mode/history/slash menus) |
| `--raised` | `#2a2d33` | chips, inline code, small raised surfaces |
| `--raised-hover` | `#31343a` | hover state of the above |
| `--hover` | `#2f3237` | list rows and icon buttons on hover |
| `--code-bg` | `#141414` | dark code surfaces: IN/OUT boxes, diffs, commands, code-block bodies |

### Text

| Token | Value | Used for |
|---|---|---|
| `--fg` | `#d5d8dd` | main text |
| `--muted` | `#8b9096` | secondary text |
| `--dot` | `#6f7378` | thinking dots, IN/OUT labels |

### Lines

| Token | Value | Used for |
|---|---|---|
| `--border` | `#3a3d43` | subtle borders |
| `--border-strong` | `#45494f` | card and popup borders |

### Brand and semantics

| Token | Value | Used for |
|---|---|---|
| `--accent` | `#d97757` | **Claude orange** — send button, focus ring, ask-card tints, welcome logo |
| `--accent-rgb` | `217, 119, 87` | the same colour as channels for `rgba()` tints — **keep in sync with `--accent`** |
| `--green` | `#4ec26b` | tool dot (success) |
| `--red` | `#f2827f` | error text and the failed-tool dot |
| `--blue` | `#4daafc` | links |
| `--amber` | `#d7a35f` | inline code |

### User surfaces (the blue family)

Everything the *user* wrote — message boxes and the composer — sits on a cool blue surface so it
reads as distinct from Claude's output.

| Token | Value | Used for |
|---|---|---|
| `--user-bg` | `#1b2129` | user message box + composer background |
| `--user-border` | `#374a63` | their outer border |
| `--user-raised` | `#242e3c` | capsules on user surfaces (attachment chips) |
| `--user-raised-hover` | `#2c394c` | hover state of the above |
| `--user-divider` | `#2d3c54` | internal borders on user surfaces |
| `--user-muted` | `#8ba3c7` | secondary text/icons on user surfaces |

### Feedback surfaces (the warm family)

One shared frame for everything **waiting on the user**: permission cards, plan cards, question
cards. Deliberately warm so a pending decision is visually distinct from ordinary output.

| Token | Value | Used for |
|---|---|---|
| `--warn-border` | `#6b4a3c` | frame of cards needing feedback |
| `--warn-bg` | `#282320` | their background |

### Action buttons

| Token | Value | Used for |
|---|---|---|
| `--ok` | `#0d542b` | confirm buttons (Accept / Submit) |
| `--ok-hover` | `#146e3a` | hover |
| `--danger` | `#6e1f1f` | destructive confirm (Delete) — solid, unlike the light `--red` |
| `--danger-hover` | `#8b2626` | hover |

---

## Colours outside the stylesheet

**Plugin icons** (`src/main/resources/icons/`) are SVG files with baked-in fills, because the IDE
renders them, not the webview:

| File | Colour | Used for |
|---|---|---|
| `claude.svg`, `claude_dark.svg` | `#ced0d6` | tool-window stripe icon, idle |
| `claude_white.svg` | `#ffffff` | stripe icon while the tool window is open |
| `META-INF/pluginIcon.svg` | `#D97757` tile + `#FFFFFF` mark | plugin listing in Settings → Plugins |

The stripe icons are swapped in code (`ClaudeToolWindowFactory.ICON_IDLE` / `ICON_SELECTED`)
because the platform doesn't recolour them on selection.

**Welcome logo** — `chat.html` inlines the mark with `#d97757` hardcoded six times (once per
path). A theme either needs those switched to `currentColor` with the colour set in CSS, or the
whole block regenerated.

**Kotlin** — no colours at all. Nothing in `src/main/kotlin/` hardcodes a colour, so a theme is
purely a webview + icons concern.

---

## What blocks a light theme today

24 hardcoded colours remain in `chat.css` outside `:root`. They must become tokens before a light
theme is possible — each one is a spot that would stay dark on a light background:

- **Scrollbars** — `#3b3d44` thumb, `#4a4d55` hover (both `scrollbar-color` and
  `::-webkit-scrollbar-thumb`)
- **Syntax highlighting** — `.hl-k` `#c586c0`, `.hl-s` `#ce9178`, `.hl-c` `#6a9955`,
  `.hl-n` `#b5cea8`, `.hl-v` `#9cdcfe` (a VS Code-derived palette; a light theme needs a second
  set, and these are the most visible offenders)
- **Card and code-header surfaces** — `.card` `#232528`, `.codeblock .cb-h` `#202226`
- **Secondary buttons** — `.no` `#3a3d41` / `#474a4f` hover
- **Error block** — `#6b3a3a` border, `#241a1a` background
- **Send button** — `#e28a6b` hover, and the stop state `#c9403a` / `#d9534d`
- **Ask card text** — `#e4bda6` on question descriptions, footer, tabs
- **Effort slider dots** — `#4a4d52`, `#5a5d63` hover
- **Shimmer gradients** — thinking `#797d84 → #eceff4`, generating `#c2603d → #f2b492`
- **Attachment placeholder** — `linear-gradient(135deg, #35486b, #6b3555)`
- **Shadows and scrims** — several `rgba(0,0,0,.35–.55)`; a light theme wants far lighter shadows
- **`color: #fff`** on `--ok` / `--danger` / `#send` button labels

## Notes for whoever builds theming

- **`--accent-rgb` must track `--accent`.** They're separate tokens only because CSS can't
  derive channels from a hex; a theme that changes one and not the other desyncs every tint.
- **Two surface families carry meaning**, not decoration: blue = the user's own input, warm =
  awaiting the user's decision. A light palette must preserve that contrast relationship, not
  just lighten each value independently.
- **`--code-bg` is deliberately darker than `--bg`**, so code recedes rather than floats. In a
  light theme it should be *lighter* than the page or the relationship inverts.
- **Where a light theme could come from:** the IDE exposes its own theme to the webview, so the
  cleanest route is reading the JCEF-hosted look-and-feel and setting a `data-theme` attribute on
  `<html>`, with `:root[data-theme="light"]` overriding the tokens. That keeps a single stylesheet
  and lets the mockup exercise both by toggling one attribute.
- **User-configurable colours** would ride the deferred settings page: persist overrides and
  inject them as inline `:root` custom properties at panel load, so they beat the stylesheet
  without editing it.
