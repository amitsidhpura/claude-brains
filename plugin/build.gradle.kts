
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.amitsidhpura"
version = "0.3.3"

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
        // Shown on the Marketplace plugin page for the current version.
        changeNotes = """
            <b>0.3.3</b>
            <ul>
              <li><b>Auto mode changed meaning.</b> It now matches Claude Code's own Auto —
                  actions that pass a safety check are approved, anything risky still pauses.
                  It previously approved everything with no checks</li>
              <li>Modes match the official names — Manual / Edit automatically / Plan / Auto —
                  and switching between them no longer restarts the CLI or interrupts a reply</li>
              <li>The composer's focus border and Send button take the colour of the current
                  mode, so the panel shows how much it is allowed to do on its own</li>
              <li>"Always allow" is now a split button: the main half grants every rule the CLI
                  suggests, the arrow opens the list to grant just one</li>
              <li>Tables in replies render as tables — they used to print as raw pipe characters —
                  and match the code block styling</li>
              <li>Header icons no longer grow a background box on hover</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(21) // IntelliJ Platform 2024.2+ requires Java 21
}
