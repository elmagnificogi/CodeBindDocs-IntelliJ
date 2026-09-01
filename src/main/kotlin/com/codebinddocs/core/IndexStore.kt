package com.codebinddocs.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

class IndexStore(
    val workspaceRoot: Path,
    var config: CbdConfig = CbdConfig(),
) {
    private var cache: CbdIndex? = null
    private var cacheMs: Long = 0

    val docsPath: String get() = config.effectiveDocsPath
    val assetsPath: String get() = config.effectiveAssetsPath
    val templatesPath: String get() = config.effectiveTemplatesPath
    val indexDocPath: String get() = config.indexDocPath

    fun workspacePath(rel: String): Path {
        val parts = normalizeRelPath(rel).split('/').filter { it.isNotEmpty() && it != "." }
        return parts.fold(workspaceRoot) { acc, part -> acc.resolve(part) }
    }

    fun toWorkspaceRelative(path: Path): String? {
        val root = workspaceRoot.toAbsolutePath().normalize()
        val abs = path.toAbsolutePath().normalize()
        if (!abs.startsWith(root)) return null
        val rel = root.relativize(abs).toString()
        if (rel.isEmpty()) return null
        return normalizeRelPath(rel)
    }

    fun invalidateCache() {
        cache = null
        cacheMs = 0
    }

    fun isUnderDocsPath(rel: String): Boolean = isUnderDocsPath(rel, docsPath)

    fun isIndexDoc(rel: String): Boolean = normalizeRelPath(rel) == indexDocPath

    fun exists(): Boolean = workspacePath(docsPath).exists()

    fun ensureLayout() {
        Files.createDirectories(workspacePath(docsPath))
        Files.createDirectories(workspacePath(assetsPath))
        Files.createDirectories(workspacePath(templatesPath))
    }

    fun read(): CbdIndex {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheMs < 500) return it }
        val index = emptyIndex()
        val docsRoot = workspacePath(docsPath)
        if (!docsRoot.exists()) {
            cache = index
            cacheMs = now
            return index
        }
        val files = listMarkdownFiles(docsRoot)
        for (file in files) {
            val rel = toWorkspaceRelative(file) ?: continue
            if (!isUnderDocsPath(rel) || isIndexDoc(rel)) continue
            if (rel == templatesPath || rel.startsWith("$templatesPath/")) continue
            try {
                val text = file.readText(Charsets.UTF_8)
                val meta = parseCbdFrontmatter(text).meta ?: continue
                index.bindings += frontmatterToBinding(rel, meta)
            } catch (_: Exception) {
            }
        }
        index.bindings.sortBy { it.target.path }
        cache = index
        cacheMs = now
        return index
    }

    fun writeBinding(
        binding: Binding,
        refreshIndex: Boolean = true,
        title: String? = null,
        body: String? = null,
    ) {
        val uri = workspacePath(binding.doc)
        val fallbackTitle = title ?: binding.target.path.substringAfterLast('/').ifEmpty { binding.target.path }
        var nextBody = body ?: defaultDocBody(fallbackTitle)
        var existed = false
        if (uri.exists() && uri.isRegularFile()) {
            existed = true
            nextBody = parseCbdFrontmatter(uri.readText(Charsets.UTF_8)).body.ifBlank { nextBody }
        } else {
            Files.createDirectories(uri.parent)
        }
        if (!existed && body != null) {
            nextBody = body
        }
        uri.writeText(serializeCbdFrontmatter(bindingToFrontmatter(binding), nextBody), Charsets.UTF_8)
        invalidateCache()
        if (refreshIndex) writeDocsIndex()
    }

    fun deleteDoc(docWorkspaceRel: String) {
        val rel = normalizeRelPath(docWorkspaceRel)
        if (!isUnderDocsPath(rel)) throw IllegalArgumentException("文档不在文档目录内: $rel")
        if (isIndexDoc(rel)) throw IllegalArgumentException("不能删除自动生成的 cbd-index.md")
        val path = workspacePath(rel)
        if (!path.exists()) throw IllegalArgumentException("删除失败: 文件不存在")
        val deleted = tryMoveToTrash(path.toFile()) || path.toFile().delete()
        if (!deleted) throw IllegalArgumentException("删除失败")
        invalidateCache()
        writeDocsIndex()
    }

    fun writeDocsIndex(): Path {
        ensureLayout()
        invalidateCache()
        val index = read()
        val path = workspacePath(indexDocPath)
        path.writeText(renderDocsIndex(docsPath, index.bindings), Charsets.UTF_8)
        return path
    }

    fun updateTargetPath(oldPath: String, newPath: String): Int {
        val index = read()
        val oldNorm = normalizeRelPath(oldPath).trimEnd('/')
        val newNorm = normalizeRelPath(newPath).trimEnd('/')
        if (oldNorm.isEmpty() || oldNorm == newNorm) return 0
        var updated = 0
        for (binding in index.bindings) {
            val cur = normalizeRelPath(binding.target.path)
            val next = when {
                cur == oldNorm -> newNorm
                cur.startsWith("$oldNorm/") -> newNorm + cur.substring(oldNorm.length)
                else -> null
            }
            if (next != null && next != cur) {
                binding.target.path = next
                writeBinding(binding)
                updated++
            }
        }
        return updated
    }

    fun hashFileContent(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.readBytes())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun listDocTemplates(): List<DocTemplateChoice> {
        val dir = workspacePath(templatesPath)
        if (!dir.exists() || !dir.isDirectory()) return builtinDocTemplates()
        val files = Files.walk(dir).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".md", true) }
                .sorted()
                .collect(Collectors.toList())
        }
        if (files.isEmpty()) return builtinDocTemplates()
        val loaded = files.mapNotNull { file ->
            val rel = toWorkspaceRelative(file) ?: return@mapNotNull null
            try {
                parseTemplateFile(file.readText(Charsets.UTF_8), rel)
            } catch (_: Exception) {
                null
            }
        }
        return loaded.ifEmpty { builtinDocTemplates() }
    }

    fun ensureDefaultTemplates(): Int {
        val dir = workspacePath(templatesPath)
        Files.createDirectories(dir)
        val existing = Files.walk(dir).use { stream ->
            stream.anyMatch { it.isRegularFile() && it.fileName.toString().endsWith(".md", true) }
        }
        if (existing) return 0
        var n = 0
        for (t in builtinDocTemplates()) {
            workspacePath("$templatesPath/${t.id}.md").writeText(serializeTemplateFile(t), Charsets.UTF_8)
            n++
        }
        return n
    }

    fun scanBindableSources(): List<String> {
        if (!workspaceRoot.exists()) return emptyList()
        return Files.walk(workspaceRoot).use { stream ->
            stream.filter { it.isRegularFile() }
                .map { toWorkspaceRelative(it) }
                .filter { rel -> rel != null && isLikelySourceFile(rel) && isBindableSourceRel(rel, docsPath) }
                .map { it!! }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    fun scanCoverage(index: CbdIndex = read()): CoverageReport {
        val bound = index.bindings
            .filter { it.target.kind != BindingKind.DIRECTORY }
            .map { normalizeRelPath(it.target.path) }
        return computeCoverage(bound, scanBindableSources(), docsPath)
    }

    companion object {
        fun listMarkdownFiles(dir: Path): List<Path> {
            if (!dir.exists() || !dir.isDirectory()) return emptyList()
            return Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".md", true) }
                    .collect(Collectors.toList())
            }
        }
    }
}

private fun tryMoveToTrash(file: java.io.File): Boolean {
    return try {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
            desktop.moveToTrash(file)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}
