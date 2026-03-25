package com.remodex.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CommandExecutionDetails(
    val fullCommand: String = "",
    val cwd: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    var outputTail: String = ""
) {
    companion object {
        private const val MAX_TAIL_LINES = 30
    }

    fun appendOutput(text: String) {
        outputTail = if (outputTail.isEmpty()) text else "$outputTail$text"
        trimOutputTail()
    }

    fun trimOutputTail() {
        val lines = outputTail.lines()
        if (lines.size > MAX_TAIL_LINES) {
            outputTail = lines.takeLast(MAX_TAIL_LINES).joinToString("\n")
        }
    }
}
