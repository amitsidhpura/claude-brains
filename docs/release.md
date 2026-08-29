# Release process (custom plugin repository + JetBrains Marketplace)

Originally "Path B", a custom plugin repository INSTEAD of the Marketplace (decided 2026-07-31) —
but the plugin was submitted to the Marketplace the same day and has been listed there since, so
both channels are live and carry the same plugin id; an IDE offers whichever advertises the higher
version. A release goes to GitHub first and the Marketplace upload follows automatically (step 10).

The custom repository is hosted on GitHub (`amitsidhpura/claude-brains`). Users add
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

**Never release with `-PskipVerifierIdes`.** That flag empties the verifier's IDE list so the rest
of the project still builds when `jb.gg` / `teamcity.jetbrains.com` is unreachable (the list is
resolved at configuration time, so one dead host otherwise stops `test` and `runIde` too — see
gotchas). `verifyPlugin` refuses to run under it rather than verifying nothing and reporting
success.

## Cutting a release

1. Bump `version = "X.Y.Z"` in `plugin/build.gradle.kts`.
1b. Update `changeNotesHtml` in the same file to THIS release's user-visible items. `plugin.xml` has
   no `<change-notes>` of its own, so the Gradle value is what gets baked into the zip and shown on
   the Marketplace version page. It went stale twice (0.4.0 and 0.5.0 both shipped 0.3.3's notes)
   back when a human watched every upload — and step 10 is automatic now, so nothing downstream
   would catch it either. **`buildPlugin` therefore refuses to build a zip whose notes carry no
   `<b>X.Y.Z</b>` entry for its own version** — step 2 fails with an explanatory message rather than
   producing a shippable zip. **Keep exactly the last THREE versions** (decided 2026-08-29; it had
   grown to 14) — add the new entry at the top, drop the oldest, and leave the closing
   "Earlier versions" link to the GitHub releases page, which has every tag's notes.
2. `cd plugin && ./gradlew test buildPlugin` → `build/distributions/claude-brains-X.Y.Z.zip`.
3. Sanity: `unzip -l` the zip — must contain ONLY our jar + open-source deps
   (never any Anthropic assets).
3b. **`./gradlew verifyPlugin` — MANDATORY on every release, no exceptions** (user's standing
   instruction, 2026-08-23). It runs the same engine and IDE ladder JetBrains runs, so it is the
   last chance to learn about a binary incompatibility BEFORE the version number is spent. Do not
   reason about whether the diff "touched platform API" and skip on that basis — the judgement is
   exactly what you cannot make reliably, and skipping it on 0.9.0 (which did pass, by luck
   confirmed afterwards) is why this step is numbered now.
   Read the VERDICT FILES, never the log tail (gotchas):
   `plugin/build/reports/pluginVerifier/PS-*/plugins/<pluginId>/X.Y.Z/verification-verdict.txt`
   — one line each, every IDE must read `Compatible`. It takes ~30s of verification inside a
   ~6min task on a warm cache, so run it in the background and wait. "Compatible with warnings"
   does not block a release, but read the warnings and record them. Never under
   `-PskipVerifierIdes` (see above — it refuses anyway).
4. Update `updatePlugins.xml`: `version` and the `url`
   (`https://github.com/amitsidhpura/claude-brains/releases/download/vX.Y.Z/claude-brains-X.Y.Z.zip`).
5. Keep the README's **Install** section true: before the first release it says "no release is
   published yet"; from then on the custom-repo URL is the primary path.
6. **APPROVAL GATE — never skip:** present the version number and the COMPLETE release notes to
   the user and wait for an explicit go. Steps 1–5 are local prep and fine to do proactively;
   nothing from step 7 on (commit of the bump, tag, push, GitHub release, Marketplace upload)
   happens until the user has seen the notes and said yes. Held for 0.2.0 and 0.3.0.
7. Commit, tag, push:
   `git tag vX.Y.Z && git push origin main vX.Y.Z`.
8. `gh release create vX.Y.Z plugin/build/distributions/claude-brains-X.Y.Z.zip \
   --repo amitsidhpura/claude-brains --title "Claude Brains vX.Y.Z" --notes "..."`.
9. Verify: the asset URL returns 200 and `cmp`s equal to the local zip, and the feed served
   from `raw.githubusercontent.com` advertises the new version. IDEs pick it up within
   minutes (CDN cache) on their next plugin-update check.
10. **Marketplace: nothing to do.** `.github/workflows/marketplace-upload.yml` fires on
   `release: published` and uploads the asset step 8 just attached — the same bytes step 9 proved
   equal to the local zip. Confirm it: `gh run list --workflow=marketplace-upload.yml --limit 1`
   is green, and the plugin page lists X.Y.Z.
   Red run, or CI unavailable? Nothing is lost — a FAILED upload does not burn the version (see
   above; only an accepted-then-rejected moderation does). Fix and re-run:
   `gh workflow run marketplace-upload.yml -f tag=vX.Y.Z -f dry_run=false`, or fall back to the web
   form. `dry_run` defaults to true, so a manual dispatch never uploads unless you say so.

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

- **Slash commands** are an ALLOWLIST, not the CLI's full roster: sixteen verified built-ins plus
  the panel's own `/btw`, and auto-enabled project/user commands, skills and MCP prompts (see
  `docs/slash-commands.md`). `/clear` was removed 2026-08-29. Never write "slash commands" plain.
- **Diffs** normally render inside the permission card (line-numbered, in the chat panel).
  Real IDE diff tabs open only when Claude calls `openDiff`. Don't promise "IDE diff
  previews" as the default path.
- **Login** happens in a terminal — there is no in-IDE login. Always say so.
- Avoid vague claims like "full project awareness"; name the concrete capability
  (editor integration over the MCP bridge: open files, diagnostics, selection).
- Check every highlight against `docs/feature-checklist.md` before publishing.

## Identity

- Plugin id: `io.github.amitsidhpura.claude-brains` (never change after first install).
- Display name: **Claude Brains**.
- Slogan: **"Develop in the IDE. Configure in the Terminal."** — opens the description
  everywhere (plugin.xml, README, updatePlugins.xml, Marketplace) and renders in-product on the
  welcome screen (`.w-slogan`). It is also the scope rule — see `.claude/context/overview.md`
  § Philosophy (the root `CLAUDE.md` it used to live in was migrated there 2026-08-07).
- Descriptive tagline, one line under it: "Claude Code for JetBrains IDEs (unofficial)".
- No syncroze references in the plugin (renamed 2026-07-31; packages are
  `io.github.amitsidhpura.claudebrains.*`).
- `since-build = 242`, `until-build` open.

## Marketplace listing (submitted 2026-07-31)

The plugin IS on the JetBrains Marketplace (vendor `amitsidhpura`), alongside the custom repo —
same plugin id, IDEs take the higher version from either source. Listing facts learned the
hard way:

- **The listing's Description comes from `plugin.xml`**: the user set the Marketplace option to take
  the description from the plugin for future releases (stated 2026-08-29), so a zip upload refreshes
  it and there is no hand-synced web copy. Edit ONLY `plugin/src/main/resources/META-INF/plugin.xml`
  (HTML; whitespace collapses, wrapping is fine). Confirm on the next upload that the page shows the
  new text — if it ever doesn't, the web editor stores Markdown and source-wrapped lines become hard
  `<br>`s, so paste one line per paragraph.
- The description splits its honest list in TWO, and they must not blur:
  **"By design"** (terminal login, no settings page, slash allowlist) states the philosophy —
  those never move; and **"Not there yet"** (dark-only UI, in-chat diffs, no tabs, no
  auto-context) is the real gap list — update it when one falls.
- Screenshots live in `design/marketplace/` (2400×1520 = 1200×760 @2x), regenerated by
  `python3 tools/marketplace_shots.py` (headless Chrome over the spliced chat.html; scenes are
  scripts against the real builders — edit them there, never retouch a PNG). Reshot 2026-08-29:
  01 conversation · 02 plan + questions · 03 control · 04 commands/agents/side question · 05
  sessions + models. Uploading them is a MANUAL step on the Marketplace edit page — the zip carries
  no screenshots. Originally composed from the real
  renderer by driving chat.html state-by-state.
- Uploads are automatic since 2026-08-12 (step 10): the `marketplace-upload` workflow re-posts the
  published GitHub asset to `https://plugins.jetbrains.com/api/updates/upload` (`xmlId` + `file`,
  bearer token in the `JETBRAINS_MARKETPLACE_TOKEN` repo secret, no `channel` field = Stable). The
  zip goes up UNSIGNED exactly as the web form sent it — JetBrains signs Marketplace builds
  themselves, and this project configures no signing key, so nothing about the artifact changed.
  **CI does not verify, it only ships** — which is why step 3b runs `./gradlew verifyPlugin`
  locally on every release (needs the `pluginVerifier()` dependency; IDE downloads are cached
  after the first run). The Marketplace also runs its own verifier on upload, but that verdict
  lands AFTER the version number is spent, so it is a confirmation, never the gate.
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
