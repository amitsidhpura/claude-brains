
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.amitsidhpura"
version = "0.18.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build/run against PhpStorm. Swap the version for one you have installed,
        // or use `local("C:/Program Files/JetBrains/PhpStorm 2024.2")` to point at your install.
        // 2024.2.6, not 2024.2: 2024.2.0's JCEF fabricates key-event storms in OSR on Linux
        // (IJPL-161111, fixed in 2024.2.2+) — the phantom-Enter bug of 2026-08-23 was this.
        phpstorm("2024.2.6")

        // JCEF + platform APIs come with the platform; nothing extra needed for the sidebar.
        instrumentationTools()
        // The Plugin Verifier CLI for `verifyPlugin` (IDE list in pluginVerification.ides).
        pluginVerifier()
        // No testFramework(TestFrameworkType.Platform): our tests are plain JUnit 5 over
        // SessionStore, which has no IntelliJ dependencies. The platform test framework registers
        // a JUnit LauncherSessionListener that cannot instantiate outside a real IDE test fixture,
        // which kills the test JVM at startup. Re-add it alongside actual platform tests.
    }

    // Tiny, dependency-free WebSocket server for the IDE-MCP bridge.
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    // JSON-RPC framing.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

/**
 * Remote DevTools for the webview, on every machine, with no per-sandbox setup:
 * `http://localhost:9222` (or `chrome://inspect`) once the chat panel is open, and
 * `tools/cdp.py` for scripted inspection.
 *
 * This was a manual Registry edit inside each sandbox, which is why it worked on one machine and
 * silently did nothing on the next — sandbox config lives in `build/idea-sandbox/`, is not in git,
 * and dies with the sandbox. Setting it here makes the port a property of the BUILD.
 *
 * Why a system property sets a registry key (verified in 2024.2 bytecode):
 * `SettingsHelper.getRemoteDebugPort()` reads `Registry.intValue("ide.browser.jcef.debug.port", -1)`,
 * and `RegistryValue._get` resolves user properties -> `System.getProperty(key)` -> bundled default.
 * The platform recommends system properties for anything read at early startup, which JCEF init is.
 * `-1` is the default and means off; the port is read once when CEF starts, so it is live from the
 * first panel open. A value set by hand in a sandbox Registry still WINS over this one — it is
 * checked first — so an old manual edit on a different port keeps overriding the line below.
 *
 * Sandbox only: `runIde` is a dev task, so no installed IDE ever opens this port.
 */
tasks.named<JavaExec>("runIde") {
    // Overridable so a sandbox can coexist with a real IDE that already owns 9222
    // (a hand-set Registry value in an installed IDE wins the port; seen 2026-08-16):
    // ./gradlew runIde -PjcefDebugPort=9223
    systemProperty("ide.browser.jcef.debug.port",
        providers.gradleProperty("jcefDebugPort").getOrElse("9222"))
}

/**
 * Print the replay blocks SessionStore produces for a real session, without launching the IDE:
 *   ./gradlew probe --args="/path/to/project <session-uuid>"
 * SessionStore has no IntelliJ dependencies, so it runs standalone.
 */
tasks.register<JavaExec>("probe") {
    group = "verification"
    description = "Dump replay blocks for a session: --args=\"<projectPath> <sessionId>\""
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.amitsidhpura.claudebrains.session.SessionProbeKt"
}

/**
 * The local escape hatch for an unreachable JetBrains host (see `pluginVerification.ides` below).
 * Declared HERE, above `intellijPlatform`, because a Kotlin build script executes top-down and
 * cannot reference a val declared later — the same reason `changeNotesHtml` sits up here.
 * `providers.gradleProperty` is configuration-cache-safe; a task action that touched it (or any
 * script-level val) would capture the build-script object and break the cache.
 */
val skipVerifierIdes = providers.gradleProperty("skipVerifierIdes").isPresent

/**
 * Release notes for THIS version, baked into plugin.xml and read straight off it by the Marketplace.
 *
 * Kept honest by the `buildPlugin` check below, because this string is easy to forget and nothing
 * downstream notices: it was last written for 0.3.3 and rode along unread through 0.4.0 and 0.5.0,
 * publishing the wrong notes twice. Now that .github/workflows/marketplace-upload.yml uploads on its
 * own, not even a human at an upload form would catch it. The LAST THREE versions stay listed
 * (decided 2026-08-29 — the block had grown to 14); everything older is one click away on the
 * GitHub releases page linked at the bottom, one entry per tag. When adding a version, drop the
 * oldest.
 */
val changeNotesHtml = """
            <b>0.18.0</b>
            <ul>
              <li>Pinned commands: pin a command on its row in the / menu and it becomes a chip
                  above the composer — one click sends it, or writes its name in front of your
                  draft when it takes an argument. Drag chips to reorder; pins are shared by all
                  projects</li>
              <li>Live output: while a command runs, what it has printed so far shows under it,
                  following the last lines, until the result replaces it</li>
              <li>Math: LaTeX in a reply is drawn as formulas, inline and display (KaTeX);
                  prices and code stay text</li>
              <li>Prompt suggestions: after a reply, Claude Code's guess at your next prompt
                  shows in the empty message box, marked "Suggestion" — Tab writes it in, nothing
                  is sent until you send it</li>
              <li>Workspace trust: when Claude Code does not trust the folder, a line above the
                  composer says so and offers Trust folder… with a confirmation</li>
              <li>The Thinking switch is shown locked on for models that cannot turn thinking
                  off</li>
              <li>Default in the model menu names the model Default really resolves to — it
                  could show the last model you had picked by name</li>
              <li>Prompt history: Up and Down recall prompts only from the first and last
                  character of the draft, so a long draft that wraps can be moved through row by
                  row</li>
              <li>Markdown: indented code blocks are drawn as code, a * or _ inside inline code
                  no longer starts italics elsewhere in the line, ~~~ fences work, and a code
                  fence opened mid-line no longer leaves stray characters</li>
              <li>The model name in the bar under the composer is cut with an ellipsis in a
                  narrow panel instead of wrapping onto more lines</li>
              <li>The @ menu lists files created since the panel opened</li>
              <li>A popup list that fits no longer shows a scrollbar at some display scales</li>
              <li>A warning line appears when Claude Code could not save part of the
                  conversation (disk full or quota used up)</li>
            </ul>
            <b>0.17.1</b>
            <ul>
              <li>Opening a file, an edit's review diff and the "Files changed · Review" diff from
                  the panel no longer uses an editor API that IntelliJ 2026.3 deprecates, so the
                  plugin stays ready for the next IDE versions. Nothing changes in how they look
                  or behave</li>
            </ul>
            <b>0.17.0</b>
            <ul>
              <li>Bookmarks: a bookmark button beside Copy response on every reply; the header
                  button opens a card above the composer listing them with their age — open one to
                  read the reply, jump to it in the conversation, or remove it. Kept per
                  conversation across restarts</li>
              <li>Save as command: hover a message you sent and save it as a /command for all
                  projects or this one — an ordinary command file, so the terminal and VS Code
                  have it too; ${'$'}ARGUMENTS takes the text typed after the name</li>
              <li>The / menu puts the commands you sent most recently first</li>
              <li>Copy image: images a tool returned (a screenshot, a PNG it read) get a copy
                  button on hover, and the image popup has one beside ×</li>
              <li>A question card's preview box keeps one height while you hover the options —
                  the list used to slide under the pointer</li>
              <li>Menus keep the cursor on the first match while you type with the mouse resting
                  over them, and show one highlight — typing /con could pick /goal</li>
              <li>Date lines are drawn as a soft wave</li>
            </ul>
            <p>Earlier versions: <a href="https://github.com/amitsidhpura/claude-brains/releases">github.com/amitsidhpura/claude-brains/releases</a></p>
        """.trimIndent()

/**
 * A distributable zip must carry notes for the version it IS — cxtify runbook.md § Release step 1b, enforced.
 *
 * On `buildPlugin` only: a stale entry matters when something is about to be SHIPPED, and failing
 * every `runIde` mid-feature would just teach everyone to ignore it. It fires at the release runbook's step 2,
 * before the tag and before the version number can be spent on a bad upload.
 */
tasks.named("buildPlugin") {
    // Locals, evaluated HERE at configuration time. A task action that reads a script-level `val`
    // (or `project`) captures the script object itself, which the configuration cache refuses to
    // serialize — "cannot serialize Gradle script object references". Closing over two plain
    // Strings is the whole fix.
    val entry = "<b>${project.version}</b>"
    val hasEntry = changeNotesHtml.contains(entry)
    val problem = """
        changeNotes has no entry for ${project.version} (looked for "$entry").
        The Marketplace shows these notes for the version being uploaded, and since the upload is
        automatic (.github/workflows/marketplace-upload.yml), nothing downstream catches a stale
        one — 0.4.0 and 0.5.0 both shipped 0.3.3's notes that way.
        Add this release's user-visible items to changeNotesHtml in plugin/build.gradle.kts, then
        build again. See cxtify runbook.md § Release step 1b.
    """.trimIndent()
    doFirst { if (!hasEntry) throw GradleException(problem) }
}

intellijPlatform {
    // We use no GUI forms and don't need @NotNull bytecode instrumentation.
    // Disabling it avoids the buggy :instrumentCode task in this plugin version.
    instrumentCode = false

    // No settings UI yet, so this only costs a headless IDE launch per build (~30s).
    // Re-enable when the deferred settings page lands.
    buildSearchableOptions = false

    pluginConfiguration {
        id = "io.github.amitsidhpura.claude-brains"
        name = "Claude Brains"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        // Shown on the Marketplace plugin page for the current version — see changeNotesHtml.
        changeNotes = changeNotesHtml
    }

    pluginVerification {
        ides {
            // `recommended()` resolves its IDE list over the NETWORK at CONFIGURATION time — the
            // JetBrains half from data.services.jetbrains.com, the Android Studio half from
            // jb.gg -> teamcity.jetbrains.com — so it runs for every task in the project, not just
            // `verifyPlugin`. When one of those hosts is unreachable, `test`, `runIde` and `probe`
            // all die after ~23s with a bare "Connection timed out: connect" that names neither the
            // URL nor the verifier, and `--offline` does NOT skip it (the value source ignores
            // offline mode). A stored configuration-cache entry hides this until something
            // invalidates the cache, so it appears to strike at random. Measured 2026-08-12:
            // teamcity.jetbrains.com unreachable for an afternoon, whole project unbuildable.
            //
            // -PskipVerifierIdes is the escape hatch for exactly that outage. It is guarded below,
            // because the failure mode it would otherwise introduce — a verifier run with no IDEs
            // to check against, passing vacuously — is worse than the outage it works around.
            if (!skipVerifierIdes) recommended()
        }
    }
}

// With no IDEs in the list the verifier checks nothing and reports success, which would turn the
// release gate into a rubber stamp on precisely the run that is supposed to catch binary
// incompatibility. Fail instead, and say how to get the real list back.
tasks.named("verifyPlugin") {
    val skip = skipVerifierIdes   // local: the doFirst lambda must not close over the script object
    doFirst {
        if (skip) throw GradleException(
            "verifyPlugin cannot run with -PskipVerifierIdes: the IDE list is empty, so it would " +
                "verify nothing and pass. Drop the flag (it exists only so `test`/`runIde` survive " +
                "an unreachable jb.gg / teamcity.jetbrains.com) and re-run once the host is back.",
        )
    }
}

kotlin {
    jvmToolchain(21) // IntelliJ Platform 2024.2+ requires Java 21
}
