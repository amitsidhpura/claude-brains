# Release process (Path B — custom plugin repository, no Marketplace)

Decided 2026-07-31: distribute via a custom plugin repository hosted on GitHub
(`amitsidhpura/claude-brains`), not the JetBrains Marketplace. Users add
`https://raw.githubusercontent.com/amitsidhpura/claude-brains/main/updatePlugins.xml`
under Settings → Plugins → ⚙ → Manage Plugin Repositories and get auto-updates.

The zip is hosted as a **GitHub Release asset** (keeps binaries out of git history;
release-asset URLs are stable and not behind the raw.githubusercontent CDN cache).
Only `updatePlugins.xml` lives in the repo — its few-minutes CDN cache just delays
update discovery, which is fine.

One remote: `origin` (`claude-brains`) — the old `claude-code-phpstorm` dev repo was
deleted 2026-07-31, so there is no second remote to push anymore.

## Versioning

Plain semver. The `v` prefix appears ONLY on git tags and the GitHub release title — every
place the IDE parses the version gets a bare number, because JetBrains compares plugin
versions as dotted numbers and a stray `v` breaks update detection.

| Where | Form (example) |
|---|---|
| Git tag | `v0.1.0` |
| GitHub release title | `Claude Brains v0.1.0` |
| `build.gradle.kts` → `plugin.xml` | `0.1.0` |
| IDE Plugins list (under the name) | `0.1.0` |
| Zip filename | `claude-brains-0.1.0.zip` |
| `updatePlugins.xml` `version=` | `0.1.0` |
| Release asset URL | `…/download/v0.1.0/claude-brains-0.1.0.zip` |

Progression: `0.1.1` for fixes with no new capability · `0.2.0` for features · `1.0.0` when
it is considered feature-complete and stable.

**An IDE only offers an update when the feed's version is HIGHER than the installed one.**
Re-uploading a changed zip under the same version silently reaches nobody who already
installed it (learned the hard way with the icon build). If a shipped version needs a fix,
bump the patch digit — and, pre-1.0, it is fine to `gh release delete --cleanup-tag` the old
one first so only a single release is ever listed.

**If a Marketplace upload is rejected (verification/moderation), the version number is burned** —
the Marketplace won't take the same number twice. Decided policy (2026-08-01): keep the standard
release order (release on GitHub first, then upload the same zip to the Marketplace); if the
Marketplace rejects it, fix and cut a normal bug-fix release (patch bump) through the same
process. Version gaps this leaves on the Marketplace are fine — it lists published versions only,
the IDE update dialog shows one version, and nobody diffs the sequence. Risk is low anyway:
`./gradlew verifyPlugin` runs the same engine + IDE list JetBrains runs, and "Compatible with
warnings" does NOT block publication (0.2.0 cleared moderation carrying warnings; updates to an
approved plugin generally publish without a human review pass).

## Cutting a release

1. Bump `version = "X.Y.Z"` in `phpstorm-plugin/build.gradle.kts`.
2. `cd phpstorm-plugin && ./gradlew test buildPlugin` → `build/distributions/claude-brains-X.Y.Z.zip`.
3. Sanity: `unzip -l` the zip — must contain ONLY our jar + open-source deps
   (never any Anthropic assets).
4. Update `updatePlugins.xml`: `version` and the `url`
   (`https://github.com/amitsidhpura/claude-brains/releases/download/vX.Y.Z/claude-brains-X.Y.Z.zip`).
5. Keep the README's **Install** section true: before the first release it says "no release is
   published yet"; from then on the custom-repo URL is the primary path.
6. Commit, tag, push:
   `git tag vX.Y.Z && git push origin main vX.Y.Z`.
7. `gh release create vX.Y.Z phpstorm-plugin/build/distributions/claude-brains-X.Y.Z.zip \
   --repo amitsidhpura/claude-brains --title "Claude Brains vX.Y.Z" --notes "..."`.
8. Verify: the asset URL returns 200 and `cmp`s equal to the local zip, and the feed served
   from `raw.githubusercontent.com` advertises the new version. IDEs pick it up within
   minutes (CDN cache) on their next plugin-update check.

## Release notes

**Title:** `Claude Brains vX.Y.Z` — nothing more. GitHub renders the title as the page
heading, so the body must NOT open with its own `#` H1; start straight at the tagline.

**Emoji go on section headings ONLY** — never on bullets. Bullets are short plain phrases,
one capability each.

Structure for the FIRST release (worked for 0.1.0):

```
<one-line what it is>

<two short punchy lines — what you no longer have to do>

<one paragraph — what it feels like to use>

## ✨ Features         — short plain bullets, one capability each
## 📦 Requirements     — IDE build, CLI installed AND logged in from a terminal
## 📥 Install          — custom-repo URL first, zip-from-disk second
## ⚠️ Notes            — early-release caveat + known gaps, honest and brief
---
<closing invite for feedback>
<unofficial / no bundled Anthropic code disclaimer>
```

Structure for UPDATE releases (0.2.0 onward) — same tagline/punchy opening, but the body
splits into what changed rather than re-listing every feature:

```
<one-line theme of the release>

<two short punchy lines — what you no longer have to do>

<one paragraph — what this release changes>

## ✨ New              — new capabilities only, short plain bullets
## 🐛 Fixes            — user-visible fixes, phrased as what now works (with the old
                         behaviour in a trailing dash-clause when it helps: "— used to X")
## 📥 Install / update — "already installed? the IDE offers X.Y.Z automatically" first,
                         then the custom-repo URL for new installs, zip-from-disk last
## ⚠️ Notes            — honest caveats specific to THIS release + the standing
                         requirements line (CLI logged in from a terminal, IDE build)
---
<closing invite for feedback>
<unofficial / no bundled Anthropic code disclaimer>
```

Internal work (docs, design pages, refactors, probes) stays OUT of release notes — users
only read about behaviour they can see.

**Accuracy rules — claims that have been wrong before:**

- **Slash commands** are an ALLOWLIST, not the CLI's full roster. Only `/compact` and
  `/clear` are enabled (see `docs/slash-commands.md`). Never write "slash commands" plain.
- **Diffs** normally render inside the permission card (line-numbered, in the chat panel).
  Real IDE diff tabs open only when Claude calls `openDiff`. Don't promise "IDE diff
  previews" as the default path.
- **Login** happens in a terminal — there is no in-IDE login. Always say so.
- Avoid vague claims like "full project awareness"; name the concrete capability
  (editor integration over the MCP bridge: open files, diagnostics, selection).
- Check every highlight against `docs/feature-checklist.md` before publishing.

## Identity

- Plugin id: `io.github.amitsidhpura.claude-brains` (never change after first install).
- Display name: **Claude Brains** — tagline "Claude Code for JetBrains IDEs (unofficial)".
- No syncroze references in the plugin (renamed 2026-07-31; packages are
  `io.github.amitsidhpura.claudebrains.*`).
- `since-build = 242`, `until-build` open.

## Marketplace listing (submitted 2026-07-31)

The plugin IS on the JetBrains Marketplace (vendor `amitsidhpura`), alongside the custom repo —
same plugin id, IDEs take the higher version from either source. Listing facts learned the
hard way:

- The **web Description editor stores Markdown**: pasted HTML gets converted, and source-wrapped
  lines become hard `<br>`s. Edit it with Markdown, one line per paragraph. `plugin.xml` keeps the
  HTML version (whitespace collapses there, so wrapping is fine) — keep the two in sync, since a
  new zip upload re-reads plugin.xml.
- The description carries an honest **"Current limitations"** list (dark-only UI, terminal login,
  no settings page, slash allowlist, in-chat diffs, no tabs, no auto-context). Update it when a
  limitation falls.
- Screenshots live in `design/marketplace/` (2400×1520 = 1200×760 @2x), composed from the real
  renderer by driving chat.html state-by-state.
- Uploads are manual via the web form; JetBrains signs Marketplace builds themselves. Run
  `./gradlew verifyPlugin` first (needs the `pluginVerifier()` dependency; IDE downloads are
  cached after the first run).
- All verifier warnings from the 0.2.0 run were resolved after submission (rides the next
  release): `DaemonCodeAnalyzerImpl.getHighlights` → `DocumentMarkupModel` +
  `HighlightInfo.fromRangeHighlighter` (public API, same data); the 8 `ReadAction.compute`
  sites → our `readLocked {}` helper (Threads.kt) wrapping `Application.runReadAction` — the
  ONE blocking-read API non-deprecated on every build 242 → 262 (Kotlin's `runReadAction {}`
  is ALSO deprecated on 2026.1, learned by re-running the verifier); and the
  `FileSaverDescriptor` ctor built via reflection — 2025.1+ deprecates the vararg ctor but
  its replacement doesn't exist on our 242 baseline, so reflection is the only way to be
  warning-free AND keep 2024.x support. Deprecation is per-IDE-version: the same zip shows
  more warnings on newer IDEs as APIs get deprecated there, never the reverse.

Resolved from the old pre-Marketplace checklist: LICENSE (MIT, repo root), vendor account,
`verifyPlugin` (clean on 2024.2 → 2026.2 EAP) — and the **name stayed "Claude Brains"**: several
third-party "Claude …" plugins live on the Marketplace unchallenged (Claude Code with GUI,
ClaudeMind, Claude Code Usage), so the trademark-leading-name concern was policy, not practice.
Worst case is a rename request later — a display-name edit, never the id. Still open: the
settings page (CLI path config) matters more now that strangers install this.
