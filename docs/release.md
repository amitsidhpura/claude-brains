# Release process (Path B — custom plugin repository, no Marketplace)

Decided 2026-07-31: distribute via a custom plugin repository hosted on GitHub
(`amitsidhpura/claude-brains`), not the JetBrains Marketplace. Users add
`https://raw.githubusercontent.com/amitsidhpura/claude-brains/main/updatePlugins.xml`
under Settings → Plugins → ⚙ → Manage Plugin Repositories and get auto-updates.

The zip is hosted as a **GitHub Release asset** (keeps binaries out of git history;
release-asset URLs are stable and not behind the raw.githubusercontent CDN cache).
Only `updatePlugins.xml` lives in the repo — its few-minutes CDN cache just delays
update discovery, which is fine.

Two git remotes carry the same `main`: `origin` (`claude-code-phpstorm`, the original dev
repo) and `brains` (`claude-brains`, the public face). **Releases exist only on `brains`.**
Push both on every release commit.

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

## Cutting a release

1. Bump `version = "X.Y.Z"` in `phpstorm-plugin/build.gradle.kts`.
2. `cd phpstorm-plugin && ./gradlew test buildPlugin` → `build/distributions/claude-brains-X.Y.Z.zip`.
3. Sanity: `unzip -l` the zip — must contain ONLY our jar + open-source deps
   (never any Anthropic assets).
4. Update `updatePlugins.xml`: `version` and the `url`
   (`https://github.com/amitsidhpura/claude-brains/releases/download/vX.Y.Z/claude-brains-X.Y.Z.zip`).
5. Keep the README's **Install** section true: before the first release it says "no release is
   published yet"; from then on the custom-repo URL is the primary path.
6. Commit, tag, push BOTH remotes:
   `git tag vX.Y.Z && git push origin main vX.Y.Z && git push brains main vX.Y.Z`.
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

Structure that worked for 0.1.0:

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

## If this ever goes to the Marketplace

Revisit first: the name embeds Anthropic's "Claude Code" mark (reorder or rename, e.g.
"Code Bridge for Claude"), add a LICENSE, plugin signing (`signPlugin`), vendor account,
`verifyPlugin` against current IDE releases, and the deferred settings page (CLI path
config) becomes mandatory.
