# Release process (Path B — custom plugin repository, no Marketplace)

Decided 2026-07-31: distribute via a custom plugin repository hosted on GitHub
(`amitsidhpura/claude-brains`), not the JetBrains Marketplace. Users add
`https://raw.githubusercontent.com/amitsidhpura/claude-brains/main/updatePlugins.xml`
under Settings → Plugins → ⚙ → Manage Plugin Repositories and get auto-updates.

The zip is hosted as a **GitHub Release asset** (keeps binaries out of git history;
release-asset URLs are stable and not behind the raw.githubusercontent CDN cache).
Only `updatePlugins.xml` lives in the repo — its few-minutes CDN cache just delays
update discovery, which is fine.

## Cutting a release

1. Bump `version = "X.Y.Z"` in `phpstorm-plugin/build.gradle.kts`.
2. `cd phpstorm-plugin && ./gradlew test buildPlugin` → `build/distributions/claude-brains-X.Y.Z.zip`.
3. Sanity: `unzip -l` the zip — must contain ONLY our jar + open-source deps
   (never any Anthropic assets).
4. Tag and push: `git tag vX.Y.Z && git push --tags`.
5. Create a GitHub Release for the tag on `amitsidhpura/claude-brains` and upload the zip
   as an asset (web UI, or `gh release create vX.Y.Z claude-brains-X.Y.Z.zip` once `gh`
   is installed). The asset URL must match what step 6 writes.
6. Update `updatePlugins.xml`: `version` and the `url`
   (`https://github.com/amitsidhpura/claude-brains/releases/download/vX.Y.Z/claude-brains-X.Y.Z.zip`).
7. Commit + push. IDEs pick up the new version within minutes (CDN cache) on their next
   plugin-update check.

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
