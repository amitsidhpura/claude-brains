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

    data class SessionInfo(
        val id: String,
        /** Last real conversation activity — NOT the file mtime; see [lastActivityOf]. */
        val lastActivity: Long,
        val title: String,
        val sizeBytes: Long = 0,
        val tokens: Long = 0,
    )

    /**
     * Timestamp of the last user/assistant record. The file's mtime is *last touched*, not *last
     * talked*: merely resuming a session makes the CLI append bookkeeping (`mode`,
     * `queue-operation`, `last-prompt`), which bumps mtime by up to an hour or more without any
     * conversation happening — so a session appeared to "update" just by being opened.
     *
     * Only the tail is read, since we want the last such record; falls back to mtime if the tail
     * holds no conversation record (e.g. a huge trailing tool result).
     */
    /**
     * Last records of a transcript, without reading the whole file. The first line is dropped when
     * the file is larger than the window, since seeking mid-file lands inside a record.
     */
    private fun tailLines(f: File, window: Long = 256L * 1024): List<String> {
        val text = runCatching {
            java.io.RandomAccessFile(f, "r").use { raf ->
                val from = maxOf(0L, raf.length() - window)
                raf.seek(from)
                val buf = ByteArray((raf.length() - from).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        }.getOrNull() ?: return emptyList()
        return text.lineSequence().drop(if (f.length() > window) 1 else 0).toList()
    }

    private fun lastActivityOf(f: File): Long {
        for (line in tailLines(f).asReversed()) {
            if (!line.contains("\"timestamp\"")) continue
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val type = obj["type"]?.jsonPrimitive?.content
            if (type != "user" && type != "assistant") continue
            val ts = obj["timestamp"]?.jsonPrimitive?.content ?: continue
            runCatching { return java.time.Instant.parse(ts).toEpochMilli() }
        }
        return f.lastModified()
    }

    /** path -> (mtime, size, tokens): a 170 MB transcript is scanned once, not on every panel open. */
    private val tokenCache = HashMap<String, Triple<Long, Long, Long>>()

    /**
     * Total output tokens across the session. Only assistant records carry `usage`, so reject lines
     * on a substring before paying for a JSON parse — that keeps even the largest local transcript
     * (177 MB) well under a second, and the cache means it is paid once.
     */
    private fun tokensOf(f: File): Long {
        val cached = tokenCache[f.path]
        if (cached != null && cached.first == f.lastModified() && cached.second == f.length()) {
            return cached.third
        }
        var total = 0L
        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.contains("\"output_tokens\"")) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    total += obj["message"]?.jsonObject?.get("usage")?.jsonObject
                        ?.get("output_tokens")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
            }
        }
        tokenCache[f.path] = Triple(f.lastModified(), f.length(), total)
        return total
    }

    /**
     * First index of a tail chunk of at most [maxBlocks] blocks ending at [endExclusive], aligned
     * forward to a turn boundary (a `user` block) so a chunk never starts mid-turn — tool lines,
     * cards and summaries stay with the user message they answer. Falls back to the unaligned cut
     * if the window contains no user block at all (one giant turn).
     */
    fun alignedStart(items: List<JsonObject>, endExclusive: Int, maxBlocks: Int): Int {
        val candidate = maxOf(0, endExclusive - maxBlocks)
        if (candidate == 0) return 0
        var i = candidate
        while (i < endExclusive) {
            if (items[i]["role"]?.jsonPrimitive?.content == "user") return i
            i++
        }
        return candidate
    }

    /**
     * Context in use at the end of a session: the last assistant record's prompt size
     * (input + cache read + cache creation). Not a sum — every request re-sends the whole
     * conversation, so the newest request's prompt IS the current context size. Lets a resumed
     * thread show its gauge straight away instead of waiting for the next turn.
     */
    fun contextTokens(cwd: String, id: String): Long {
        val f = File(projectDir(cwd), "$id.jsonl")
        if (!f.isFile) return 0
        for (line in tailLines(f).asReversed()) {
            if (!line.contains("\"usage\"")) continue
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            if (obj["type"]?.jsonPrimitive?.content != "assistant") continue
            val u = obj["message"]?.jsonObject?.get("usage")?.jsonObject ?: continue
            fun n(k: String) = u[k]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val used = n("input_tokens") + n("cache_read_input_tokens") + n("cache_creation_input_tokens")
            if (used > 0) return used
        }
        return 0
    }

    /**
     * Delete a session transcript and its sibling `<id>/` directory (overflow tool results).
     * Irreversible — the caller is responsible for confirming. Returns false if the id looks
     * unsafe or the transcript was not there.
     */
    fun delete(cwd: String, id: String): Boolean {
        // the id arrives from the webview: never let it walk out of the project directory
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return false
        val dir = projectDir(cwd)
        val transcript = File(dir, "$id.jsonl")
        if (transcript.parentFile?.canonicalFile != dir.canonicalFile) return false
        val sidecar = File(dir, id)
        if (sidecar.isDirectory) sidecar.deleteRecursively()
        tokenCache.remove(transcript.path)
        return transcript.isFile && transcript.delete()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cap on base64 image payload embedded in one replayed transcript; chips past it fall back to
     * name-only. The whole transcript ships as a single `executeJavaScript` string literal, so this
     * keeps a screenshot-heavy session from producing a huge frame.
     */
    private const val IMAGE_BUDGET = 4 * 1024 * 1024

    /** Root holding `.claude/projects`. Overridable so tests can point at a fixture tree. */
    internal var claudeHome: File = File(System.getProperty("user.home"))

    fun projectDir(cwd: String): File {
        val enc = cwd.replace(Regex("[^a-zA-Z0-9]"), "-")
        return File(File(claudeHome, ".claude/projects"), enc)
    }

    fun list(cwd: String, limit: Int = 40): List<SessionInfo> {
        val files = projectDir(cwd).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }
            ?: return emptyList()
        // sort on the same value we display, so ordering matches the dates the user reads
        return files.map { it to lastActivityOf(it) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (f, activity) ->
                SessionInfo(f.nameWithoutExtension, activity, titleOf(f), f.length(), tokensOf(f))
            }
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
                        // fall back to the first REAL user message: skip caveat/stdout/task wrappers
                        // (isMeta + cleanInjected) exactly as replay does, so the title isn't a
                        // <local-command-caveat> blob
                        "user" -> if (firstUser == null && obj["isMeta"]?.jsonPrimitive?.content != "true") {
                            firstUser = userText(obj)?.let { cleanInjected(it) }?.trim()?.takeIf { it.isNotBlank() }
                        }
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
            // A finished request always took time, so emit the summary whenever real work happened
            // (reqWork = an assistant record appeared). The renderer omits the "↓ N tokens" segment
            // when it is 0, so there is no noisy "↓ 0 tokens" — but the "for Ns" time still shows.
            // A truly empty turn (no assistant record) has reqWork == false and is skipped here.
            if (reqWork && s != null && e != null) {
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
                            val atts = attachmentsOf(content)
                            if (text == null && atts.isEmpty()) continue
                            val item = Item("user")
                            item.text = text ?: ""
                            // Attach everything here; the budget is applied afterwards walking
                            // from the NEWEST turn back (see trimAttachments) so the visible tail
                            // wins. Spending it in file order let early turns — which windowed
                            // replay may never even ship — starve the images actually on screen.
                            atts.forEach { att -> if (att["data"] != null) item.images.add(att) }
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
                            // an API error ("session limit", "usage credits") is persisted as an
                            // ordinary assistant record + `isApiErrorMessage`; live draws it as an
                            // `.error` block, so replay must not render it as prose
                            val role = if (obj["isApiErrorMessage"]?.jsonPrimitive?.content == "true")
                                "error" else "assistant"
                            val content = obj["message"]?.jsonObject?.get("content")
                            if (content is JsonPrimitive) {
                                out.add(Item(role).apply { text = content.content })
                            } else if (content is JsonArray) {
                                for (block in content) {
                                    val b = block.jsonObject
                                    when (b["type"]?.jsonPrimitive?.content) {
                                        "text" -> b["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                            ?.let { out.add(Item(role).apply { text = it }) }
                                        // emit even when the body is blank — the CLI frequently
                                        // persists only a signature; the block replays as a
                                        // `think no-body` "Thought for Ns" line, matching live
                                        "thinking" -> out.add(Item("thinking").apply {
                                            text = b["thinking"]?.jsonPrimitive?.content ?: ""
                                            durMs = sincePrev
                                        })
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
        trimAttachments(out)
        return out.map { it.toJson() }
    }

    /**
     * Apply [IMAGE_BUDGET] walking from the newest block backwards: attachments near the end of the
     * transcript keep their bytes, older ones degrade to name-only chips. The whole transcript ships
     * as one `executeJavaScript` string and windowed replay sends only the tail first, so the images
     * a user actually sees must win the budget — spending it in file order gave it to the oldest.
     */
    private fun trimAttachments(items: List<Item>) {
        var used = 0L
        for (item in items.asReversed()) {
            if (item.images.isEmpty()) continue
            val kept = ArrayList<JsonObject>(item.images.size)
            for (att in item.images) {
                val data = att["data"]?.jsonPrimitive?.content
                if (data != null && used + data.length <= IMAGE_BUDGET) {
                    used += data.length
                    kept.add(att)
                } else {
                    kept.add(buildJsonObject {   // past the budget: keep the chip, drop the bytes
                        put("kind", att["kind"]?.jsonPrimitive?.content ?: "image")
                        put("name", att["name"]?.jsonPrimitive?.content ?: "file")
                    })
                }
            }
            item.images.clear(); item.images.addAll(kept)
        }
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
                    // MCP (Playwright): `element` is the schema's own human-readable description;
                    // `target` is the machine ref, so it comes last
                    ?: inp?.get("element")?.jsonPrimitive?.content
                    ?: inp?.get("filename")?.jsonPrimitive?.content
                    ?: inp?.get("target")?.jsonPrimitive?.content
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
            // Match the live path (onUserEvent): show the tool_result block's content — the
            // model-facing result text — so a resumed OUT box reads identically. stdout/stderr are
            // only a fallback for the rare record whose content block is empty.
            val fromBlock = resultText(block["content"]).takeIf { it.isNotBlank() }
            val stdout = res?.get("stdout")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val stderr = res?.get("stderr")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val txt = fromBlock ?: listOfNotNull(stdout, stderr).joinToString("\n")
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

    /**
     * Image + document attachments on a user message, as {kind, media_type, data, name}. Images stay
     * base64; a text document's raw text is re-base64'd so the chip's download path is uniform with
     * images/pdf. Documents carry a `title` (the CLI stores the real filename), so — unlike images —
     * replayed PDFs/text files show their actual name.
     */
    private fun attachmentsOf(content: JsonElement?): List<JsonObject> {
        if (content !is JsonArray) return emptyList()
        return content.mapNotNull { p ->
            val b = p.jsonObject
            val src = b["source"]?.jsonObject ?: return@mapNotNull null
            when (b["type"]?.jsonPrimitive?.content) {
                "image" -> {
                    val data = src["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val mt = src["media_type"]?.jsonPrimitive?.content ?: "image/png"
                    buildJsonObject { put("kind", "image"); put("media_type", mt); put("data", data); put("name", imageName(mt)) }
                }
                "document" -> {
                    val title = b["title"]?.jsonPrimitive?.content
                    if (src["type"]?.jsonPrimitive?.content == "text") {
                        val raw = src["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val data = java.util.Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
                        buildJsonObject { put("kind", "text"); put("media_type", "text/plain"); put("data", data); put("name", title ?: "file.txt") }
                    } else {
                        val data = src["data"]?.jsonPrimitive?.content ?: return@mapNotNull null   // base64 pdf
                        val mt = src["media_type"]?.jsonPrimitive?.content ?: "application/pdf"
                        buildJsonObject { put("kind", "pdf"); put("media_type", mt); put("data", data); put("name", title ?: "file.pdf") }
                    }
                }
                else -> null
            }
        }
    }

    // The real filename is never sent to the CLI (sendTurn ships only media_type + data), so it can't
    // be recovered on replay — but the media_type can, so at least the extension is honest: a pasted
    // JPEG reads "file.jpg", not a misleading "image.png". Base stays generic ("file").
    private fun imageName(mediaType: String): String {
        val sub = mediaType.substringAfter('/', "").substringBefore('+').lowercase()
        val ext = when (sub) {
            "jpeg" -> "jpg"   // canonical subtype is jpeg; files are .jpg
            "" -> "png"       // media_type malformed/absent
            else -> sub       // png, gif, webp, bmp, avif, svg (from svg+xml), …
        }
        return "file.$ext"
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
