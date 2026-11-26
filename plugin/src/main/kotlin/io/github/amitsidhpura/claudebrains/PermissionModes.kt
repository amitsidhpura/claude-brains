package io.github.amitsidhpura.claudebrains

/**
 * The persisted-mode rule behind the spawn flag, pure so it can be pinned without a
 * PropertiesComponent (checklist 4.5 / 4.7).
 */
object PermissionModes {
    /**
     * `manual` when the project never picked a mode (2026-11-26, the user's choice): the flag is
     * always sent and beats the user's `permissions.defaultMode`, so a new project never starts in
     * Auto or in another project's pick. Until then nothing picked meant no flag (4.7, as VS Code).
     *
     * A stored value is migrated to the CLI's current vocabulary (2.1.220): `default` is no longer
     * advertised (it still parses, but `manual` replaced it), and what this plugin once labelled
     * "Auto mode" was `bypassPermissions` — approve everything — while the CLI's own Auto is `auto`,
     * which safety-checks each action and pauses on anything risky. A stored bypass is therefore
     * migrated DOWN to `auto`: the label the user chose now means the safer thing.
     */
    fun resolveStored(stored: String?): String = when (stored) {
        null, "" -> "manual"
        "default" -> "manual"
        "bypassPermissions" -> "auto"
        else -> stored
    }
}
