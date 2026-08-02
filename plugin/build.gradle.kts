
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.amitsidhpura"
version = "0.3.1"

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
            <b>0.3.1</b>
            <ul>
              <li>Quoted text in replies renders as a quote, with any list inside it kept as
                  a list — it previously showed the raw &gt; and - characters</li>
              <li>Folded blocks fade the same way everywhere — a long command in a permission card
                  no longer looks cut off compared with the same text in a Bash IN box, and a
                  folded command keeps all three lines instead of losing one to a scrollbar</li>
              <li>The permission card and the record of what ran now show the same 4000 characters
                  of a command; the card previously previewed more than was kept</li>
              <li>History and slash-command menus no longer show a scrollbar for exactly five
                  items that visibly fit</li>
              <li>The delete button in the history list no longer overlaps the conversation title</li>
              <li>Tool lines keep their description on resume when the tool sends a blank one —
                  live and replayed sessions now render identically</li>
              <li>Long conversations report a more honest scrollbar</li>
              <li>Welcome screen shows the running version; the model chip has an icon</li>
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
