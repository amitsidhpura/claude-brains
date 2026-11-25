package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Model-switch list, items 3 and 4 (2026-11-25): what the session runs on and how much of the
 * plan is left, told to the model as a `<session_state>` note after the user's text — the same
 * place the 6.6 selection tag rides, last, so the prompt still opens the message (titles, history).
 *
 * The note goes on a message only when something in it changed since the last note this CLI
 * process saw (model, effort, fast mode) or a usage figure crossed 50 / 75 / 90 %; the first
 * message of a process always carries one. The user decided the shape (a hidden note on the
 * next message, 2026-11-25). Replay strips it ([split]), so a resumed conversation never shows it.
 *
 * Sources, MEASURED 2026-11-25 on 2.1.331 over stdio with no turn sent: `get_settings.applied`
 * `{model, effort}`; `get_usage.rate_limits` `{five_hour, seven_day}.{utilization, resets_at}` and
 * `model_scoped[] {display_name, utilization, resets_at}` (Fable's own weekly share), plus
 * `extra_usage.is_enabled` — whether usage credits pick up past the plan. Fast mode comes from the
 * CLI's `fast_mode_state` (initialize and every result).
 */
object SessionStateNote {
    data class Limit(val name: String, val percent: Int, val resetsAt: String?)

    data class Snapshot(
        val model: String?,
        val effort: String?,
        val fast: Boolean?,
        val limits: List<Limit>,
        val creditsEnabled: Boolean?,
    ) {
        /** What decides whether a new note is due: the facts plus each limit's threshold bucket. */
        fun key(): List<Any?> = listOf(model, effort, fast, creditsEnabled) + limits.map { it.name to bucket(it.percent) }
    }

    val THRESHOLDS = listOf(50, 75, 90)
    fun bucket(percent: Int): Int = THRESHOLDS.count { percent >= it }

    private const val OPEN = "<session_state>"
    private const val CLOSE = "</session_state>"
    private const val WHO = "Added by the chat client, not typed by the user."

    fun snapshot(settings: JsonObject?, usage: JsonObject?, fastState: String?): Snapshot {
        val applied = settings?.get("applied") as? JsonObject
        val model = applied?.get("model")?.jsonPrimitive?.contentOrNull
        val effort = applied?.get("effort")?.jsonPrimitive?.contentOrNull
        val limits = ArrayList<Limit>()
        val rl = usage?.get("rate_limits") as? JsonObject
        fun pct(o: JsonObject?): Int? = o?.get("utilization")?.jsonPrimitive?.doubleOrNull?.toInt()
        fun at(o: JsonObject?): String? = o?.get("resets_at")?.jsonPrimitive?.contentOrNull
        (rl?.get("five_hour") as? JsonObject)?.let { o -> pct(o)?.let { limits += Limit("5-hour limit", it, at(o)) } }
        (rl?.get("seven_day") as? JsonObject)?.let { o -> pct(o)?.let { limits += Limit("weekly limit", it, at(o)) } }
        (rl?.get("model_scoped") as? JsonArray)?.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val name = o["display_name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            pct(o)?.let { limits += Limit("$name weekly limit", it, at(o)) }
        }
        val credits = ((rl?.get("extra_usage") ?: usage?.get("extra_usage")) as? JsonObject)
            ?.get("is_enabled")?.jsonPrimitive?.booleanOrNull
        val fast = when (fastState) { "on" -> true; "off", "cooldown" -> false; else -> null }
        return Snapshot(model, effort, fast, limits, credits)
    }

    /** The note's text, or null when there is nothing to say. */
    fun tag(s: Snapshot): String? {
        val head = listOfNotNull(
            s.model?.let { "model $it" },
            s.effort?.let { "effort $it" },
            s.fast?.let { if (it) "fast mode on" else "fast mode off" },
        )
        val use = s.limits.map { l ->
            val full = if (l.percent >= 100) {
                when (s.creditsEnabled) {
                    true -> " — used up; further use bills usage credits"
                    false -> " — used up; usage credits are off"
                    null -> " — used up"
                }
            } else ""
            "${l.name} ${l.percent}% used" + (l.resetsAt?.let { ", resets ${shortTime(it)}" } ?: "") + full
        }
        if (head.isEmpty() && use.isEmpty()) return null
        val lines = ArrayList<String>()
        if (head.isNotEmpty()) lines += "Now on: " + head.joinToString(", ") + "."
        if (use.isNotEmpty()) lines += "Plan usage: " + use.joinToString("; ") + "."
        lines += WHO
        return OPEN + lines.joinToString("\n") + CLOSE
    }

    /** `2026-11-27T22:59:59.584560+00:00` → `2026-11-27 22:59 UTC`; anything else unchanged. */
    fun shortTime(iso: String): String {
        val m = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})""").find(iso) ?: return iso
        val utc = iso.endsWith("+00:00") || iso.endsWith("Z")
        return m.groupValues[1] + " " + m.groupValues[2] + if (utc) " UTC" else ""
    }

    /** The prompt with the note after it (after a selection tag too: the note is always last). */
    fun append(prompt: String, tag: String): String = if (prompt.isBlank()) tag else prompt + "\n\n" + tag

    private val NOTE_RE = Regex("""(?:\n\n)?<session_state>[\s\S]*?</session_state>\s*$""")

    /** (text without the note, whether one was there) — for replay and titles. */
    fun split(text: String?): Pair<String?, Boolean> {
        if (text == null) return null to false
        val m = NOTE_RE.find(text) ?: return text to false
        return text.substring(0, m.range.first) to true
    }
}
