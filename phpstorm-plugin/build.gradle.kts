
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.syncroze"
version = "0.1.0"

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
    mainClass = "com.syncroze.claudecode.session.SessionProbeKt"
}

intellijPlatform {
    // We use no GUI forms and don't need @NotNull bytecode instrumentation.
    // Disabling it avoids the buggy :instrumentCode task in this plugin version.
    instrumentCode = false

    // No settings UI yet, so this only costs a headless IDE launch per build (~30s).
    // Re-enable when the deferred settings page lands.
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.syncroze.claude-code"
        name = "Claude Code (Syncroze)"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21) // IntelliJ Platform 2024.2+ requires Java 21
}
