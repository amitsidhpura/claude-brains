package com.syncroze.claudecode.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Reads Claude Code's on-disk conversation history for the current project.
 *
 * Layout (matches the CLI): ~/.claude/projects/<enc-cwd>/<sessionId>.jsonl
 * where enc-cwd = cwd with every non-alphanumeric char replaced by '-'.
 */
object SessionStore {

    data class SessionInfo(val id: String, val title: String, val lastModified: Long)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cap on base64 image payload embedded in one replayed transcript; chips past it fall back to
     * name-only. The whole transcript ships as a single `executeJavaScript` string literal, so this
     * keeps a screenshot-heavy session from producing a huge frame.
     */
    private const val IMAGE_BUDGET = 4 * 1024 * 1024

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

    /**
     * Derive a human title. The CLI writes an `ai-title` record (current versions) and older
     * sessions carry a `summary` record; fall back to the first user message.
     */
    private fun titleOf(f: File): String {
        var firstUser: String? = null
        var aiTitle: String? = null
        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines.take(400)) {
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "summary" -> obj["summary"]?.jsonPrimitive?.content?.let { return it.trim() }
                        // keep scanning: later ai-title records supersede earlier ones
                        "ai-title" -> obj["aiTitle"]?.jsonPrimitive?.content?.trim()
                            ?.takeIf { it.isNotBlank() }?.let { aiTitle = it }
                        "user" -> if (firstUser == null) firstUser = userText(obj)
                    }
                }
            }
        }
        return aiTitle ?: firstUser?.take(80)?.ifBlank { null } ?: "(untitled session)"
    }

    /** One replayable block. Mirrors what the live renderer draws; serialized straight to the webview. */
    private class Item(val role: String) {
        var text: String = ""              // user text / assistant markdown / thinking / tool name
        var desc: String? = null           // tool description or file path
        var isPath: Boolean = false
        var cmd: String? = null            // Bash IN
        var out: String? = null            // Bash OUT
        var isError: Boolean = false
        var patch: JsonElement? = null     // structuredPatch hunks (authoritative diff + line numbers)
        var content: String? = null        // Write body (no patch available)
        var oldStr: String? = null         // Edit fallback when no patch
        var newStr: String? = null
        var file: String? = null
        var images = mutableListOf<JsonObject>()
        var questions: JsonElement? = null // AskUserQuestion input
        var answers: JsonElement? = null   // chosen answers (from toolUseResult)
        var plan: String? = null           // ExitPlanMode plan markdown
        var denied: Boolean = false        // permission was refused — card reads ✗, not ✓
        var icon: String? = null           // status glyph key ("stop")
        var durMs: Long? = null            // thinking duration / request wall-clock
        var tokens: Long? = null           // output tokens for the request summary

        fun toJson(): JsonObject = buildJsonObject {
            put("role", role)
            put("text", text)
            plan?.let { put("plan", it) }
            if (denied) put("denied", true)
            icon?.let { put("icon", it) }
            durMs?.let { put("durMs", it) }
            tokens?.let { put("tokens", it) }
            desc?.let { put("desc", it) }
            if (isPath) put("isPath", true)
            cmd?.let { put("cmd", it) }
            out?.let { put("out", it) }
            if (isError) put("isError", true)
            patch?.let { put("patch", it) }
            content?.let { put("content", it) }
            oldStr?.let { put("oldStr", it) }
            newStr?.let { put("newStr", it) }
            file?.let { put("file", it) }
            questions?.let { put("questions", it) }
            answers?.let { put("answers", it) }
            if (images.isNotEmpty()) put("images", buildJsonArray { images.forEach { add(it) } })
        }
    }

    /**
     * Parse a session's JSONL into renderable blocks: user text/images, thinking, assistant
     * markdown, tool lines (description, Bash IN/OUT, failure), Edit/Write diffs, and answered
     * AskUserQuestion cards. Tool results arrive in later records, so tool items are indexed by
     * `tool_use_id` and patched in place as their results are read.
     */
    fun readTranscript(cwd: String, id: String, max: Int = 4000): List<JsonObject> {
        val f = File(projectDir(cwd), "$id.jsonl")
        if (!f.isFile) return emptyList()
        val out = ArrayList<Item>()
        val byToolId = HashMap<String, Item>()
        var imageBytes = 0

        // Per-request accounting for the trailing "✻ … for Ns · ↓ N tokens" summary. A request runs
        // from a user turn to the next one; `prevTs` also gives thinking blocks an approximate
        // duration (record-to-record wall time — the JSONL has no per-block timer).
        var prevTs: java.time.Instant? = null
        var reqStart: java.time.Instant? = null
        var reqLast: java.time.Instant? = null
        var reqTokens = 0L
        var reqWork = false

        fun flushSummary() {
            val s = reqStart
            val e = reqLast
            // no tokens means nothing was actually produced (usage absent / empty turn) —
            // a "for 0s · ↓ 0 tokens" line is noise, so skip it rather than render an empty stat
            if (reqWork && s != null && e != null && reqTokens > 0) {
                out.add(Item("done").apply {
                    durMs = java.time.Duration.between(s, e).toMillis().coerceAtLeast(0)
                    tokens = reqTokens
                })
            }
            reqStart = null; reqLast = null; reqTokens = 0L; reqWork = false
        }

        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (out.size >= max) break
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    val ts = obj["timestamp"]?.jsonPrimitive?.content
                        ?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
                    val sincePrev = if (prevTs != null && ts != null)
                        java.time.Duration.between(prevTs, ts).toMillis() else null
                    if (ts != null) prevTs = ts
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "user" -> {
                            val content = obj["message"]?.jsonObject?.get("content")
                            // tool results first: they patch an earlier tool item, not a new block
                            if (content is JsonArray) {
                                for (block in content) {
                                    val b = block.jsonObject
                                    if (b["type"]?.jsonPrimitive?.content == "tool_result") {
                                        applyToolResult(
                                            b, obj["toolUseResult"], byToolId,
                                            denied = obj["toolDenialKind"] != null,
                                        )
                                    }
                                }
                            }
                            // an interrupt ends the request with "⏹ Stopped" and no summary,
                            // exactly as the live path does — not as a user message
                            if (obj["interruptedByShutdown"]?.jsonPrimitive?.content == "true") {
                                reqWork = false; flushSummary()
                                out.add(Item("status").apply { text = "Stopped"; icon = "stop" })
                                continue
                            }
                            if (obj["isMeta"]?.jsonPrimitive?.content == "true") continue // caveat wrapper
                            val text = userTextFull(obj)?.let { cleanInjected(it) }?.takeIf { it.isNotBlank() }
                            val imgs = imagesOf(content)
                            if (text == null && imgs.isEmpty()) continue
                            val item = Item("user")
                            item.text = text ?: ""
                            for (img in imgs) {
                                val data = img["data"]?.jsonPrimitive?.content ?: continue
                                if (imageBytes + data.length > IMAGE_BUDGET) {
                                    item.images.add(buildJsonObject { put("name", img["name"]?.jsonPrimitive?.content ?: "image.png") })
                                } else {
                                    imageBytes += data.length
                                    item.images.add(img)
                                }
                            }
                            flushSummary() // close the previous request before the next turn opens
                            out.add(item)
                            reqStart = ts
                        }
                        "assistant" -> {
                            reqWork = true
                            if (ts != null) reqLast = ts
                            obj["message"]?.jsonObject?.get("usage")?.jsonObject
                                ?.get("output_tokens")?.jsonPrimitive?.content?.toLongOrNull()
                                ?.let { reqTokens += it }
                            val content = obj["message"]?.jsonObject?.get("content")
                            if (content is JsonPrimitive) {
                                out.add(Item("assistant").apply { text = content.content })
                            } else if (content is JsonArray) {
                                for (block in content) {
                                    val b = block.jsonObject
                                    when (b["type"]?.jsonPrimitive?.content) {
                                        "text" -> b["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                            ?.let { out.add(Item("assistant").apply { text = it }) }
                                        "thinking" -> b["thinking"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                            ?.let { out.add(Item("thinking").apply { text = it; durMs = sincePrev }) }
                                        "tool_use" -> out.add(toolItem(b, byToolId))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            flushSummary() // the last request has no following user turn to close it
        }
        return out.map { it.toJson() }
    }

    /** Build the block for a tool_use, indexing it so its result can be attached later. */
    private fun toolItem(b: JsonObject, byToolId: HashMap<String, Item>): Item {
        val name = b["name"]?.jsonPrimitive?.content ?: "tool"
        val inp = b["input"] as? JsonObject
        val item = if (name == "AskUserQuestion") {
            Item("ask").apply { questions = inp?.get("questions") }
        } else {
            Item("tool").apply {
                text = name
                val path = inp?.get("file_path")?.jsonPrimitive?.content
                    ?: inp?.get("path")?.jsonPrimitive?.content
                // keep this chain in step with chat.html's live `content_block_stop` handler;
                // query/url cover ToolSearch, WebSearch and WebFetch, which carry no description
                val d = inp?.get("description")?.jsonPrimitive?.content
                    ?: path
                    ?: inp?.get("pattern")?.jsonPrimitive?.content
                    ?: inp?.get("query")?.jsonPrimitive?.content
                    ?: inp?.get("url")?.jsonPrimitive?.content
                if (d != null) { desc = d.take(140); isPath = (d == path) }
                file = path
                if (name == "Bash") cmd = inp?.get("command")?.jsonPrimitive?.content?.take(2000)
                if (name == "ExitPlanMode") plan = inp?.get("plan")?.jsonPrimitive?.content
                oldStr = inp?.get("old_string")?.jsonPrimitive?.content
                newStr = inp?.get("new_string")?.jsonPrimitive?.content
                if (name == "Write") content = inp?.get("content")?.jsonPrimitive?.content
            }
        }
        b["id"]?.jsonPrimitive?.content?.let { byToolId[it] = item }
        return item
    }

    /**
     * Attach a tool result to its tool block: failure flag, Bash output, the authoritative
     * structuredPatch for edits, and the answers chosen on an AskUserQuestion card.
     */
    private fun applyToolResult(
        block: JsonObject,
        toolUseResult: JsonElement?,
        byToolId: Map<String, Item>,
        denied: Boolean = false,
    ) {
        val id = block["tool_use_id"]?.jsonPrimitive?.content ?: return
        val item = byToolId[id] ?: return
        if (block["is_error"]?.jsonPrimitive?.content == "true") item.isError = true
        // a refused permission isn't a tool failure: the card must read ✗ Rejected, not ✓ Applied
        if (denied) { item.denied = true; item.isError = false }

        val res = toolUseResult as? JsonObject
        if (item.role == "ask") { res?.get("answers")?.let { item.answers = it }; return }

        res?.get("structuredPatch")?.takeIf { it is JsonArray && it.jsonArray.isNotEmpty() }
            ?.let { item.patch = it }

        if (item.text == "Bash") {
            val stdout = res?.get("stdout")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val stderr = res?.get("stderr")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val fromBlock = resultText(block["content"])
            val txt = listOfNotNull(stdout, stderr).joinToString("\n").ifBlank { fromBlock }
            item.out = txt.trim().takeIf { it.isNotBlank() }?.take(2000)
        }
    }

    private fun resultText(content: JsonElement?): String = when (content) {
        is JsonPrimitive -> content.content
        is JsonArray -> content.mapNotNull { p ->
            p.jsonObject.takeIf { it["type"]?.jsonPrimitive?.content == "text" }
                ?.get("text")?.jsonPrimitive?.content
        }.joinToString("\n")
        else -> ""
    }

    /** Image blocks attached to a user message, as {media_type, data, name}. */
    private fun imagesOf(content: JsonElement?): List<JsonObject> {
        if (content !is JsonArray) return emptyList()
        return content.mapNotNull { p ->
            val b = p.jsonObject
            if (b["type"]?.jsonPrimitive?.content != "image") return@mapNotNull null
            val src = b["source"]?.jsonObject ?: return@mapNotNull null
            val data = src["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
            buildJsonObject {
                put("media_type", src["media_type"]?.jsonPrimitive?.content ?: "image/png")
                put("data", data)
                put("name", "image.png")
            }
        }
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
