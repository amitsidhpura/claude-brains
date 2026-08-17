package io.github.amitsidhpura.claudebrains.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

/**
 * Writes / removes ~/.claude/ide/<port>.lock exactly as the VS Code host does (zG/I0e/P0e).
 * The CLI discovers us via CLAUDE_CODE_SSE_PORT + this file's authToken.
 *
 * Contents: { pid, workspaceFolders, ideName, transport:"ws", runningInWindows, authToken }
 */
class IdeLockFile(
    private val port: Int,
    private val authToken: String,
) {
    private val path: Path = ideDir().resolve("$port.lock")

    fun write(workspaceFolders: List<String>, ideName: String) {
        sweepStale(path.parent)
        val json = buildJsonObject {
            put("pid", ProcessHandle.current().pid())
            put("workspaceFolders", JsonArray(workspaceFolders.map { JsonPrimitive(it) }))
            put("ideName", ideName)
            put("transport", "ws")
            put("runningInWindows", System.getProperty("os.name").startsWith("Windows"))
            put("authToken", authToken)
        }
        Files.createDirectories(path.parent)
        Files.writeString(path, Json.encodeToString(JsonObject.serializer(), json))
        tryChmod(path, "rw-------") // 0600; no-op on Windows
    }

    fun delete() {
        runCatching { Files.deleteIfExists(path) }
    }

    /**
     * Remove lock files whose writer is gone. [delete] only runs on an orderly dispose — a killed
     * sandbox, a crashed IDE or a hot-reload that skipped dispose leaves its lock behind (17 files,
     * 15 dead, found in `~/.claude/ide/` on 2026-08-17). The CLI has the same sweep (dead pid →
     * unlink) but runs it only when it ENUMERATES IDEs, which our `--mcp-config` route never asks
     * it to, so nothing else ever cleans up. Same rule as the CLI's: pid absent → stale. A lock
     * with a live pid — ours, another IDE's, a reused pid — is left alone; a lock we cannot read
     * is left alone too (the CLI deletes those; we stay in our lane).
     */
    private fun sweepStale(dir: Path) {
        val me = ProcessHandle.current().pid()
        runCatching {
            Files.list(dir).use { files ->
                files.filter { it.fileName.toString().endsWith(".lock") }.forEach { f ->
                    val pid = runCatching {
                        Json.parseToJsonElement(Files.readString(f)).jsonObject["pid"]?.jsonPrimitive?.long
                    }.getOrNull() ?: return@forEach
                    if (pid != me && ProcessHandle.of(pid).isEmpty) runCatching { Files.deleteIfExists(f) }
                }
            }
        }
    }

    private fun ideDir(): Path {
        val base = System.getenv("CLAUDE_CONFIG_DIR")
            ?: Paths.get(System.getProperty("user.home"), ".claude").toString()
        val dir = Paths.get(base, "ide")
        Files.createDirectories(dir)
        tryChmod(dir, "rwx------") // 0700
        return dir
    }

    private fun tryChmod(p: Path, perms: String) {
        runCatching {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(perms))
        } // UnsupportedOperationException on Windows -> ignored
    }
}
