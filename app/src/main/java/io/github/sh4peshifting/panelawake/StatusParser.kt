package io.github.sh4peshifting.panelawake

/** Observed physical power is independent from Android's logical wakefulness. */
object StatusParser {
    data class State(
        val wakefulness: String = "Unknown",
        val powered: Boolean? = null,
        val staySetting: Int = -1,
        val physicalId: String = "",
        val panelMode: Int = -1,
    ) {
        val awake get() = wakefulness == "Awake"
    }

    private data class Panel(val id: String, val primary: Boolean, var mode: Int = -1)
    private val displayHeader = Regex("DisplayDevice\\{(\\d+),([^}]*)\\}")
    private val numericMode = Regex("^(?:[A-Za-z_]+\\s*\\()?([0-4])(?:\\)|\\s|$)")

    fun parse(power: String, surface: String, stay: Int): State {
        val wake = Regex("(?m)^\\s*mWakefulness=(\\w+)").find(power)?.groupValues?.get(1) ?: "Unknown"
        val powered = Regex("(?m)^\\s*mIsPowered=(true|false)").find(power)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        val panels = mutableListOf<Panel>()
        var current: Panel? = null
        surface.lineSequence().forEach { line ->
            val header = displayHeader.find(line)
            if (header != null) {
                val flags = header.groupValues[2].split(',').map { it.trim().lowercase() }
                current = if ("internal" in flags) {
                    Panel(header.groupValues[1], "primary" in flags).also(panels::add)
                } else null
            } else if (line.trim().startsWith("+ ")) {
                current = null
            }
            if (line.contains("powerMode=")) current?.mode = parseMode(line.substringAfter("powerMode=").trim())
        }
        val primary = panels.filter { it.primary }
        val selected = primary.singleOrNull() ?: panels.singleOrNull().takeIf { primary.isEmpty() }
        return State(wake, powered, stay, selected?.id.orEmpty(), selected?.mode ?: -1)
    }

    private fun parseMode(value: String): Int = numericMode.find(value)?.groupValues?.get(1)?.toInt()
        ?: when (value.substringBefore(' ').substringBefore('(').uppercase()) {
            "OFF" -> 0
            "DOZE" -> 1
            "ON", "NORMAL" -> 2
            "DOZE_SUSPEND" -> 3
            "ON_SUSPEND" -> 4
            else -> -1
        }
}
