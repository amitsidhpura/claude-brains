# Plugin Verifier matrix — widening beyond PhpStorm (PARKED, decision pending)

Status 2026-08-01: researched, NOT implemented. `verifyPlugin` still checks PhpStorm only.
Blocked on one decision (breadth, below); everything else here is verified groundwork so the
implementation is a 20-minute job once decided.

## Why

`./gradlew verifyPlugin` follows the declared platform (`phpstorm("2024.2")`), so
`ides { recommended() }` resolves to PhpStorm only — the latest build of each branch in the
compatibility range (7 today: PS-242 … PS-262). But the plugin is platform-only
(`com.intellij.modules.platform` + optional JCEF), so the Marketplace lists it for EVERY
IntelliJ-family IDE, and JetBrains' post-upload verification runs cross-product. A rejection
there burns the version number (docs/release.md). A wider local matrix means that check can't
surprise us with a product we never looked at.

## Decisions

- **Products (DECIDED): the major coding IDEs** — IntelliJ IDEA Community, WebStorm,
  PyCharm Professional, PyCharm Community, GoLand, Rider, CLion, RubyMine, DataGrip,
  DataSpell, RustRover, Aqua (+ PhpStorm, which keeps its full ladder).
  Skipped deliberately: IDEA Ultimate (IC is the identical platform), Android Studio
  (separate release cadence, JCEF often absent, known matrix flake source),
  Gateway/MPS/Writerside/Fleet (not real targets).
- **Breadth (PENDING — the parked decision):**
  | Option | IDEs | New download | Per-run wall clock |
  |---|---|---|---|
  | Edges: others at 242 + newest branch, PS full ladder | ~25 | ~20 GB | ~30–60 min |
  | Latest only: others at newest branch, PS full ladder | ~19 | ~13 GB | fastest |
  | Full matrix: everything × every branch | ~70–90 | ~80 GB | 2–4 h EVERY run |

  The two failure axes: API drift over time (covered by the PS ladder alone) and
  product-specific module gaps, e.g. JCEF leaving core in 2025.x (covered by checking other
  products at all — the range EDGES catch both the old-baseline and current shape).
  Recommendation on file: **edges**. Full matrix is mostly redundant — products share the
  platform APIs within a branch — and it taxes every future release's verify run.

## Verified facts (2.1.0 DSL, checked via javap against the cached plugin jar — don't re-derive)

- `pluginVerification.ides` supports `recommended()`, `ide(type, version)`, and
  `select { types / channels / sinceBuild / untilBuild }`
  (`ProductReleasesValueSource.FilterParameters`; properties are Gradle
  `ListProperty`/`Property`, so `.set(...)` may be needed if plain `=` fails at sync).
- `ProductRelease.Channel`: RELEASE, RC, EAP (+ others). **Include EAP + RC**: the current
  `recommended()` ladder contains an EAP build (PS-262.9437.67), so RELEASE-only channels
  silently drop the newest branch.
- Exact enum names: `IntellijIdeaCommunity`, `WebStorm`, `PyCharmProfessional`,
  `PyCharmCommunity`, `GoLand`, `Rider`, `CLion`, `RubyMine`, `DataGrip`, `DataSpell`,
  `RustRover`, `Aqua`, `PhpStorm` (in `org.jetbrains.intellij.platform.gradle.IntelliJPlatformType`).
- Cost basis: ~1.1 GB per IDE build (7 cached PhpStorm builds = 7.9 GB under
  `~/.gradle/caches/modules-2/files-2.1/webide/PhpStorm`), verification ~1–3 min per IDE,
  341 GB free on this machine — disk is not the constraint, wall clock is.
- A product that has no build in a selected range just resolves to nothing (RustRover/Aqua
  didn't exist at 242) — harmless, but eyeball the resolved IDE list on the first run.

## Config sketch (edges variant; drop into build.gradle.kts when unparked)

```kotlin
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

pluginVerification {
    ides {
        recommended()   // PhpStorm: latest build of every branch in the range (API-drift axis)
        val others = listOf(
            IntelliJPlatformType.IntellijIdeaCommunity, IntelliJPlatformType.WebStorm,
            IntelliJPlatformType.PyCharmProfessional, IntelliJPlatformType.PyCharmCommunity,
            IntelliJPlatformType.GoLand, IntelliJPlatformType.Rider, IntelliJPlatformType.CLion,
            IntelliJPlatformType.RubyMine, IntelliJPlatformType.DataGrip,
            IntelliJPlatformType.DataSpell, IntelliJPlatformType.RustRover,
            IntelliJPlatformType.Aqua,
        )
        val allChannels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.RC,
            ProductRelease.Channel.EAP)
        select { types = others; channels = allChannels; sinceBuild = "242"; untilBuild = "242.*" }
        select { types = others; channels = allChannels; sinceBuild = "262"; untilBuild = "262.*" }
        // Full matrix instead: one select spanning sinceBuild "242" -> untilBuild "262.*".
        // NB: the newest-branch numbers ("262") need a manual bump when a new branch ships;
        // recommended() follows the range automatically, select{} does not.
    }
}
```

## When implementing

1. `./gradlew verifyPlugin --dry-run` first — catches DSL errors without downloads.
2. First real run downloads the new IDEs (~20 GB for edges); confirm the resolved IDE list
   matches expectations and note products that resolved to nothing.
3. Existing PhpStorm results must stay clean. NEW warnings from other products are the point —
   surface them for a decision (fix vs document vs untilBuild) rather than silently patching.
4. Update docs/release.md (verifyPlugin bullet: matrix description + first-run download note)
   and CLAUDE.md's Plugin Verifier hygiene line to match the real result.
