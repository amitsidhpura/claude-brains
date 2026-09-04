package io.github.amitsidhpura.claudebrains

/**
 * The persisted-mode rule behind the spawn flag, pure so it can be pinned without a
 * PropertiesComponent (checklist 4.5 / 4.7).
 */
object PermissionModes {
    /**
     * Null when nothing was ever picked — the CLI is then launched without `--permission-mode` and
     * applies the user's own `permissions.defaultMode` (the flag would beat the file otherwise).
     *
     * A stored value is migrated to the CLI's current vocabulary (2.1.220): `default` is no longer
     * advertised (it still parses, but `manual` replaced it), and what this plugin once labelled
     * "Auto mode" was `bypassPermissions` — approve everything — while the CLI's own Auto is `auto`,
     * which safety-checks each action and pauses on anything risky. A stored bypass is therefore
     * migrated DOWN to `auto`: the label the user chose now means the safer thing.
     */
    fun resolveStored(stored: String?): String? = when (stored) {
        null, "" -> null
        "default" -> "manual"
        "bypassPermissions" -> "auto"
        else -> stored
    }
}
