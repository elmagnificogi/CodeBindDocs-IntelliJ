package com.codebinddocs.core

import java.nio.file.Path

fun posixDirname(rel: String): String {
    val norm = normalizeRelPath(rel)
    val idx = norm.lastIndexOf('/')
    return if (idx < 0) "" else norm.substring(0, idx)
}

fun posixJoin(base: String, child: String): String {
    val cleanedChild = normalizeRelPath(child)
    if (base.isEmpty() || base == ".") return cleanedChild
    return normalizeRelPath("$base/$cleanedChild")
}

fun posixRelative(fromDir: String, toFile: String): String {
    val fromParts = if (fromDir.isEmpty() || fromDir == ".") emptyList() else fromDir.split('/')
    val toParts = normalizeRelPath(toFile).split('/')
    var i = 0
    while (i < fromParts.size && i < toParts.size - 1 && fromParts[i] == toParts[i]) {
        i++
    }
    val ups = fromParts.size - i
    val down = toParts.drop(i).joinToString("/")
    return when {
        ups == 0 -> down.ifEmpty { Path.of(toFile).fileName.toString() }
        else -> "../".repeat(ups) + down
    }
}

fun resolveFromDoc(docRel: String, link: String): String {
    val cleaned = normalizeRelPath(link.trim().replace(Regex("^<|>$"), ""))
    if (cleaned.isEmpty() || Regex("^(https?:|data:|vscode-webview:|jbcef:|file:)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) {
        return cleaned
    }
    if (cleaned.startsWith("/")) {
        return normalizeRelPath(cleaned.substring(1))
    }
    val docDir = posixDirname(docRel)
    return posixJoin(if (docDir == ".") "" else docDir, cleaned)
}

fun relativeToDoc(docRel: String, targetRel: String): String {
    val docDir = posixDirname(docRel)
    val base = if (docDir == ".") "" else docDir
    val rel = posixRelative(base, targetRel)
    return rel.ifEmpty { Path.of(targetRel).fileName.toString() }
}

private val MD_IMAGE_RE = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")

fun rewriteImagesForWebview(
    markdown: String,
    docRel: String,
    toWebviewUri: (String) -> String,
): Pair<String, MutableMap<String, String>> {
    val reverse = linkedMapOf<String, String>()
    val out = MD_IMAGE_RE.replace(markdown) { match ->
        val full = match.value
        val url = match.groupValues[2]
        val trimmed = url.trim()
        if (Regex("^(https?:|data:|vscode-webview:|jbcef:|file:)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return@replace full
        }
        val targetRel = resolveFromDoc(docRel, trimmed)
        if (targetRel.isEmpty()) return@replace full
        val web = toWebviewUri(targetRel)
        reverse[web] = relativeToDoc(docRel, targetRel)
        full.replace(url, web)
    }
    return out to reverse
}

fun rewriteImagesForDisk(markdown: String, reverse: Map<String, String>): String {
    if (reverse.isEmpty()) return markdown
    var out = markdown
    for ((web, rel) in reverse) {
        if (web.isNotEmpty()) {
            out = out.replace(web, rel)
        }
    }
    return out
}

fun safeAssetFileName(original: String, fallbackExt: String = "png"): String {
    val base = Path.of(original.replace('\\', '/')).fileName.toString().ifEmpty { "paste.$fallbackExt" }
    val cleaned = base.replace(Regex("[^\\w.\\-()+]+"), "_").replace(Regex("^\\.+"), "")
    if (cleaned.isEmpty() || cleaned == "_" || cleaned == ".") {
        return "paste-${System.currentTimeMillis()}.$fallbackExt"
    }
    return if (!cleaned.contains('.')) "$cleaned.$fallbackExt" else cleaned
}

fun relativeMarkdownLink(fromFile: String, toFile: String): String {
    val from = normalizeRelPath(fromFile)
    val to = normalizeRelPath(toFile)
    val fromDir = if (from.contains('/')) from.substring(0, from.lastIndexOf('/')) else ""
    val rel = posixRelative(fromDir, to)
    return if (rel.startsWith("../") || rel.startsWith("./")) rel else "./$rel"
}
