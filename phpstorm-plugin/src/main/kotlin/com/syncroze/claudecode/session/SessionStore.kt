package com.syncroze.claudecode.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Reads Claude Code's on-disk conversation history for the current project.
 *
 * Layout (matches the CLI): ~/.claude/projects/<enc-cwd>/<sessionId>.jsonl
 * where enc-cwd = cwd with every non-alphanumeric char replaced by '-'.
 */
object SessionStore {

    data class SessionInfo(val id: String, val title: String, val lastModified: Long)

    /** role = "user" | "assistant" | "tool"; for "tool", text is the tool name. */
    data class TranscriptItem(val role: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun projectDir(cwd: String): File {
        val enc = cwd.replace(Regex("[^a-zA-Z0-9]"), "-")
        return File(File(System.getProperty("user.home"), ".claude/projects"), enc)
    }

    fun list(cwd: String, limit: Int = 40): List<SessionInfo> {
        val files = projectDir(cwd).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
            .take(limit)
            .map { SessionInfo(it.nameWithoutExtension, titleOf(it), it.lastModified()) }
    }

    /** Derive a human title: prefer a summary record, else the first user message text. */
    private fun titleOf(f: File): String {
        var firstUser: String? = null
        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines.take(60)) {
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "summary" -> obj["summary"]?.jsonPrimitive?.content?.let { return it.trim() }
                        "user" -> if (firstUser == null) firstUser = userText(obj)
                    }
                }
            }
        }
        return firstUser?.take(80)?.ifBlank { null } ?: "(untitled session)"
    }

    /** Parse a session's JSONL into renderable turns (user text, assistant text, tool chips). */
    fun readTranscript(cwd: String, id: String, max: Int = 4000): List<TranscriptItem> {
        val f = File(projectDir(cwd), "$id.jsonl")
        if (!f.isFile) return emptyList()
        val out = ArrayList<TranscriptItem>()
        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (out.size >= max) break
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "user" -> {
                            val isMeta = obj["isMeta"]?.jsonPrimitive?.content == "true" // caveat wrapper
                            if (!isMeta) userTextFull(obj)?.takeIf { it.isNotBlank() }
                                ?.let { cleanInjected(it) }?.takeIf { it.isNotBlank() }
                                ?.let { out.add(TranscriptItem("user", it)) }
                        }
                        "assistant" -> {
                            val content = obj["message"]?.jsonObject?.get("content")
                            if (content is JsonArray) {
                                for (block in content) {
                                    val b = block.jsonObject
                                    when (b["type"]?.jsonPrimitive?.content) {
                                        "text" -> b["text"]?.jsonPrimitive?.content
                                            ?.takeIf { it.isNotBlank() }?.let { out.add(TranscriptItem("assistant", it)) }
                                        "tool_use" -> b["name"]?.jsonPrimitive?.content
                                            ?.let { out.add(TranscriptItem("tool", it)) }
                                    }
                                }
                            } else if (content is JsonPrimitive) {
                                out.add(TranscriptItem("assistant", content.content))
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    // The CLI injects bookkeeping as user messages: slash-command wrappers and background-task
    // notifications. Drop the caveat/stdout/task-notification wrappers (plumbing the main agent
    // consumes — its own replies carry the user-facing content), and collapse a
    // <command-name>/x</command-name>…<command-args>y</command-args> block into a compact "/x y".
    private val CMD_RE = Regex(
        "<command-name>\\s*(.*?)\\s*</command-name>.*?<command-args>\\s*(.*?)\\s*</command-args>",
        RegexOption.DOT_MATCHES_ALL,
    )
    private fun cleanInjected(text: String): String? {
        val t = text.trim()
        if (t.startsWith("<local-command-caveat>") || t.startsWith("<local-command-stdout>") ||
            t.startsWith("<task-notification>")) return null
        CMD_RE.find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val args = m.groupValues[2].trim()
            return if (args.isEmpty()) name else "$name $args"
        }
        return text
    }

    /** Full user text (all text parts, newlines preserved) — skips tool_result-only messages. */
    private fun userTextFull(obj: JsonObject): String? {
        val content = obj["message"]?.jsonObject?.get("content") ?: return null
        return when (content) {
            is JsonPrimitive -> content.content
            is JsonArray -> content.mapNotNull { p ->
                p.jsonObject.takeIf { it["type"]?.jsonPrimitive?.content == "text" }
                    ?.get("text")?.jsonPrimitive?.content
            }.joinToString("\n").ifBlank { null }
            else -> null
        }
    }

    private fun userText(obj: JsonObject): String? {
        val content = obj["message"]?.jsonObject?.get("content") ?: return null
        return when (content) {
            is JsonPrimitive -> content.content
            is JsonArray -> content.firstNotNullOfOrNull { part ->
                part.jsonObject.takeIf { it["type"]?.jsonPrimitive?.content == "text" }
                    ?.get("text")?.jsonPrimitive?.content
            }
            else -> null
        }?.trim()?.replace("\n", " ")
    }
}
