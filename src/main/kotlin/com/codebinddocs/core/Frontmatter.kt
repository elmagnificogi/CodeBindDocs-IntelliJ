package com.codebinddocs.core

data class CbdFrontmatter(
    var target: String,
    var kind: BindingKind,
    var startLine: Int? = null,
    var endLine: Int? = null,
    var symbol: String? = null,
    var contentHash: String? = null,
)

data class SplitMarkdown(
    val header: String?,
    val body: String,
)

data class ParsedFrontmatter(
    val meta: CbdFrontmatter?,
    val body: String,
)

private val FRONTMATTER_RE = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---\\r?\\n?")

private fun stripBom(markdown: String): String = markdown.replace(Regex("^\uFEFF"), "")

fun splitMarkdown(markdown: String): SplitMarkdown {
    var text = stripBom(markdown)
    val match = FRONTMATTER_RE.find(text) ?: return SplitMarkdown(null, text)
    var header = match.value
    if (!header.endsWith("\n")) {
        header += "\n"
    }
    var body = text.substring(match.value.length)
    while (FRONTMATTER_RE.containsMatchIn(body)) {
        body = body.replaceFirst(FRONTMATTER_RE, "")
    }
    return SplitMarkdown(header, body)
}

fun joinMarkdown(header: String?, body: String): String {
    val normalized = stripBom(body)
    return if (header == null) normalized else header + normalized
}

fun parseCbdFrontmatter(markdown: String): ParsedFrontmatter {
    val text = stripBom(markdown)
    val match = FRONTMATTER_RE.find(text) ?: return ParsedFrontmatter(null, text)
    var body = text.substring(match.value.length)
    while (FRONTMATTER_RE.containsMatchIn(body)) {
        body = body.replaceFirst(FRONTMATTER_RE, "")
    }
    return ParsedFrontmatter(parseCbdYaml(match.groupValues[1]), body)
}

private fun parseCbdYaml(yaml: String): CbdFrontmatter? {
    val lines = yaml.split(Regex("\\r?\\n"))
    var inCbd = false
    val values = linkedMapOf<String, String>()
    for (raw in lines) {
        val line = raw.replace("\t", "  ")
        if (!inCbd) {
            val trimmedEnd = line.trimEnd()
            if (Regex("^cbd:\\s*$").matches(trimmedEnd) || Regex("^cbd:\\s*\\S").containsMatchIn(line)) {
                inCbd = true
            }
            continue
        }
        if (Regex("^\\S").containsMatchIn(line) && !Regex("^\\s").containsMatchIn(raw)) {
            break
        }
        val m = Regex("^\\s+([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.*?)\\s*$").find(line) ?: continue
        var value = m.groupValues[2]
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        values[m.groupValues[1]] = value
    }
    val target = values["target"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val meta = CbdFrontmatter(
        target = normalizeRelPath(target),
        kind = BindingKind.fromYaml(values["kind"]),
    )
    values["startLine"]?.toIntOrNull()?.let { meta.startLine = it }
    values["endLine"]?.toIntOrNull()?.let { meta.endLine = it }
    values["symbol"]?.let { meta.symbol = it }
    values["contentHash"]?.let { meta.contentHash = it }
    return meta
}

fun serializeCbdFrontmatter(meta: CbdFrontmatter, body: String): String {
    val lines = mutableListOf("---", "cbd:", "  target: ${meta.target}", "  kind: ${meta.kind.yaml}")
    if (meta.kind == BindingKind.RANGE) {
        meta.startLine?.let { lines += "  startLine: $it" }
        meta.endLine?.let { lines += "  endLine: $it" }
    }
    meta.symbol?.let { lines += "  symbol: $it" }
    meta.contentHash?.let { lines += "  contentHash: $it" }
    lines += "---"
    lines += ""
    var normalizedBody = stripBom(body).replace(Regex("^\\r?\\n"), "")
    if (FRONTMATTER_RE.containsMatchIn(normalizedBody)) {
        normalizedBody = parseCbdFrontmatter(normalizedBody).body.replace(Regex("^\\r?\\n"), "")
    }
    return lines.joinToString("\n") + normalizedBody
}

fun frontmatterToBinding(docRelFromCbd: String, meta: CbdFrontmatter): Binding {
    val anchors = if (!meta.contentHash.isNullOrEmpty() || !meta.symbol.isNullOrEmpty()) {
        mutableListOf(BindingAnchor(symbol = meta.symbol, contentHash = meta.contentHash))
    } else {
        mutableListOf()
    }
    return Binding(
        id = normalizeRelPath(docRelFromCbd),
        doc = normalizeRelPath(docRelFromCbd),
        target = BindingTarget(
            path = meta.target,
            kind = meta.kind,
            startLine = meta.startLine,
            endLine = meta.endLine,
        ),
        anchors = anchors,
    )
}

fun bindingToFrontmatter(binding: Binding): CbdFrontmatter {
    val anchor = binding.anchors.firstOrNull()
    return CbdFrontmatter(
        target = normalizeRelPath(binding.target.path),
        kind = binding.target.kind,
        startLine = binding.target.startLine,
        endLine = binding.target.endLine,
        symbol = anchor?.symbol,
        contentHash = anchor?.contentHash,
    )
}
