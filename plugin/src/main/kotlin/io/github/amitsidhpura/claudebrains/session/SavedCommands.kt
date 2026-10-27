package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.Paths

/**
 * Saved messages (checklist 7.11 / 7.12). A saved message is an ordinary Claude Code command file —
 * `.claude/commands/<name>.md` in the project, or `commands/<name>.md` under the CLI's config dir
 * for every project — so the terminal and VS Code send the same thing, and the CLI (not the
 * panel) expands it. Nothing here talks to the IDE; the dialog and the panel call in.
 */
object SavedCommands {
    /** What the panel itself can type and route: `^/([a-z][\w-]*)` in submit(), lowercased here
     *  so the file name and the menu entry never differ by case. No `:` — subfolders are left to
     *  the terminal. */
    val NAME = Regex("[a-z][a-z0-9_-]{0,63}")

    /** Prompts that carry the CLI's argument placeholders get a hint, or the pick rule (7.5) would
     *  run them on click with the placeholder empty. */
    private val ARGS = Regex("""\$(ARGUMENTS|\d)""")
    const val ARG_HINT = "[text]"

    /** The CLI's config dir, resolved the way the lock file resolves it (IdeLockFile). */
    fun userDir(): File = File(
        System.getenv("CLAUDE_CONFIG_DIR") ?: Paths.get(System.getProperty("user.home"), ".claude").toString(),
        "commands",
    )

    fun projectDir(basePath: String): File = File(basePath, ".claude/commands")

    /** A starting name from the prompt's first words: `Run the tests and fix…` → `run-the-tests`. */
    fun suggestName(text: String): String {
        val words = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            .dropWhile { !it[0].isLetter() }   // a name starts with a letter
        val slug = words.take(3).joinToString("-").take(40).trimEnd('-')
        return slug.ifEmpty { "saved-message" }
    }

    fun argumentHint(body: String): String? = if (ARGS.containsMatchIn(body)) ARG_HINT else null

    /** The file's text: frontmatter only for the fields that are set, then the prompt as typed. */
    fun fileBody(description: String, body: String): String {
        val desc = description.replace(Regex("\\s+"), " ").trim()
        val hint = argumentHint(body)
        val front = buildList {
            if (desc.isNotEmpty()) add("description: " + JsonPrimitive(desc))   // JSON string = valid YAML double-quoted scalar
            if (hint != null) add("argument-hint: " + JsonPrimitive(hint))
        }
        val head = if (front.isEmpty()) "" else "---\n" + front.joinToString("\n") + "\n---\n"
        return head + body.trimEnd() + "\n"
    }

    // ---- 7.12 recency: {name: epochMs}, newest kept ----
    const val MAX_RECENT = 100
    private val json = Json { ignoreUnknownKeys = true }

    fun parseRecent(stored: String?): Map<String, Long> = runCatching {
        json.parseToJsonElement(stored ?: return emptyMap()).jsonObject
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** [stored] with [name] stamped [now], trimmed to the [MAX_RECENT] newest. */
    fun bumpRecent(stored: String?, name: String, now: Long): String {
        val next = (parseRecent(stored) + (name to now)).entries
            .sortedByDescending { it.value }.take(MAX_RECENT)
        return encodeRecent(next.associate { it.key to it.value })
    }

    fun encodeRecent(map: Map<String, Long>): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject { map.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
}
