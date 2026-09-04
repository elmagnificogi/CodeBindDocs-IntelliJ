package com.codebinddocs.intellij

import com.codebinddocs.core.Binding
import com.codebinddocs.core.BindingAnchor
import com.codebinddocs.core.BindingKind
import com.codebinddocs.core.BindingTarget
import com.codebinddocs.core.IndexStore
import com.codebinddocs.core.applyDocTemplate
import com.codebinddocs.core.findBindingsForTarget
import com.codebinddocs.core.findByDocPath
import com.codebinddocs.core.isBindableDirectoryRel
import com.codebinddocs.core.normalizeRelPath
import com.codebinddocs.core.suggestDocPath
import com.codebinddocs.core.suggestSymbolFromLines
import com.codebinddocs.core.findOverlapsWithExisting
import com.codebinddocs.intellij.drift.refreshBindingHash
import com.codebinddocs.intellij.editor.SymbolSuggest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText

class CbdCommands(private val svc: CbdProjectService) {
    private val project get() = svc.project
    private var modalInProgress = false
    private var modalCooldownUntil = 0L

    private fun beginModalCmd(): Boolean {
        if (modalInProgress || System.currentTimeMillis() < modalCooldownUntil) return false
        modalInProgress = true
        return true
    }

    private fun endModalCmd() {
        modalInProgress = false
        modalCooldownUntil = System.currentTimeMillis() + 1500
    }

    private fun runModalCmd(block: () -> Unit) {
        if (!beginModalCmd()) return
        try {
            block()
        } finally {
            endModalCmd()
        }
    }

    fun initialize() {
        val store = svc.store() ?: return
        store.ensureLayout()
        svc.scaffoldAgentFiles(store.workspaceRoot)
        val templatesWritten = store.ensureDefaultTemplates()
        if (store.read().bindings.isEmpty()) {
            val welcome = store.workspacePath("${store.docsPath}/welcome.md")
            if (!welcome.exists()) {
                welcome.toFile().parentFile.mkdirs()
                welcome.toFile().writeText(
                    """
                    # 欢迎使用 CodeBind Docs

                    本工作区使用 `${store.docsPath}/*.md` 的 YAML 文件头声明绑定（可用设置 `cbd.docsPath` 修改目录）。

                    用 **CBD: Bind Doc to Current File** 为源文件创建文档；切换源文件即可分栏同步。
                    """.trimIndent() + "\n",
                    Charsets.UTF_8,
                )
            }
        }
        store.writeDocsIndex()
        refreshVfs(store.workspaceRoot)
        svc.drift.scanAll(notify = false)
        svc.splitSync.syncNow(forceFocus = true)
        val extra = if (templatesWritten > 0) {
            " 已写入 $templatesWritten 个默认模板到 `${store.templatesPath}/`。"
        } else {
            " 模板目录：`${store.templatesPath}/`。"
        }
        CbdUi.info(project, "CBD: 已初始化文档目录 `${store.docsPath}/` 与 Agent 脚手架。$extra")
        svc.refreshUi()
    }

    fun bindCurrentFile(sourceRelArg: String? = null) {
        if (!beginModalCmd()) return
        var releaseOnExit = true
        try {
            val store = svc.store() ?: return
            val editorMgr = FileEditorManager.getInstance(project)
            var rel = sourceRelArg?.replace('\\', '/')
            var sourceFile: VirtualFile? = null
            if (rel != null) {
                sourceFile = findVf(store.workspacePath(rel))
            } else {
                sourceFile = editorMgr.selectedFiles.firstOrNull()
                rel = sourceFile?.let { store.toWorkspaceRelative(it.toNioPath()) }
            }
            if (rel == null || sourceFile == null) {
                CbdUi.error(project, "CBD: 请先聚焦一个源文件。")
                return
            }
            if (store.isUnderDocsPath(rel)) {
                CbdUi.error(project, "CBD: 不能绑定文档目录（${store.docsPath}/）内的文件。")
                return
            }
            if (sourceFile.isDirectory) {
                CbdUi.error(project, "CBD: 这是一个文件夹，请使用「CBD: Bind Doc to Folder」。")
                return
            }
            store.ensureLayout()
            svc.scaffoldAgentFiles(store.workspaceRoot)
            store.ensureDefaultTemplates()
            svc.markdownPane.warmIr()

            val kind = CbdUi.choose(project, "选择绑定粒度", "整文件", "代码块（稍后在编辑器中选区）") ?: return
            val bindKind = if (kind.startsWith("代码块")) BindingKind.RANGE else BindingKind.FILE
            if (bindKind == BindingKind.RANGE) {
                releaseOnExit = false
                val sourceRel = rel
                val file = sourceFile
                svc.rangePicker.pick(
                    file,
                    "请在「$sourceRel」中选中要绑定的代码块，然后点击编辑器顶部的「确认选区」。",
                ) { range ->
                    try {
                        if (range == null) return@pick
                        val asked = promptRangeSymbol(store, file, range.first, range.second)
                        if (!asked.first) return@pick
                        finishBindFile(store, file, sourceRel, BindingKind.RANGE, range.first, range.second, asked.second)
                    } finally {
                        endModalCmd()
                    }
                }
                return
            }
            finishBindFile(store, sourceFile, rel, BindingKind.FILE, null, null, null)
        } finally {
            if (releaseOnExit) endModalCmd()
        }
    }

    private fun finishBindFile(
        store: IndexStore,
        sourceFile: VirtualFile,
        rel: String,
        bindKind: BindingKind,
        startLine: Int?,
        endLine: Int?,
        symbol: String?,
    ) {
        store.invalidateCache()
        val index = store.read()
        val forFile = findBindingsForTarget(index, rel)
        if (bindKind == BindingKind.RANGE && startLine != null && endLine != null) {
            val overlaps = findOverlapsWithExisting(forFile, rel, startLine, endLine)
            if (overlaps.isNotEmpty()) {
                val detail = overlaps.take(3).joinToString("、") { "${it.doc} (L${it.target.startLine}-${it.target.endLine})" }
                val go = CbdUi.choose(project, "所选范围与已有代码块绑定重叠：$detail。仍要继续？", "继续绑定", "取消")
                if (go != "继续绑定") return
            }
        }
        if (bindKind == BindingKind.FILE) {
            val existing = forFile.firstOrNull { it.target.kind == BindingKind.FILE }
            if (existing != null) {
                when (CbdUi.choose(project, "该文件已有整文件绑定：${existing.doc}。打开？", "打开", "刷新 contentHash")) {
                    "打开" -> svc.splitSync.revealDocForFile(sourceFile, true)
                    "刷新 contentHash" -> {
                        refreshBindingHash(store, existing)
                        svc.refreshUi()
                    }
                }
                return
            }
        } else {
            val existing = forFile.firstOrNull {
                it.target.kind == BindingKind.RANGE &&
                    it.target.startLine == startLine &&
                    it.target.endLine == endLine
            }
            if (existing != null) {
                if (CbdUi.choose(project, "该选区已有绑定：${existing.doc}。打开？", "打开") == "打开") {
                    svc.splitSync.openDoc(existing.doc, true)
                }
                return
            }
        }

        val baseSuggest = suggestDocPath(store.docsPath, rel).removeSuffix(".md")
        val suggested = if (bindKind == BindingKind.RANGE) {
            "$baseSuggest-${symbol ?: "L$startLine-$endLine"}.md"
        } else {
            "$baseSuggest.md"
        }
        val title = if (bindKind == BindingKind.RANGE) {
            "${rel.substringAfterLast('/')} ${symbol ?: "L$startLine-$endLine"}"
        } else {
            rel.substringAfterLast('/')
        }
        val hash = store.hashFileContent(store.workspacePath(rel))
        val scope = if (bindKind == BindingKind.RANGE) "L$startLine-$endLine" else "整文件"
        pickDocPathAndWrite(
            store,
            suggested,
            title,
            BindingTarget(rel, bindKind, startLine, endLine),
            mutableListOf(BindingAnchor(contentHash = hash, symbol = symbol)),
            scope,
        )
    }

    fun bindCurrentFolder(folder: VirtualFile? = null) {
        val store = svc.store() ?: return
        val picked = folder ?: FileChooser.chooseFile(
            FileChooserDescriptor(false, true, false, false, false, false).withTitle("选择要绑定的文件夹"),
            project,
            findVf(store.workspaceRoot),
        ) ?: return
        val rel = store.toWorkspaceRelative(picked.toNioPath())
        if (rel == null) {
            CbdUi.error(project, "CBD: 文件夹不在项目内。")
            return
        }
        if (!isBindableDirectoryRel(rel, store.docsPath)) {
            CbdUi.error(project, "CBD: 不能绑定该目录（$rel）。")
            return
        }
        store.ensureLayout()
        svc.scaffoldAgentFiles(store.workspaceRoot)
        store.ensureDefaultTemplates()
        store.invalidateCache()
        val existing = findBindingsForTarget(store.read(), rel).firstOrNull { it.target.kind == BindingKind.DIRECTORY }
        if (existing != null) {
            if (CbdUi.choose(project, "该目录已有绑定：${existing.doc}。打开？", "打开") == "打开") {
                svc.splitSync.openDoc(existing.doc, true)
            }
            return
        }
        val suggested = suggestDocPath(store.docsPath, rel, directory = true)
        val title = "${rel.substringAfterLast('/')}/"
        pickDocPathAndWrite(store, suggested, title, BindingTarget(rel, BindingKind.DIRECTORY), mutableListOf(), "整个目录")
    }

    fun revealBoundDoc() {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (file == null) {
            CbdUi.error(project, "CBD: 没有活动编辑器。")
            return
        }
        if (!svc.splitSync.revealDocForFile(file, true)) {
            CbdUi.info(project, "CBD: 无法打开关联文档（需在项目源文件上使用，且文档目录已初始化）。")
        }
    }

    fun toggleSplitSync() {
        val enabled = svc.splitSync.toggle()
        CbdUi.info(
            project,
            if (enabled) {
                "CBD: 已开启自动分栏（打开源文件时显示绑定文档）。关闭：设置或再按 Ctrl+Alt+Shift+D"
            } else {
                "CBD: 已关闭自动分栏。可用 Ctrl+Alt+D 一键打开当前文件的绑定文档"
            },
        )
    }

    fun refreshTree() {
        val store = svc.store() ?: return
        store.invalidateCache()
        store.writeDocsIndex()
        refreshVfs(store.workspaceRoot)
        svc.refreshUi()
        if (!svc.splitSync.refreshCatalogIfOpen()) {
            svc.splitSync.syncNow()
        }
        CbdUi.info(project, "CBD: 已刷新绑定（${store.read().bindings.size} 个）")
    }

    fun openDocsIndex() {
        val store = svc.store() ?: return
        store.ensureLayout()
        store.writeDocsIndex()
        refreshVfs(store.workspaceRoot)
        svc.splitSync.openHome(true)
    }

    fun openDoc(docRel: String?) {
        val rel = docRel ?: svc.markdownPane.currentDocRel
        if (rel.isNullOrBlank()) return
        svc.splitSync.openDoc(rel, true)
    }

    fun revealSourceRange(sourceRel: String? = null, startLine: Int? = null, endLine: Int? = null, kind: BindingKind? = null, docRel: String? = null) {
        val store = svc.storeOrNull() ?: return
        var path = sourceRel
        var start = startLine
        var end = endLine
        var k = kind
        if (path == null) {
            val rel = docRel ?: svc.markdownPane.currentDocRel
            if (rel == null) {
                CbdUi.warn(project, "CBD: 请先打开一篇绑定文档。")
                return
            }
            val binding = findByDocPath(store.read(), rel)
            if (binding == null) {
                CbdUi.warn(project, "CBD: 未找到绑定 $rel")
                return
            }
            path = binding.target.path
            start = binding.target.startLine
            end = binding.target.endLine
            k = binding.target.kind
        }
        val vf = findVf(store.workspacePath(path!!)) ?: run {
            CbdUi.warn(project, "CBD: 无法打开源文件 $path")
            return
        }
        if (k == BindingKind.DIRECTORY) {
            com.intellij.ide.projectView.ProjectView.getInstance(project).select(null, vf, true)
            return
        }
        svc.splitSync.suppressEditorSync()
        val descriptor = if (start != null) {
            OpenFileDescriptor(project, vf, (start - 1).coerceAtLeast(0), 0)
        } else {
            OpenFileDescriptor(project, vf)
        }
        val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true) ?: return
        if (start != null) {
            val doc = editor.document
            val s = (start - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
            val e = ((end ?: start) - 1).coerceIn(s, (doc.lineCount - 1).coerceAtLeast(0))
            editor.selectionModel.setSelection(doc.getLineStartOffset(s), doc.getLineEndOffset(e))
            editor.caretModel.moveToOffset(doc.getLineStartOffset(s))
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
        }
        focusSourceEditor(editor)
    }

    private fun focusSourceEditor(editor: Editor) {
        val run = Runnable {
            if (project.isDisposed) return@Runnable
            IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) app.invokeLater(run) else app.invokeLater(run)
    }

    fun deleteDoc(docRelArg: String? = null) {
        runModalCmd { deleteDocOnce(docRelArg) }
    }

    private fun deleteDocOnce(docRelArg: String?) {
        val store = svc.store() ?: return
        var docRel = docRelArg ?: svc.markdownPane.currentDocRel
        if (docRel == null) {
            val picks = store.read().bindings
            if (picks.isEmpty()) {
                CbdUi.info(project, "CBD: 没有可删除的绑定文档。")
                return
            }
            val picked = CbdUi.popupChoose(project, "选择要删除的文档", picks) { "${it.doc}  →  ${it.target.path}" } ?: return
            docRel = picked.doc
        }
        if (store.isIndexDoc(docRel)) {
            CbdUi.warn(project, "CBD: 不能删除自动生成的 cbd-index.md。")
            return
        }
        if (!CbdUi.confirm(project, "确定删除文档「$docRel」？\n绑定将解除；文件通常进入回收站。", "删除")) return
        val deleted = findByDocPath(store.read(), docRel)
        val targetPath = deleted?.target?.path
        val wasCurrent = svc.markdownPane.currentDocRel == docRel
        val wasHome = svc.markdownPane.isHome
        svc.markdownPane.releaseDoc(docRel)
        try {
            store.deleteDoc(docRel)
        } catch (err: Exception) {
            CbdUi.error(project, "CBD: ${err.message}")
            return
        }
        refreshVfs(store.workspaceRoot)
        svc.refreshUi()
        if ((wasCurrent || wasHome) && targetPath != null) {
            svc.splitSync.syncForSourceRel(targetPath, false)
        } else {
            svc.splitSync.syncNow()
        }
    }

    fun rebindDoc(docRelArg: String? = null) {
        if (!beginModalCmd()) return
        var releaseOnExit = true
        try {
            rebindDocOnce(docRelArg, releaseOnExit = { releaseOnExit = it })
        } finally {
            if (releaseOnExit) endModalCmd()
        }
    }

    private fun rebindDocOnce(docRelArg: String?, releaseOnExit: (Boolean) -> Unit) {
        val store = svc.store() ?: return
        val docRel = docRelArg ?: svc.markdownPane.currentDocRel
        if (docRel == null) {
            CbdUi.warn(project, "CBD: 请指定要重新绑定的文档。")
            return
        }
        if (store.isIndexDoc(docRel)) {
            CbdUi.warn(project, "CBD: 不能重新绑定 cbd-index.md。")
            return
        }
        val binding = findByDocPath(store.read(), docRel)
        if (binding == null) {
            CbdUi.warn(project, "CBD: 未找到绑定文档 $docRel")
            return
        }
        if (binding.target.kind == BindingKind.DIRECTORY) {
            CbdUi.warn(project, "CBD: 目录绑定暂不支持重新绑定，请删除后重新创建。")
            return
        }
        val previousSymbol = binding.anchors.firstOrNull()?.symbol
        val wasRange = binding.target.kind == BindingKind.RANGE
        val picked = FileChooser.chooseFile(
            FileChooserDescriptor(true, false, false, false, false, false).withTitle("为 $docRel 选择源文件"),
            project,
            findVf(store.workspacePath(binding.target.path)),
        ) ?: return
        val newRel = store.toWorkspaceRelative(picked.toNioPath())
        if (newRel == null) {
            CbdUi.error(project, "CBD: 请选择项目内的文件。")
            return
        }
        if (store.isUnderDocsPath(newRel)) {
            CbdUi.error(project, "CBD: 不能绑定到文档目录内的文件。")
            return
        }
        val kind = CbdUi.choose(project, "选择绑定粒度", "整文件", "代码块（稍后在编辑器中选区）") ?: return
        val bindKind = if (kind.startsWith("代码块")) BindingKind.RANGE else BindingKind.FILE
        if (bindKind == BindingKind.RANGE) {
            releaseOnExit(false)
            val pre = if (wasRange && binding.target.startLine != null && binding.target.endLine != null) {
                binding.target.startLine!! to binding.target.endLine!!
            } else null
            svc.rangePicker.pick(picked, "请在「$newRel」中选中要绑定的代码块，然后点击编辑器顶部的「确认选区」。", pre) { range ->
                try {
                    if (range == null) return@pick
                    val asked = promptRangeSymbol(store, picked, range.first, range.second, previousSymbol)
                    if (!asked.first) return@pick
                    applyRebind(store, binding, docRel, newRel, BindingKind.RANGE, range.first, range.second, asked.second)
                } finally {
                    endModalCmd()
                }
            }
            return
        }
        applyRebind(store, binding, docRel, newRel, BindingKind.FILE, null, null, previousSymbol)
    }

    private fun applyRebind(
        store: IndexStore,
        binding: Binding,
        docRel: String,
        newRel: String,
        bindKind: BindingKind,
        startLine: Int?,
        endLine: Int?,
        symbol: String?,
    ) {
        binding.target = BindingTarget(newRel, bindKind, startLine, endLine)
        if (binding.anchors.isEmpty()) {
            binding.anchors += BindingAnchor(symbol = symbol)
        } else {
            binding.anchors[0].symbol = symbol
        }
        refreshBindingHash(store, binding)
        store.writeDocsIndex()
        refreshVfs(store.workspaceRoot)
        svc.refreshUi()
        svc.splitSync.openDoc(binding.doc, true)
        val scope = if (bindKind == BindingKind.RANGE) "L$startLine-$endLine" else "整文件"
        CbdUi.notify(project, "CBD: 已将 $docRel 重新绑定到 $newRel（$scope）")
    }

    fun retightenRange(docRelArg: String? = null) {
        val docRel = docRelArg ?: svc.markdownPane.currentDocRel
        if (docRel == null) {
            CbdUi.warn(project, "CBD: 请指定要重算行号的文档。")
            return
        }
        svc.drift.retightenBindingBySymbol(docRel) ?: return
        svc.refreshUi()
        if (svc.markdownPane.isHome) svc.splitSync.openHome(false)
        else if (svc.markdownPane.currentDocRel == docRel) svc.splitSync.openDoc(docRel, false)
    }

    fun refreshDocHash(docRelArg: String? = null) {
        runModalCmd { refreshDocHashOnce(docRelArg) }
    }

    private fun refreshDocHashOnce(docRelArg: String?) {
        val store = svc.store() ?: return
        val docRel = docRelArg ?: svc.markdownPane.currentDocRel
        if (docRel == null) {
            CbdUi.warn(project, "CBD: 请指定要刷新哈希的文档。")
            return
        }
        val binding = findByDocPath(store.read(), docRel) ?: run {
            CbdUi.warn(project, "CBD: 未找到绑定 $docRel")
            return
        }
        try {
            refreshBindingHash(store, binding)
        } catch (err: Exception) {
            CbdUi.error(project, "CBD: 刷新哈希失败（${err.message}）")
            return
        }
        refreshVfs(store.workspaceRoot)
        svc.refreshUi()
        if (svc.markdownPane.isHome) svc.splitSync.openHome(false)
        CbdUi.notify(project, "CBD: 已更新 $docRel 的 contentHash")
    }

    fun refreshAllDocHashes() {
        val n = svc.drift.refreshAllHashes()
        svc.refreshUi()
        if (n > 0 && svc.markdownPane.isHome) svc.splitSync.openHome(false)
    }

    fun showDrift() {
        svc.drift.showIssuesPicker()
    }

    private fun pickDocPathAndWrite(
        store: IndexStore,
        suggestedDocRel: String,
        title: String,
        target: BindingTarget,
        anchors: MutableList<BindingAnchor>,
        scopeLabel: String,
    ) {
        val docRel = CbdUi.input(project, "新建关联文档路径（项目相对，默认在 ${store.docsPath}/）", suggestedDocRel)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val templates = store.listDocTemplates()
        val template = CbdUi.popupChoose(project, "选择文档模板（可编辑 ${store.templatesPath}/）", templates) {
            "${it.label} — ${it.description}"
        } ?: return
        val binding = Binding(
            id = normalizeRelPath(docRel),
            target = target,
            doc = normalizeRelPath(docRel.replace('\\', '/')),
            anchors = anchors,
        )
        svc.drift.runWithoutSaveHandling {
            store.writeBinding(binding, refreshIndex = false, title = title, body = applyDocTemplate(template.body, title))
        }
        refreshVfs(store.workspaceRoot)
        svc.splitSync.openDoc(binding.doc, true)
        CbdUi.notify(project, "CBD: 已绑定 ${target.path}（$scopeLabel）→ ${binding.doc}")
        store.writeDocsIndex()
        svc.refreshUi()
    }

    private fun promptRangeSymbol(
        store: IndexStore,
        file: VirtualFile,
        startLine: Int,
        endLine: Int,
        previous: String? = null,
    ): Pair<Boolean, String?> {
        val text = try {
            file.toNioPath().readText(Charsets.UTF_8)
        } catch (_: Exception) {
            Files.readString(store.workspacePath(store.toWorkspaceRelative(file.toNioPath()) ?: return false to null))
        }
        val suggested = previous?.trim()?.ifEmpty { null }
            ?: SymbolSuggest.enclosingName(project, file, startLine, endLine)
            ?: suggestSymbolFromLines(text.split(Regex("\\r?\\n")), startLine, endLine)
            ?: ""
        val input = CbdUi.input(
            project,
            "符号名（函数/类名，可改）。已按选区预填；留空则只按行号绑定。当前 L$startLine-$endLine",
            suggested,
        ) ?: return false to null
        return true to input.trim().ifEmpty { null }
    }

    private fun findVf(path: java.nio.file.Path): VirtualFile? =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)

    private fun refreshVfs(root: java.nio.file.Path) {
        VfsUtil.markDirtyAndRefresh(true, true, true, findVf(root))
    }
}
