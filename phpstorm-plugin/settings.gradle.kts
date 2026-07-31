plugins {
    // Lets Gradle auto-provision the JDK 17 toolchain (no manual JDK install needed).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "claude-brains"
