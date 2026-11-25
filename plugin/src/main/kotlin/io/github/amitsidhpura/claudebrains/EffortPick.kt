package io.github.amitsidhpura.claudebrains

/**
 * 9.2, 2026-11-25: the effort level the user picked, read off the message that sets it.
 *
 * Since CLI 2.1.331 `/effort <level>` lasts for its process only ("(this session only)"): a new
 * panel, an IDE restart or a resume came back on the CLI's default while the slider showed High
 * (measured on 2.1.332, `_local/probe-effort-persist/`). So the plugin keeps the pick itself and
 * re-applies it at every start with `apply_flag_settings {effortLevel}`.
 *
 * Every way a level is set goes through a `/effort <level>` message — the slider, a model-switch
 * marker's effort, the user typing it — so this one check catches them all. Only the slider's
 * five levels count; `/effort` with nothing or anything else is left to the CLI and not saved.
 */
object EffortPick {
    val LEVELS = listOf("low", "medium", "high", "xhigh", "max")

    private val RE = Regex("""^\s*/effort\s+(\S+)\s*$""")

    /** The level a message sets, or null when it is not exactly `/effort <one of LEVELS>`. */
    fun parse(text: String): String? =
        RE.matchEntire(text)?.groupValues?.get(1)?.takeIf { it in LEVELS }
}
