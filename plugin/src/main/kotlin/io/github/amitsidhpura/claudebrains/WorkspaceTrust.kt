package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Whether Claude Code holds a trust record for the folder a session runs in (checklist 15.7).
 * Pure, so the rule can be pinned without a Project or a real home directory.
 *
 * MEASURED 2026-11-22 on 2.1.330 (bare `initialize`, panel flags, a folder whose
 * `.claude/settings.json` holds an output style, a SessionStart hook and one allow rule): with no
 * record the CLI DROPS the folder's `permissions.allow` entries — stderr: "Ignoring 1
 * permissions.allow entry from .claude/settings.json: this workspace has not been trusted" —
 * while the output style still applied and the hook still ran.
 * The record that counts is the one of the session's trust key: the git root of the working
 * directory, else the directory itself. Measured the same day: a folder inside a trusted git
 * repository, with no record of its own, had its rule applied (its git root is trusted); a git
 * repository of its own directly under a trusted folder (`~/Sites` trusted, the repo's own flag
 * false) had it dropped, and so had a plain folder with no git and no record under that same
 * trusted folder. So a record does NOT cover the folders below it, except through the git root.
 * A terminal asks first; over stream-json nothing does — `claude --help`: "The workspace trust
 * dialog is skipped when Claude is run in non-interactive mode" — and the wire carries nothing
 * about it (the `initialize` answer is a plain `success`). So the panel reads the record itself,
 * read-only: the CLI keeps it in its global config as `projects[<dir>].hasTrustDialogAccepted`.
 *
 * Reading is the panel's OWN mechanism (VS Code asks its CLI binary); the write is not ours at
 * all — the user's click sends `initialize.workspaceTrust` and the CLI records it ([attestDir]).
 */
object WorkspaceTrust {
    private val json = Json { ignoreUnknownKeys = true }

    /** The CLI's global config: `$CLAUDE_CONFIG_DIR/.claude.json`, else `~/.claude.json`. */
    fun configFile(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home")): File =
        env("CLAUDE_CONFIG_DIR")?.takeIf { it.isNotBlank() }?.let { File(it, ".claude.json") }
            ?: File(home, ".claude.json")

    /**
     * true / false from the config's text, or null when it cannot say (unreadable, not JSON, no
     * `projects` object) — null draws nothing: a warning we cannot stand behind is worse than
     * none. Trusted = the record of [key] itself carries the flag; [key] is the session's trust
     * key ([attestDir]). A trusted folder ABOVE the key counts for nothing (measured, class doc).
     */
    fun isTrusted(configText: String?, key: String): Boolean? {
        val projects = runCatching {
            json.parseToJsonElement(configText ?: return null).jsonObject["projects"]?.jsonObject
        }.getOrNull() ?: return null
        val k = norm(key)
        return projects.entries.any { (name, v) ->
            norm(name) == k &&
                runCatching { v.jsonObject["hasTrustDialogAccepted"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true
        }
    }

    /**
     * The session's trust key, and the directory an attestation names: the nearest folder at or above [cwd] holding `.git`
     * (the CLI honours `workspaceTrust.directory` only when it resolves to the session's trust
     * key, the git root — its schema, 2.1.330), else [cwd] itself.
     */
    fun attestDir(cwd: File, hasGit: (File) -> Boolean = { File(it, ".git").exists() }): File =
        generateSequence(cwd.absoluteFile) { it.parentFile }.firstOrNull(hasGit) ?: cwd.absoluteFile

    /** [path] with the home prefix folded to `~`, for the line's confirm. */
    fun display(path: String, home: String = System.getProperty("user.home")): String {
        val p = norm(path); val h = norm(home)
        return if (p == h) "~" else if (p.startsWith("$h/")) "~" + p.substring(h.length) else p
    }

    private fun norm(p: String): String = p.replace('\\', '/').trimEnd('/').ifEmpty { "/" }
}
