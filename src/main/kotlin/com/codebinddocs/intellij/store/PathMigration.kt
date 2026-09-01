package com.codebinddocs.intellij.store

import com.codebinddocs.core.INDEX_FILE_NAME
import com.codebinddocs.core.MigrationResult
import com.codebinddocs.core.normalizeRelPath
import com.codebinddocs.core.parseCbdFrontmatter
import com.codebinddocs.core.planMoves
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.CbdUi
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

object PathMigration {
    fun checkOnStartup(svc: CbdProjectService) {
        val store = svc.storeOrNull() ?: return
        val st = svc.settings.stored
        checkOne(svc, "docsPath", st.lastDocsPath, store.docsPath) { collectDocsPathRelPaths(store.workspaceRoot, it) }
        checkOne(svc, "assetsPath", st.lastAssetsPath, store.assetsPath) { collectWholeFolder(store.workspaceRoot, it) }
        checkOne(svc, "templatesPath", st.lastTemplatesPath, store.templatesPath) { collectWholeFolder(store.workspaceRoot, it) }
    }

    private fun checkOne(
        svc: CbdProjectService,
        setting: String,
        last: String?,
        current: String,
        collect: (String) -> List<String>,
    ) {
        val store = svc.storeOrNull() ?: return
        if (last == null) {
            remember(svc, setting, current)
            return
        }
        if (normalizeRelPath(last) == normalizeRelPath(current)) return
        val oldNorm = normalizeRelPath(last).trimEnd('/')
        val oldPath = store.workspacePath(oldNorm)
        if (!oldPath.exists()) {
            remember(svc, setting, current)
            return
        }
        val label = when (setting) {
            "docsPath" -> "文档目录 (cbd.docsPath)"
            "assetsPath" -> "资源目录 (cbd.assetsPath)"
            else -> "模板目录 (cbd.templatesPath)"
        }
        val go = CbdUi.choose(
            svc.project,
            "CBD: 检测到${label}由「$last」改为「$current」。是否将旧目录下的内容自动迁移过去？",
            "迁移",
            "暂不迁移",
        )
        remember(svc, setting, current)
        if (go != "迁移") return
        val newNorm = normalizeRelPath(current).trimEnd('/')
        val result = migrate(store.workspaceRoot, collect(oldNorm), oldNorm, newNorm)
        store.invalidateCache()
        store.writeDocsIndex()
        val extra = if (result.conflicts.isNotEmpty()) "；${result.conflicts.size} 个因目标已存在被跳过" else ""
        CbdUi.info(svc.project, "CBD: 已迁移 ${result.moved} 个文件到 $current/$extra")
    }

    private fun remember(svc: CbdProjectService, setting: String, value: String) {
        when (setting) {
            "docsPath" -> svc.settings.stored.lastDocsPath = value
            "assetsPath" -> svc.settings.stored.lastAssetsPath = value
            "templatesPath" -> svc.settings.stored.lastTemplatesPath = value
        }
    }

    private fun migrate(root: Path, relPaths: List<String>, oldNorm: String, newNorm: String): MigrationResult {
        val existingDest = relPaths.map { normalizeRelPath(it) }
            .filter { it == oldNorm || it.startsWith("$oldNorm/") }
            .map { newNorm + it.substring(oldNorm.length) }
            .filter { root.resolve(it.replace('/', java.io.File.separatorChar)).exists() }
            .toSet()
        val plan = planMoves(relPaths, oldNorm, newNorm) { existingDest.contains(it) }
        var moved = 0
        for ((from, to) in plan.moves) {
            val src = resolve(root, from)
            val dest = resolve(root, to)
            Files.createDirectories(dest.parent)
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE)
            moved++
        }
        return MigrationResult(moved, plan.conflicts)
    }

    private fun resolve(root: Path, rel: String): Path =
        rel.split('/').filter { it.isNotEmpty() }.fold(root) { acc, p -> acc.resolve(p) }

    private fun collectWholeFolder(root: Path, oldNorm: String): List<String> {
        val dir = resolve(root, oldNorm)
        if (!dir.exists()) return emptyList()
        return Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace('\\', '/') }.toList()
        }
    }

    private fun collectDocsPathRelPaths(root: Path, oldNorm: String): List<String> {
        val dir = resolve(root, oldNorm)
        if (!dir.exists() || !dir.isDirectory()) return emptyList()
        val templatesRoot = "$oldNorm/_templates"
        val indexPath = "$oldNorm/$INDEX_FILE_NAME"
        val out = mutableListOf<String>()
        Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md", true) }.forEach { file ->
                val rel = root.relativize(file).toString().replace('\\', '/')
                when {
                    rel == indexPath -> out += rel
                    rel == templatesRoot || rel.startsWith("$templatesRoot/") -> Unit
                    else -> {
                        try {
                            if (parseCbdFrontmatter(file.readText(Charsets.UTF_8)).meta != null) out += rel
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        for (sub in listOf("_templates", "assets")) {
            out += collectWholeFolder(root, "$oldNorm/$sub")
        }
        return out
    }
}
