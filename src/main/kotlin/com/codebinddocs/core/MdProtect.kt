package com.codebinddocs.core

private const val ZWSP = '\u200B'

fun protectHrInFences(markdown: String): String =
    mapFenceLines(markdown) { line ->
        if (Regex("^---\\s*$").matches(line)) ZWSP + line else line
    }

fun unprotectHrInFences(markdown: String): String =
    markdown.replace(Regex("^${ZWSP}---\\s*$", RegexOption.MULTILINE), "---")

private fun mapFenceLines(markdown: String, mapInside: (String) -> String): String {
    val lines = markdown.split(Regex("\\r?\\n")).toMutableList()
    var inFence = false
    var fenceMarker = ""
    for (i in lines.indices) {
        val line = lines[i]
        val open = Regex("^(`{3,}|~{3,})(.*)$").find(line)
        if (open != null) {
            val marker = open.groupValues[1][0]
            val len = open.groupValues[1].length
            if (!inFence) {
                inFence = true
                fenceMarker = marker.toString().repeat(len)
            } else if (line.startsWith(fenceMarker) && Regex("^[`~]+$").matches(line.trim())) {
                inFence = false
                fenceMarker = ""
            }
            continue
        }
        if (inFence) {
            lines[i] = mapInside(line)
        }
    }
    return lines.joinToString("\n")
}
