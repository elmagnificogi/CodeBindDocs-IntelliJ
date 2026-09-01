package com.codebinddocs.core

const val DEFAULT_DOCS_PATH = "docs/cbd"
const val INDEX_FILE_NAME = "cbd-index.md"

data class CbdConfig(
    val docsPath: String = DEFAULT_DOCS_PATH,
    val assetsPath: String = "",
    val templatesPath: String = "",
) {
    val effectiveDocsPath: String
        get() = normalizeRelPath(docsPath.ifBlank { DEFAULT_DOCS_PATH }).trimEnd('/').ifEmpty { DEFAULT_DOCS_PATH }

    val effectiveAssetsPath: String
        get() = assetsPath.trim().takeIf { it.isNotEmpty() }?.let { normalizeRelPath(it).trimEnd('/') }
            ?: "$effectiveDocsPath/assets"

    val effectiveTemplatesPath: String
        get() = templatesPath.trim().takeIf { it.isNotEmpty() }?.let { normalizeRelPath(it).trimEnd('/') }
            ?: "$effectiveDocsPath/_templates"

    val indexDocPath: String get() = "$effectiveDocsPath/$INDEX_FILE_NAME"
}

private val SKIP_PREFIXES = listOf(
    "node_modules/",
    "out/",
    "dist/",
    "build/",
    ".git/",
    "media/vditor/",
    ".vscode/",
    ".idea/",
    ".gradle/",
    "webview/vditor/",
)

val BINDABLE_EXTENSIONS = setOf(
    "ts", "tsx", "js", "jsx", "mjs", "cjs", "py", "go", "rs", "java", "kt", "kts", "cs",
    "c", "h", "cpp", "hpp", "cc", "swift", "rb", "php", "scala", "groovy", "gradle",
)

fun isUnderDocsPath(rel: String, docsPath: String): Boolean {
    val norm = normalizeRelPath(rel)
    val root = normalizeRelPath(docsPath).trimEnd('/')
    return norm == root || norm.startsWith("$root/")
}

fun isBindableSourceRel(rel: String, docsPath: String): Boolean {
    val norm = normalizeRelPath(rel)
    if (norm.isEmpty() || isUnderDocsPath(norm, docsPath)) return false
    val lower = norm.lowercase()
    if (SKIP_PREFIXES.any { lower.startsWith(it) || lower.contains("/$it") }) return false
    if (Regex("\\.(png|jpe?g|gif|webp|ico|woff2?|ttf|eot|map|vsix|jar|zip)$", RegexOption.IGNORE_CASE).containsMatchIn(norm)) {
        return false
    }
    if (norm == "package-lock.json") return false
    return true
}

fun isBindableDirectoryRel(rel: String, docsPath: String): Boolean {
    val norm = normalizeRelPath(rel)
    if (norm.isEmpty() || isUnderDocsPath(norm, docsPath)) return false
    val lower = "$norm/"
    return SKIP_PREFIXES.none { lower.startsWith(it) || lower.contains("/$it") }
}

fun isLikelySourceFile(rel: String): Boolean {
    val ext = rel.substringAfterLast('.', "").lowercase()
    return ext in BINDABLE_EXTENSIONS
}

data class CoverageReport(
    val boundCount: Int,
    val unbound: List<String>,
    val total: Int,
)

fun computeCoverage(boundPaths: Collection<String>, scannedSources: Collection<String>, docsPath: String): CoverageReport {
    val bound = boundPaths.map { normalizeRelPath(it) }.toSet()
    val unbound = scannedSources
        .map { normalizeRelPath(it) }
        .filter { isBindableSourceRel(it, docsPath) && it !in bound }
        .sorted()
    val boundExisting = bound.filter { isBindableSourceRel(it, docsPath) }
    return CoverageReport(
        boundCount = boundExisting.size,
        unbound = unbound,
        total = boundExisting.size + unbound.size,
    )
}

fun findByTargetPath(index: CbdIndex, targetPath: String): Binding? {
    val all = findBindingsForTarget(index, targetPath)
    return all.firstOrNull { it.target.kind == BindingKind.FILE } ?: all.firstOrNull()
}

fun findBindingsForTarget(index: CbdIndex, targetPath: String): List<Binding> {
    val norm = normalizeRelPath(targetPath)
    return index.bindings.filter { normalizeRelPath(it.target.path) == norm }
}

fun findDirectoryBindingForRel(index: CbdIndex, rel: String): Binding? {
    val norm = normalizeRelPath(rel)
    val candidates = index.bindings.filter { b ->
        b.target.kind == BindingKind.DIRECTORY &&
            (normalizeRelPath(b.target.path) == norm || norm.startsWith("${normalizeRelPath(b.target.path)}/"))
    }
    return candidates.maxByOrNull { normalizeRelPath(it.target.path).length }
}

fun resolveBindingForLine(index: CbdIndex, targetPath: String, line1Based: Int): Binding? {
    val forFile = findBindingsForTarget(index, targetPath)
    if (forFile.isEmpty()) return null
    val covering = forFile.filter { b ->
        b.target.kind == BindingKind.RANGE &&
            b.target.startLine != null &&
            b.target.endLine != null &&
            line1Based >= b.target.startLine!! &&
            line1Based <= b.target.endLine!!
    }.sortedBy { it.target.endLine!! - it.target.startLine!! }
    return covering.firstOrNull() ?: forFile.firstOrNull { it.target.kind == BindingKind.FILE }
}

fun findByDocPath(index: CbdIndex, docPath: String): Binding? {
    val norm = normalizeRelPath(docPath)
    return index.bindings.firstOrNull { normalizeRelPath(it.doc) == norm }
}

fun suggestDocPath(docsPath: String, targetRel: String, directory: Boolean = false): String {
    val base = targetRel
        .replace(Regex("^src/"), "")
        .replace(Regex("\\.[^.]+$"), "")
        .replace('/', '-')
    val suffix = if (directory) "-README" else ""
    return "$docsPath/${base.ifEmpty { "untitled" }}$suffix.md"
}

fun renderDocsIndex(docsPath: String, bindings: List<Binding>): String {
    val indexPath = "$docsPath/$INDEX_FILE_NAME"
    val lines = mutableListOf(
        "# CodeBind Docs 文档汇总",
        "",
        "共 **${bindings.size}** 个绑定。由 CodeBind Docs 自动生成，请勿手改（绑定变更后会覆盖）。",
        "",
        "文档目录：`$docsPath/`（设置项 `cbd.docsPath`）。",
        "",
        "绑定声明在各文档 YAML 头的 `cbd.target`；本页仅作目录。",
        "",
        "| 源文件 | 文档 | 类型 |",
        "| --- | --- | --- |",
    )
    for (b in bindings) {
        val srcLink = relativeMarkdownLink(indexPath, b.target.path)
        val docLink = relativeMarkdownLink(indexPath, b.doc)
        var kind = b.target.kind.yaml
        if (b.target.kind == BindingKind.RANGE && b.target.startLine != null && b.target.endLine != null) {
            val sym = b.anchors.firstOrNull()?.symbol
            kind = if (sym != null) {
                "range L${b.target.startLine}-${b.target.endLine} ($sym)"
            } else {
                "range L${b.target.startLine}-${b.target.endLine}"
            }
        }
        lines += "| [`${b.target.path}`]($srcLink) | [`${b.doc}`]($docLink) | $kind |"
    }
    if (bindings.isEmpty()) {
        lines += "| _暂无_ | | |"
    }
    lines += ""
    lines += "## 快捷操作"
    lines += ""
    lines += "- 命令：`CBD: Open Docs Index` 打开本页"
    lines += "- 命令：`CBD: Bind Doc to Current File` 为当前源文件创建绑定"
    lines += "- 命令：`CBD: Delete Bound Doc` 删除绑定文档"
    lines += "- 侧栏 **CodeBind Docs → Bindings** 可跳转源码 / 文档 / 删除"
    lines += ""
    return lines.joinToString("\n")
}
