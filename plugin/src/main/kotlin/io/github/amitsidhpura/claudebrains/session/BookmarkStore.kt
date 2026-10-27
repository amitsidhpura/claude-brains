package io.github.amitsidhpura.claudebrains.session

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Bookmarks (checklist 8.15): which replies of a conversation the user keeps at hand. One small
 * file per conversation, VS Code's own shape — `{updatedAt, bookmarks:[{uuid, addedAt, writtenAt}]}`
 * — in OUR folder under the IDE's config path (never VS Code's `globalStorage`, whose format is
 * private to it; and config rather than the system/cache path, which Invalidate Caches clears).
 * A bookmark is the assistant record's `uuid`; the text is never copied here — the page reads it
 * back from the transcript by uuid, so a record that is gone reads as missing rather than stale.
 * The file goes away with its last bookmark. Writes are whole-file replaces through a sibling
 * temp file, so a crash mid-write leaves the previous list, never a half-written one.
 */
class BookmarkStore(private val dir: File) {
    data class Bookmark(val uuid: String, val addedAt: Long, val writtenAt: Long?)

    companion object {
        private val log = Logger.getInstance(BookmarkStore::class.java)
        /** More than anyone works through at once; keeps a runaway page from growing the file. */
        const val MAX = 200
        private val json = Json { ignoreUnknownKeys = true }
        private val ID = Regex("[A-Za-z0-9._-]{1,120}")
        fun default() = BookmarkStore(File(PathManager.getConfigPath(), "claude-brains/session-bookmarks"))
    }

    fun list(sessionId: String): List<Bookmark> {
        val f = fileFor(sessionId) ?: return emptyList()
        return read(f)
    }

    /** Add ([on]) or remove the bookmark for [uuid]; returns the list as it now stands. */
    @Synchronized
    fun update(sessionId: String, uuid: String, on: Boolean, writtenAt: Long?): List<Bookmark> {
        val f = fileFor(sessionId) ?: return emptyList()
        if (!ID.matches(uuid)) return read(f)
        val kept = read(f).filter { it.uuid != uuid }
        val next = if (on) (kept + Bookmark(uuid, System.currentTimeMillis(), writtenAt)).takeLast(MAX) else kept
        write(f, next)
        return next
    }

    private fun fileFor(sessionId: String): File? =
        if (ID.matches(sessionId)) File(dir, "$sessionId.json") else null

    private fun read(f: File): List<Bookmark> {
        if (!f.isFile) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(f.readText()).jsonObject
            (root["bookmarks"] as? JsonArray ?: return emptyList()).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val uuid = o["uuid"]?.jsonPrimitive?.contentOrNull?.takeIf { ID.matches(it) } ?: return@mapNotNull null
                Bookmark(uuid, o["addedAt"]?.jsonPrimitive?.longOrNull ?: 0L, o["writtenAt"]?.jsonPrimitive?.longOrNull)
            }.distinctBy { it.uuid }
        }.onFailure { log.warn("bookmarks: unreadable ${f.name}", it) }.getOrDefault(emptyList())
    }

    private fun write(f: File, list: List<Bookmark>) {
        if (list.isEmpty()) { f.delete(); return }
        dir.mkdirs()
        val body = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("updatedAt", System.currentTimeMillis())
            put("bookmarks", buildJsonArray {
                list.forEach { b ->
                    add(buildJsonObject {
                        put("uuid", b.uuid); put("addedAt", b.addedAt)
                        b.writtenAt?.let { put("writtenAt", it) }
                    })
                }
            })
        })
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(body)
        runCatching { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .recoverCatching { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            .onFailure { log.warn("bookmarks: could not write ${f.name}", it); tmp.delete() }
    }
}
