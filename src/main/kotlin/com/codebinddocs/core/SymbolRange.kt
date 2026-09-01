package com.codebinddocs.core

data class LineSpan(val startLine: Int, val endLine: Int)

fun findSymbolLine(text: String, symbol: String): Int? {
    val escaped = Regex.escape(symbol)
    val patterns = listOf(
        Regex("(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s+$escaped\\b"),
        Regex("(?:export\\s+)?(?:abstract\\s+)?class\\s+$escaped\\b"),
        Regex("(?:export\\s+)?(?:async\\s+)?function\\*?\\s+$escaped\\b"),
        Regex("(?:export\\s+)?(?:const|let|var)\\s+$escaped\\b"),
        Regex("(?:export\\s+)?(?:type|interface|enum)\\s+$escaped\\b"),
        Regex("$escaped\\s*=\\s*(?:async\\s*)?(?:function|\\()"),
        Regex("(?:fun|class|object|interface|enum class|data class|sealed class)\\s+$escaped\\b"),
        Regex("(?:def|class)\\s+$escaped\\b"),
        Regex("(?:public|private|protected|internal|open|override|suspend|abstract|final)?\\s*(?:fun|class|object|interface)\\s+$escaped\\b"),
        Regex("(?:public|private|protected)?\\s*(?:static\\s+)?(?:class|interface|enum|record)\\s+$escaped\\b"),
        Regex("(?:func|fn)\\s+$escaped\\b"),
        // Java / C# 方法：返回类型 + 名字(，且不是 obj.name( 调用
        Regex("(?<![.\\w])$escaped\\s*\\("),
    )
    val lines = text.split(Regex("\\r?\\n"))
    for (i in lines.indices) {
        val trimmed = lines[i].trim()
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue
        if (patterns.any { it.containsMatchIn(lines[i]) }) return i + 1
    }
    return null
}

fun findBraceBlockEnd(lines: List<String>, startLine1Based: Int): Int? {
    var depth = 0
    var started = false
    for (i in (startLine1Based - 1) until lines.size) {
        val text = stripLineCommentsAndStrings(lines[i])
        for (ch in text) {
            when (ch) {
                '{' -> {
                    depth++
                    started = true
                }
                '}' -> {
                    if (!started) continue
                    depth--
                    if (depth == 0) return i + 1
                }
            }
        }
    }
    return null
}

fun resolveSymbolLineRangeFromText(
    text: String,
    symbol: String,
    previousSpan: Int? = null,
): LineSpan? {
    val startLine = findSymbolLine(text, symbol) ?: return null
    val lines = text.split(Regex("\\r?\\n"))
    val braced = findBraceBlockEnd(lines, startLine)
    if (braced != null) return LineSpan(startLine, braced)
    val span = if (previousSpan != null && previousSpan >= 0) previousSpan else 0
    return LineSpan(startLine, startLine + span)
}

fun stripLineCommentsAndStrings(line: String): String {
    val out = StringBuilder()
    var inSingle = false
    var inDouble = false
    var inTemplate = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        val next = line.getOrNull(i + 1)
        if (!inSingle && !inDouble && !inTemplate && ch == '/' && next == '/') break
        if (!inDouble && !inTemplate && ch == '\'' && line.getOrNull(i - 1) != '\\') {
            inSingle = !inSingle
            i++
            continue
        }
        if (!inSingle && !inTemplate && ch == '"' && line.getOrNull(i - 1) != '\\') {
            inDouble = !inDouble
            i++
            continue
        }
        if (!inSingle && !inDouble && ch == '`' && line.getOrNull(i - 1) != '\\') {
            inTemplate = !inTemplate
            i++
            continue
        }
        if (!inSingle && !inDouble && !inTemplate) out.append(ch)
        i++
    }
    return out.toString()
}

fun stripSymbolNoise(name: String): String =
    name.replace(Regex("\\(.*\\)$"), "").replace(Regex("\\s+"), "").trim()

private val CONTROL_CALLS = setOf(
    "if", "for", "while", "switch", "catch", "synchronized", "with", "when",
    "using", "foreach", "assert", "return", "throw", "new", "match", "elif",
    "elseif", "unless", "until", "guard", "function",
)

private val SIGNATURE_HINT = Regex(
    """\b(public|private|protected|internal|static|final|abstract|override|suspend|fun|def|func|fn|void|async|export|class|interface|enum|record|struct|native|default|virtual|sealed|open)\b""",
)

fun guessDeclName(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() ||
        trimmed.startsWith("@") ||
        trimmed.startsWith("#") ||
        trimmed.startsWith("//") ||
        trimmed.startsWith("*") ||
        trimmed.startsWith("/*")
    ) {
        return null
    }
    val patterns = listOf(
        Regex("(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)"),
        Regex("(?:export\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)"),
        Regex("(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)"),
        Regex("(?:export\\s+)?(?:type|interface|enum)\\s+([A-Za-z_$][\\w$]*)"),
        Regex("(?:fun|class|object|interface|enum class|data class)\\s+([A-Za-z_][\\w]*)"),
        Regex("(?:def|class)\\s+([A-Za-z_][\\w]*)"),
        Regex("(?:public|private|protected)?\\s*(?:static\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_][\\w]*)"),
    )
    for (re in patterns) {
        val m = re.find(trimmed)
        if (m != null) return m.groupValues[1]
    }
    if (SIGNATURE_HINT.containsMatchIn(trimmed) || trimmed.endsWith("{")) {
        val m = Regex("""\b([A-Za-z_][\w]*)\s*\(""").find(trimmed) ?: return null
        val name = m.groupValues[1]
        if (name.lowercase() in CONTROL_CALLS) return null
        return name
    }
    return null
}

fun suggestSymbolFromLines(lines: List<String>, startLine: Int, endLine: Int): String? {
    val from = (startLine - 1).coerceAtLeast(0)
    val to = endLine.coerceAtMost(lines.size)
    for (i in from until to) {
        val guessed = guessDeclName(lines[i])
        if (guessed != null) return guessed
    }
    return null
}
