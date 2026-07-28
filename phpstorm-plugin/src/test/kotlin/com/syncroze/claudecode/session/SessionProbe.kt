package com.syncroze.claudecode.session

import kotlinx.serialization.json.jsonPrimitive

/**
 * Ad-hoc inspection of what a past session replays as, run from the command line:
 *
 *     ./gradlew probe --args="/home/me/Sites/my-project 174bf208-2225-4f69-b49d-ea3038426fb7"
 *
 * Prints a role census plus every block the live renderer treats specially, so a resume bug can be
 * pinned to the parser or the renderer without starting a sandbox IDE.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: probe <projectPath> <sessionId>")
        println("sessions for a project live in ~/.claude/projects/<path-with-non-alphanumerics-as-dashes>/")
        return
    }
    val items = SessionStore.readTranscript(args[0], args[1])
    if (items.isEmpty()) {
        println("no blocks — wrong project path or session id?")
        println("looked in: ${SessionStore.projectDir(args[0])}")
        return
    }

    val roles = items.groupingBy { it["role"]?.jsonPrimitive?.content ?: "?" }.eachCount()
    println("blocks: ${items.size}  ${roles.toSortedMap()}")

    val notable = items.filter { o ->
        val role = o["role"]?.jsonPrimitive?.content
        role == "status" || role == "done" || o["denied"] != null || o["plan"] != null ||
            (role == "thinking" && o["durMs"] != null)
    }
    if (notable.isEmpty()) return
    println("\nstatus / summaries / plans / refusals / timed thinking:")
    for (o in notable) {
        val s = o.toString()
        println("  " + if (s.length > 160) s.take(150) + "…}" else s)
    }
}
