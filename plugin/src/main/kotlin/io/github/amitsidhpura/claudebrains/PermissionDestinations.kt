package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Where an echoed "don't ask again" grant is kept (checklist 4.8). Pure, so the stamping rule can be
 * pinned without a session.
 *
 * The CLI reads `destination` off every `updatedPermissions` entry and that value — not the
 * suggestion's own — decides the file (MEASURED on 2.1.260, 2026-09-04, stdio in a trusted scratch
 * workspace): `projectSettings` → `.claude/settings.json`, `localSettings` →
 * `.claude/settings.local.json`, `userSettings` → `<config dir>/settings.json`, `session` → nothing
 * on disk and no re-ask for the run. An UNKNOWN value drops the grant silently (the next turn asks
 * again), so only the four the picker offers are ever forwarded; anything else leaves the entry as
 * the CLI suggested it. `cliArg` exists in the CLI's vocabulary but is not offered — VS Code's
 * picker (webview `Ss`) leaves it out too.
 */
object PermissionDestinations {
    val OFFERED: Set<String> = setOf("session", "localSettings", "projectSettings", "userSettings")

    /**
     * Stamps [destination] onto every entry except `setMode` ones, which keep their own scope
     * (VS Code stamps only non-setMode suggestions; a mode switch is session-bound by nature).
     * A null or unknown destination returns [entries] untouched.
     */
    fun stamp(entries: List<JsonObject>, destination: String?): List<JsonObject> {
        if (destination == null || destination !in OFFERED) return entries
        return entries.map { e ->
            if (e["type"]?.jsonPrimitive?.contentOrNull == "setMode") e
            else JsonObject(e.toMutableMap().apply { put("destination", JsonPrimitive(destination)) })
        }
    }
}
