package com.codebinddocs.core

data class IncludeSpec(
    var doc: String,
    var heading: String? = null,
    var startLine: Int? = null,
    var endLine: Int? = null,
)

fun parseIncludeMeta(block: String): IncludeSpec? {
    val spec = IncludeSpec(doc = "")
    for (raw in block.split(Regex("\\r?\\n"))) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (line.startsWith(">")) break
        val m = Regex("^(\\w+)\\s*:\\s*(.+)$").find(line) ?: continue
        val key = m.groupValues[1].lowercase()
        val value = m.groupValues[2].trim()
        when (key) {
            "doc", "path", "file" -> spec.doc = value.replace(Regex("^<|>$"), "")
            "heading", "section", "title" -> spec.heading = value.replace(Regex("^#+\\s*"), "")
            "lines", "line" -> {
                val lm = Regex("^(\\d+)\\s*[-–—]\\s*(\\d+)$").find(value)
                if (lm != null) {
                    spec.startLine = lm.groupValues[1].toInt()
                    spec.endLine = lm.groupValues[2].toInt()
                }
            }
            "startline", "start" -> spec.startLine = value.toIntOrNull()
            "endline", "end" -> spec.endLine = value.toIntOrNull()
        }
    }
    return if (spec.doc.isNotEmpty()) spec else null
}

fun serializeIncludeMeta(spec: IncludeSpec): String {
    val lines = mutableListOf("doc: ${spec.doc}")
    spec.heading?.let { lines += "heading: $it" }
    if (spec.startLine != null && spec.endLine != null) {
        lines += "lines: ${spec.startLine}-${spec.endLine}"
    }
    return lines.joinToString("\n")
}

fun collapseDocIncludes(markdown: String): String =
    replaceFences(markdown, "cbd-include-view", "cbd-include") { inner ->
        val spec = parseIncludeMeta(inner) ?: return@replaceFences null
        serializeIncludeMeta(spec)
    }

fun resolveIncludeDoc(currentDocRel: String, link: String, docsPath: String): String {
    val cleaned = normalizeRelPath(link.trim())
    if (cleaned.isEmpty()) return cleaned
    if (cleaned == docsPath || cleaned.startsWith("$docsPath/")) return cleaned
    if (!cleaned.contains('/')) return normalizeRelPath("$docsPath/$cleaned")
    val docDir = posixDirname(normalizeRelPath(currentDocRel))
    val base = if (docDir == ".") "" else docDir
    return posixJoin(base, cleaned)
}

fun extractHeadingSection(body: String, heading: String): String {
    val want = heading.trim().lowercase()
    val wantCompact = want.replace(Regex("[^\\w\\u4e00-\\u9fff]+"), "")
    val lines = body.split(Regex("\\r?\\n"))
    var start = -1
    var level = 0
    for (i in lines.indices) {
        val m = Regex("^(#{1,6})\\s+(.+?)\\s*$").find(lines[i]) ?: continue
        val title = m.groupValues[2].trim().lowercase()
        val titleCompact = title.replace(Regex("[^\\w\\u4e00-\\u9fff]+"), "")
        if (title == want || titleCompact == wantCompact) {
            start = i
            level = m.groupValues[1].length
            break
        }
    }
    if (start < 0) return "_未找到标题「$heading」_"
    val out = mutableListOf(lines[start])
    for (i in start + 1 until lines.size) {
        val m = Regex("^(#{1,6})\\s+").find(lines[i])
        if (m != null && m.groupValues[1].length <= level) break
        out += lines[i]
    }
    return out.joinToString("\n")
}

fun expandIncludeBody(rawDoc: String, spec: IncludeSpec): String {
    var body = splitMarkdown(rawDoc).body.replace(Regex("^\uFEFF"), "")
    spec.heading?.let { body = extractHeadingSection(body, it) }
    if (spec.startLine != null && spec.endLine != null) {
        val lines = body.split(Regex("\\r?\\n"))
        val start = maxOf(1, spec.startLine!!)
        val end = minOf(lines.size, spec.endLine!!)
        body = lines.subList(start - 1, end).joinToString("\n")
    }
    body = body.trimEnd()
    if (body.isBlank()) return "_（嵌入内容为空）_"
    val maxChars = 12_000
    return if (body.length > maxChars) "${body.take(maxChars)}\n\n_…已截断_" else body
}

fun formatExpandedInclude(spec: IncludeSpec, resolved: String, body: String): String {
    val meta = serializeIncludeMeta(spec.copy(doc = resolved))
    val extraHeading = spec.heading?.let { " · 标题「$it」" } ?: ""
    val extraLines = if (spec.startLine != null && spec.endLine != null) {
        " · L${spec.startLine}-${spec.endLine}"
    } else ""
    val note = "> **嵌入（只读）** `$resolved`$extraHeading$extraLines\n>\n"
    val quoted = body.split(Regex("\\r?\\n")).joinToString("\n") { "> $it" }
    return "$meta\n\n$note$quoted"
}

fun expandDocIncludes(
    markdown: String,
    currentDocRel: String,
    docsPath: String,
    loadDoc: (String) -> String?,
): String = replaceFences(markdown, "cbd-include", "cbd-include-view") { metaBlock ->
    val spec = parseIncludeMeta(metaBlock) ?: return@replaceFences null
    val resolved = resolveIncludeDoc(currentDocRel, spec.doc, docsPath)
    val raw = loadDoc(resolved)
    val body = if (raw == null) "_无法读取嵌入文档 `$resolved`_" else expandIncludeBody(raw, spec)
    formatExpandedInclude(spec, resolved, body)
}

fun replaceFences(
    markdown: String,
    inLang: String,
    outLang: String,
    replacer: (String) -> String?,
): String {
    val openRe = Regex("^```$inLang\\s*$")
    val lines = markdown.split(Regex("\\r?\\n"))
    val out = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        if (!openRe.matches(lines[i])) {
            out += lines[i]
            i++
            continue
        }
        val inner = mutableListOf<String>()
        i++
        while (i < lines.size && !Regex("^```\\s*$").matches(lines[i])) {
            inner += lines[i]
            i++
        }
        if (i < lines.size) i++
        val replacement = replacer(inner.joinToString("\n"))
        if (replacement == null) {
            out += "```$inLang"
            out += inner
            out += "```"
        } else {
            out += "```$outLang"
            out += replacement
            out += "```"
        }
    }
    return out.joinToString("\n")
}
