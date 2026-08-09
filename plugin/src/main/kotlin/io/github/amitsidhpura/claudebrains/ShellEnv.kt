package io.github.amitsidhpura.claudebrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The user's SHELL environment, captured once per IDE run by running their login+interactive
 * shell — the PATH layer a desktop-launched IDE never sees (nvm, pyenv, …), which the CLI
 * needs to spawn `npx`-style MCP servers (measured 2026-08-09: `npx @playwright/mcp` failed
 * under the IDE, fine in a terminal; npx lived only in nvm's shell-added dir). The platform's
 * `EnvironmentUtil` solves exactly this but ONLY on macOS — `shouldLoadShellEnv()` opens with
 * `if (!isMac) return false` (read from the 242 bytecode, unchanged on 262) — so Linux needs
 * this hand-rolled twin. Windows has no shell-init PATH layer: empty there by design.
 *
 * Interactive (-i) because that is where nvm & co. hook (nvm loads from .zshrc; a login-only
 * shell missed it). `env -0` NUL-separates entries so multiline values cannot shear; rc-file
 * chatter shares stdout, so every entry must present a valid NAME= head, salvaged past the
 * chatter's last newline. Bounded everywhere — a blocking rc file (a tmux auto-attach, say)
 * costs THIS capture, never a thread or the IDE: the watchdog kills the shell and callers get
 * an empty map, which is exactly the pre-capture behavior.
 */
object ShellEnv {

    private val log = Logger.getInstance(ShellEnv::class.java)
    private val captured = CompletableFuture<Map<String, String>>()
    @Volatile private var started = false

    /** Start the capture on a pooled thread; first call wins, later calls no-op. */
    fun warm() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            captured.complete(
                runCatching { capture() }
                    .getOrElse { log.warn("shell env capture failed", it); emptyMap() },
            )
        }
    }

    /** The captured environment, waiting at most [timeoutMs]; empty when unavailable. */
    fun get(timeoutMs: Long = 3_000): Map<String, String> {
        warm()
        return runCatching { captured.get(timeoutMs, TimeUnit.MILLISECONDS) }.getOrElse { emptyMap() }
    }

    private fun capture(): Map<String, String> {
        if (System.getProperty("os.name").startsWith("Windows")) return emptyMap()
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val p = ProcessBuilder(shell, "-l", "-i", "-c", "command env -0")
            .redirectErrorStream(false)
            .start()
        p.outputStream.close() // an interactive shell left waiting on stdin must see EOF
        // The read blocks until the shell's stdout closes, so the timeout must come from
        // OUTSIDE the read: a watchdog kill unblocks it. waitFor() then reaps a process that
        // is either done or already being killed — it cannot hang.
        val watchdog = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule({ if (p.isAlive) p.destroyForcibly() }, 10, TimeUnit.SECONDS)
        val out = p.inputStream.readBytes()
        p.waitFor()
        watchdog.cancel(false)
        return parse(String(out, Charsets.UTF_8))
    }

    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** NUL-separated NAME=value entries; anything without a valid head is rc chatter, dropped. */
    private fun parse(text: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        text.split('\u0000').forEach { raw ->
            // rc chatter precedes the first entry on the same stream — strip it line by line
            // until a valid head appears ("Welcome!\nPATH=…" is PATH, not lost), so a
            // multiline VALUE behind that head still survives intact
            var entry = raw
            while (!valid(entry)) {
                val n = entry.indexOf('\n')
                if (n < 0) break
                entry = entry.substring(n + 1)
            }
            if (valid(entry)) {
                val i = entry.indexOf('=')
                map[entry.substring(0, i)] = entry.substring(i + 1)
            }
        }
        return map
    }

    private fun valid(entry: String): Boolean {
        val i = entry.indexOf('=')
        return i > 0 && NAME.matches(entry.substring(0, i))
    }
}
