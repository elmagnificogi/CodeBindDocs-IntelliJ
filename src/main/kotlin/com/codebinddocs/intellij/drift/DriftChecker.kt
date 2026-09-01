package com.codebinddocs.intellij.drift

import com.codebinddocs.core.Binding
import com.codebinddocs.core.BindingKind
import com.codebinddocs.core.IndexStore
import com.codebinddocs.core.findOverlappingRangePairs
import com.codebinddocs.core.findSymbolLine
import com.codebinddocs.core.normalizeRelPath
import com.codebinddocs.core.resolveSymbolLineRangeFromText
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.CbdUi
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

enum class DriftSeverity { INFO, WARNING }

enum class DriftKind {
    MISSING_TARGET, MISSING_DOC, HASH, RANGE, SYMBOL, RENAMED, OVERLAP
}

data class DriftIssue(
    val bindingId: String,
    val message: String,
    val severity: DriftSeverity,
    val kind: DriftKind,
    val targetPath: String,
    val doc: String,
)

class DriftChecker(private val svc: CbdProjectService) : BulkFileListener, Disposable {
    var issues: List<DriftIssue> = emptyList()
        private set
    private val notifiedKeys = mutableSetOf<String>()
    private var suspendSaveHandling = 0

    fun runWithoutSaveHandling(block: () -> Unit) {
        suspendSaveHandling++
        try {
            block()
        } finally {
            suspendSaveHandling--
        }
    }

    fun scanAll(notify: Boolean = false, focusTarget: String? = null): List<DriftIssue> {
        val store = svc.storeOrNull()
        if (store == null || !store.exists()) {
            issues = emptyList()
            notifiedKeys.clear()
            svc.splitSync.updateStatus()
            return emptyList()
        }
        store.invalidateCache()
        val index = store.read()
        val found = mutableListOf<DriftIssue>()
        for (binding in index.bindings) {
            val targetPath = store.workspacePath(binding.target.path)
            if (!targetPath.exists()) {
                found += DriftIssue(binding.id, "绑定源文件缺失: ${binding.target.path}", DriftSeverity.WARNING, DriftKind.MISSING_TARGET, binding.target.path, binding.doc)
                continue
            }
            if (!store.workspacePath(binding.doc).exists()) {
                found += DriftIssue(binding.id, "绑定文档缺失: ${binding.doc}", DriftSeverity.WARNING, DriftKind.MISSING_DOC, binding.target.path, binding.doc)
                continue
            }
            if (binding.target.kind == BindingKind.DIRECTORY) continue
            found += checkAnchors(store, binding, targetPath)
        }
        for (pair in findOverlappingRangePairs(index.bindings)) {
            val aRange = "L${pair.a.target.startLine}-${pair.a.target.endLine}"
            val bRange = "L${pair.b.target.startLine}-${pair.b.target.endLine}"
            found += DriftIssue(
                pair.a.id,
                "代码块范围与「${pair.b.doc}」重叠（$aRange ∩ $bRange）",
                DriftSeverity.WARNING,
                DriftKind.OVERLAP,
                pair.path,
                pair.a.doc,
            )
        }
        val previous = notifiedKeys.toSet()
        issues = found
        val currentKeys = found.map { issueKey(it) }.toSet()
        notifiedKeys.removeAll { it !in currentKeys }
        svc.splitSync.updateStatus()
        if (notify) notifyNewIssues(found, previous, focusTarget)
        return found
    }

    fun showIssuesPicker() {
        scanAll(notify = false)
        if (issues.isEmpty()) {
            CbdUi.info(svc.project, "CBD: 当前没有绑定漂移。")
            return
        }
        data class Row(val label: String, val issue: DriftIssue?, val bulkHash: Boolean)
        val rows = mutableListOf<Row>()
        val hashCount = issues.count { it.kind == DriftKind.HASH }
        if (hashCount > 0) {
            rows += Row("全部标记已核对（$hashCount 项）", null, true)
        }
        for (issue in issues) {
            rows += Row("${driftKindLabel(issue.kind)}  ${issue.targetPath}  ${issue.message}", issue, false)
        }
        val picked = CbdUi.popupChoose(svc.project, "CodeBind Docs 绑定变更提示", rows) { it.label } ?: return
        if (picked.bulkHash) {
            refreshAllHashes()
            return
        }
        picked.issue?.let { promptIssueActions(it, true) }
    }

    fun refreshAllHashes(): Int {
        val store = svc.storeOrNull() ?: return 0
        scanAll(notify = false)
        val hashIssues = issues.filter { it.kind == DriftKind.HASH }
        if (hashIssues.isEmpty()) {
            CbdUi.info(svc.project, "CBD: 当前没有待核对的源码变更提醒。")
            return 0
        }
        val index = store.read()
        var n = 0
        for (issue in hashIssues) {
            val binding = index.bindings.firstOrNull { it.doc == issue.doc } ?: continue
            try {
                refreshBindingHash(store, binding)
                notifiedKeys.remove(issueKey(issue))
                n++
            } catch (_: Exception) {
            }
        }
        store.writeDocsIndex()
        scanAll(notify = false)
        CbdUi.info(svc.project, "CBD: 已标记 $n 个文档为已核对")
        return n
    }

    fun retightenBindingBySymbol(docRel: String): Pair<Int, Int>? {
        val store = svc.storeOrNull() ?: return null
        val binding = store.read().bindings.firstOrNull { normalizeRelPath(it.doc) == normalizeRelPath(docRel) }
        if (binding == null) {
            CbdUi.warn(svc.project, "CBD: 未找到绑定 $docRel")
            return null
        }
        val symbol = binding.anchors.firstOrNull()?.symbol?.trim()
        if (symbol.isNullOrEmpty()) {
            CbdUi.warn(svc.project, "CBD: 该绑定没有 symbol，无法按符号重算行号。")
            return null
        }
        if (binding.target.kind != BindingKind.RANGE) {
            CbdUi.warn(svc.project, "CBD: 仅代码块（range）绑定支持按 symbol 重算。")
            return null
        }
        val prevStart = binding.target.startLine
        val prevEnd = binding.target.endLine
        val previousSpan = if (prevStart != null && prevEnd != null) (prevEnd - prevStart).coerceAtLeast(0) else null
        val text = try {
            store.workspacePath(binding.target.path).readText(Charsets.UTF_8)
        } catch (err: Exception) {
            CbdUi.error(svc.project, "CBD: 重算行号失败（${err.message}）")
            return null
        }
        val span = resolveSymbolLineRangeFromText(text, symbol, previousSpan)
        if (span == null) {
            CbdUi.warn(svc.project, "CBD: 未找到符号「$symbol」，请改绑或手改行号。")
            return null
        }
        val oldLabel = if (prevStart != null && prevEnd != null) "L$prevStart-$prevEnd" else "原范围"
        if (span.startLine == prevStart && span.endLine == prevEnd) {
            CbdUi.info(svc.project, "CBD: $docRel 行号已是最新（$oldLabel · $symbol）")
            refreshBindingHash(store, binding)
            scanAll(notify = false)
            return span.startLine to span.endLine
        }
        binding.target.startLine = span.startLine
        binding.target.endLine = span.endLine
        runWithoutSaveHandling { refreshBindingHash(store, binding) }
        store.writeDocsIndex()
        notifiedKeys.clear()
        scanAll(notify = false)
        CbdUi.info(svc.project, "CBD: 已按「$symbol」重算 $docRel：$oldLabel → L${span.startLine}-${span.endLine}")
        return span.startLine to span.endLine
    }

    override fun after(events: List<VFileEvent>) {
        val store = svc.storeOrNull() ?: return
        if (!store.exists()) return
        var hits = 0
        val updated = mutableListOf<String>()
        for (event in events) {
            if (event is VFileMoveEvent || (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME)) {
                val oldPath = when (event) {
                    is VFileMoveEvent -> event.oldPath
                    is VFilePropertyChangeEvent -> {
                        val parent = event.file.parent?.path ?: continue
                        "$parent/${event.oldValue}"
                    }
                    else -> continue
                }
                val newPath = event.file?.path ?: continue
                val oldRel = store.toWorkspaceRelative(Path.of(oldPath)) ?: continue
                val newRel = store.toWorkspaceRelative(Path.of(newPath)) ?: continue
                if (store.isUnderDocsPath(oldRel) || store.isUnderDocsPath(newRel)) continue
                val n = store.updateTargetPath(oldRel, newRel)
                if (n > 0) {
                    hits += n
                    updated += "$oldRel → $newRel（$n 条绑定）"
                }
            }
        }
        if (updated.isNotEmpty()) {
            notify("CBD: 已根据改名更新绑定路径（共 $hits 条）\n${updated.take(3).joinToString("\n")}")
            store.writeDocsIndex()
        }
        val saved = events.mapNotNull { it.file }.filter { !it.isDirectory }
        for (file in saved) {
            onFileSaved(file)
        }
        if (updated.isNotEmpty()) scanAll(notify = true)
    }

    fun onFileSaved(file: VirtualFile) {
        if (suspendSaveHandling > 0) return
        val store = svc.storeOrNull() ?: return
        val rel = store.toWorkspaceRelative(file.toNioPath()) ?: return
        if (store.isUnderDocsPath(rel) && rel.endsWith(".md")) {
            if (store.isIndexDoc(rel)) return
            store.invalidateCache()
            scanAll(notify = false)
            return
        }
        val index = store.read()
        if (index.bindings.any { normalizeRelPath(it.target.path) == normalizeRelPath(rel) }) {
            scanAll(notify = true, focusTarget = rel)
        }
    }

    private fun checkAnchors(store: IndexStore, binding: Binding, targetPath: Path): List<DriftIssue> {
        val out = mutableListOf<DriftIssue>()
        val text = try {
            targetPath.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return out
        }
        val lineCount = text.split(Regex("\\r?\\n")).size
        val currentHash = store.hashFileContent(targetPath)
        val anchor = binding.anchors.firstOrNull()
        val symbol = anchor?.symbol
        if (!anchor?.contentHash.isNullOrEmpty() && anchor!!.contentHash != currentHash) {
            out += DriftIssue(
                binding.id,
                "源码已修改，请确认旁路文档是否仍准确（${symbol ?: "整文件"}）· 仅提醒，可忽略",
                DriftSeverity.INFO,
                DriftKind.HASH,
                binding.target.path,
                binding.doc,
            )
        }
        if (binding.target.kind == BindingKind.RANGE && binding.target.startLine != null && binding.target.endLine != null) {
            val startLine = binding.target.startLine!!
            val endLine = binding.target.endLine!!
            if (startLine < 1 || endLine > lineCount || startLine > endLine) {
                out += DriftIssue(binding.id, "行范围 L$startLine-$endLine 越界（文件共 $lineCount 行）", DriftSeverity.WARNING, DriftKind.RANGE, binding.target.path, binding.doc)
            } else if (!symbol.isNullOrEmpty()) {
                val foundLine = findSymbolLine(text, symbol)
                if (foundLine == null) {
                    out += DriftIssue(binding.id, "未找到符号「$symbol」，代码块绑定可能已失效", DriftSeverity.WARNING, DriftKind.SYMBOL, binding.target.path, binding.doc)
                } else if (foundLine < startLine || foundLine > endLine) {
                    out += DriftIssue(binding.id, "符号「$symbol」现位于 L$foundLine，不在绑定范围 L$startLine-$endLine", DriftSeverity.WARNING, DriftKind.SYMBOL, binding.target.path, binding.doc)
                }
            }
        } else if (!symbol.isNullOrEmpty() && binding.target.kind == BindingKind.FILE) {
            val foundLine = findSymbolLine(text, symbol)
            if (foundLine == null && !anchor?.contentHash.isNullOrEmpty() && anchor!!.contentHash != currentHash) {
                out += DriftIssue(binding.id, "未找到符号「$symbol」（文件内容亦已变化）", DriftSeverity.INFO, DriftKind.SYMBOL, binding.target.path, binding.doc)
            }
        }
        return out
    }

    private fun notifyNewIssues(found: List<DriftIssue>, previousNotified: Set<String>, focusTarget: String?) {
        val actionable = found.filter {
            it.kind == DriftKind.HASH || it.kind == DriftKind.RANGE || it.kind == DriftKind.SYMBOL ||
                it.kind == DriftKind.OVERLAP || it.kind == DriftKind.MISSING_TARGET || it.kind == DriftKind.MISSING_DOC
        }
        val candidates = if (focusTarget != null) {
            actionable.filter { normalizeRelPath(it.targetPath) == normalizeRelPath(focusTarget) }
                .sortedBy { issueNotifyPriority(it) }
        } else {
            actionable.filter { it.severity == DriftSeverity.WARNING }
        }
        for (issue in candidates) {
            val key = issueKey(issue)
            if (key in notifiedKeys && key in previousNotified) continue
            notifiedKeys += key
            promptIssueActions(issue, false)
            break
        }
    }

    private fun promptIssueActions(issue: DriftIssue, force: Boolean) {
        if (issue.kind == DriftKind.HASH) {
            val actions = if (force) arrayOf("打开文档核对", "标记已核对", "知道了") else arrayOf("知道了", "打开文档核对")
            when (CbdUi.choose(svc.project, "CodeBind Docs 提醒（可忽略）\n${issue.message}\n文档：${issue.doc}", *actions)) {
                "全部标记已核对" -> refreshAllHashes()
                "标记已核对" -> {
                    val store = svc.storeOrNull() ?: return
                    val binding = store.read().bindings.firstOrNull { it.doc == issue.doc } ?: return
                    refreshBindingHash(store, binding)
                    notifiedKeys.remove(issueKey(issue))
                    scanAll(notify = false)
                    CbdUi.info(svc.project, "CBD: 已清除 ${issue.doc} 的源码变更提醒")
                }
                "打开文档核对" -> svc.commands.openDoc(issue.doc)
            }
            return
        }
        val actions = mutableListOf<String>()
        when (issue.kind) {
            DriftKind.MISSING_DOC -> actions += listOf("重新绑定", "打开源文件")
            DriftKind.MISSING_TARGET -> actions += listOf("重新绑定", "删除文档")
            DriftKind.SYMBOL, DriftKind.RANGE -> {
                if (!issue.message.contains("未找到符号")) actions += "按 symbol 重算行号"
                actions += listOf("打开文档", "重新绑定")
            }
            DriftKind.OVERLAP -> actions += listOf("重新绑定", "打开文档")
            else -> actions += "打开文档"
        }
        actions += "忽略"
        val title = when (issue.kind) {
            DriftKind.RANGE, DriftKind.SYMBOL -> "CodeBind Docs：建议按 symbol 重算行号"
            DriftKind.OVERLAP -> "CodeBind Docs 绑定关系可能已变更"
            else -> "CodeBind Docs 绑定异常"
        }
        when (CbdUi.choose(svc.project, "$title\n${issue.message}\n文档：${issue.doc}", *actions.toTypedArray())) {
            "按 symbol 重算行号" -> retightenBindingBySymbol(issue.doc)
            "重新绑定" -> if (issue.kind == DriftKind.MISSING_DOC) svc.commands.bindCurrentFile(issue.targetPath) else svc.commands.rebindDoc(issue.doc)
            "打开文档" -> svc.commands.openDoc(issue.doc)
            "打开源文件" -> svc.commands.revealSourceRange(issue.targetPath)
            "删除文档" -> svc.commands.deleteDoc(issue.doc)
        }
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodeBind Docs")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(svc.project)
    }

    override fun dispose() {}
}

fun refreshBindingHash(store: IndexStore, binding: Binding) {
    if (binding.target.kind == BindingKind.DIRECTORY) return
    val hash = store.hashFileContent(store.workspacePath(binding.target.path))
    if (binding.anchors.isEmpty()) {
        binding.anchors += com.codebinddocs.core.BindingAnchor(contentHash = hash)
    } else {
        for (a in binding.anchors) a.contentHash = hash
    }
    store.writeBinding(binding)
}

fun issueKey(issue: DriftIssue): String = "${issue.kind}|${issue.doc}|${issue.targetPath}|${issue.message}"

fun issueNotifyPriority(issue: DriftIssue): Int = when (issue.kind) {
    DriftKind.SYMBOL -> 0
    DriftKind.RANGE -> 1
    DriftKind.OVERLAP -> 2
    DriftKind.MISSING_DOC, DriftKind.MISSING_TARGET -> 3
    DriftKind.HASH -> 9
    else -> 5
}

fun driftKindLabel(kind: DriftKind): String = when (kind) {
    DriftKind.MISSING_TARGET -> "源文件缺失"
    DriftKind.MISSING_DOC -> "文档缺失"
    DriftKind.HASH -> "文档核对提醒"
    DriftKind.OVERLAP -> "范围重叠"
    DriftKind.RANGE -> "行范围失效"
    DriftKind.SYMBOL -> "符号变动"
    DriftKind.RENAMED -> "路径已改"
}
