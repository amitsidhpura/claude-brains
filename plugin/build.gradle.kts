
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.amitsidhpura"
version = "0.5.2"

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
        phpstorm("2024.2")

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
    systemProperty("ide.browser.jcef.debug.port", "9222")
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
 * own, not even a human at an upload form would catch it. Older versions stay listed while they are
 * what users are updating FROM.
 */
val changeNotesHtml = """
            <b>0.5.2</b>
            <ul>
              <li>Resuming a long conversation brings back its most recent turns. Past a certain
                  length the panel replayed the oldest part instead, so a thread could come back
                  looking days out of date; when one is too long to load whole, the top now says
                  how many earlier blocks were left out</li>
              <li>The Stop button is there whenever Claude is working — including the turn the CLI
                  starts on its own when a background command finishes, which used to print with
                  the button on Send and no way to interrupt it</li>
              <li>A background command no longer leaves the panel spinning. The turn ends with its
                  summary while the task chip keeps showing the command still running</li>
              <li>Tool calls that carry code, such as Playwright's browser_evaluate, show it in the
                  IN box instead of a cut-off fragment on the tool line</li>
              <li>Long tool descriptions stay on one line, with the full text on hover</li>
              <li>IN and OUT boxes match the permission card: the scrollbar sits on the border, and
                  a collapsed box no longer shows one</li>
              <li>Clicking outside the rename editor discards the edit, the same as Escape</li>
              <li>A Bash command is no longer repeated beneath its own IN box while a sub-agent
                  reports progress</li>
            </ul>
            <b>0.5.1</b>
            <ul>
              <li>Renaming a conversation sticks on long threads. The new name was saved correctly
                  but never read back, so the header kept showing the auto-generated title</li>
            </ul>
            <b>0.5.0</b>
            <ul>
              <li>Rename a conversation from the panel header — hover the title and click the
                  pencil. It writes the same record the CLI's own /rename does, so a name set here
                  and one set in the terminal are the same thing</li>
              <li>File paths on tool lines are project-relative and stay on one line, keeping the
                  filename readable in a narrow panel; clicking still opens the real file</li>
              <li>The context gauge gains a ring that fills as the window fills</li>
              <li>Queued messages match the attachment chips, and their remove control appears on
                  hover instead of taking up space</li>
              <li>Images returned by tools no longer stretch out of shape</li>
            </ul>
            <b>0.4.0</b>
            <ul>
              <li>MCP servers that need your shell's PATH (nvm, asdf) now start when the IDE was
                  launched from the desktop — they used to fail silently</li>
              <li>The conversation you are in can be deleted, not just past ones</li>
              <li>Removed a permanent gap above the composer's first row</li>
            </ul>
        """.trimIndent()

/**
 * A distributable zip must carry notes for the version it IS — docs/release.md step 1b, enforced.
 *
 * On `buildPlugin` only: a stale entry matters when something is about to be SHIPPED, and failing
 * every `runIde` mid-feature would just teach everyone to ignore it. It fires at release.md step 2,
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
        build again. See docs/release.md step 1b.
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
