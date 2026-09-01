package com.codebinddocs.intellij.sync

import com.codebinddocs.core.findDirectoryBindingForRel
import com.codebinddocs.core.findByTargetPath
import com.codebinddocs.core.isBindableSourceRel
import com.codebinddocs.core.normalizeRelPath
import com.codebinddocs.core.resolveBindingForLine
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.drift.DriftSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SplitSync(private val svc: CbdProjectService) : FileEditorManagerListener, Disposable {
    private var syncing = false
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var caretFuture: ScheduledFuture<*>? = null
    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            val editor = FileEditorManager.getInstance(svc.project).selectedTextEditor ?: return
            if (event.editor != editor) return
            caretFuture?.cancel(false)
            caretFuture = scheduler.schedule({
                onEdt { syncForEditor(false) }
            }, 120, TimeUnit.MILLISECONDS)
        }
    }

    fun attachEditors() {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener, this)
        scheduler.schedule({
            onEdt { syncNow() }
        }, 400, TimeUnit.MILLISECONDS)
    }

    fun isEnabled(): Boolean = svc.settings.stored.splitSyncEnabled

    fun setEnabled(value: Boolean) {
        svc.settings.stored.splitSyncEnabled = value
    }

    fun toggle(): Boolean {
        val next = !isEnabled()
        setEnabled(next)
        return next
    }

    fun syncNow(forceFocus: Boolean = false) {
        onEdt { syncForEditor(forceFocus) }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        if (isEnabled()) syncNow(false)
        else updateStatus()
    }

    fun revealDocForFile(file: VirtualFile, forceFocus: Boolean): Boolean {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            onEdt { revealDocForFile(file, forceFocus) }
            return true
        }
        val store = svc.storeOrNull() ?: return false
        val rel = store.toWorkspaceRelative(file.toNioPath()) ?: return false
        if (store.isUnderDocsPath(rel)) return false
        val editor = FileEditorManager.getInstance(svc.project).selectedTextEditor
        val line = (editor?.caretModel?.logicalPosition?.line ?: 0) + 1
        val index = store.read()
        val binding = resolveBindingForLine(index, rel, line) ?: findByTargetPath(index, rel)
        if (binding == null) {
            showUnbound(rel, forceFocus, shouldOfferBind(rel, store.docsPath), dirDoc(store, rel, index))
            return true
        }
        openDoc(binding.doc, forceFocus)
        return true
    }

    fun openDoc(docRel: String, forceFocus: Boolean) {
        onEdt {
            ensureToolWindow(forceFocus)
            svc.markdownPane.showDoc(docRel, forceFocus)
            updateStatus()
        }
    }

    fun openHome(forceFocus: Boolean) {
        onEdt {
            ensureToolWindow(forceFocus)
            svc.markdownPane.showHome(forceFocus)
            updateStatus()
        }
    }

    fun syncForSourceRel(sourceRel: String, forceFocus: Boolean) {
        onEdt { syncForSourceRelOnEdt(sourceRel, forceFocus) }
    }

    private fun syncForSourceRelOnEdt(sourceRel: String, forceFocus: Boolean) {
        val store = svc.storeOrNull() ?: return
        val rel = normalizeRelPath(sourceRel)
        if (rel.isEmpty() || store.isUnderDocsPath(rel) || !store.exists()) return
        val index = store.read()
        val forFile = index.bindings.filter { normalizeRelPath(it.target.path) == rel }
        if (forFile.isEmpty()) {
            showUnbound(rel, forceFocus, shouldOfferBind(rel, store.docsPath), dirDoc(store, rel, index))
            return
        }
        val editor = FileEditorManager.getInstance(svc.project).selectedTextEditor
        val line = (editor?.caretModel?.logicalPosition?.line ?: 0) + 1
        val binding = resolveBindingForLine(index, rel, line) ?: forFile.first()
        openDoc(binding.doc, forceFocus)
    }

    fun refreshCatalogIfOpen(): Boolean = svc.markdownPane.refreshCatalogIfOpen()

    fun updateStatus() {
        svc.project.messageBus.syncPublisher(com.codebinddocs.intellij.CbdTopics.REFRESH).refreshed()
    }

    fun statusText(): Pair<String, Boolean> {
        val warnings = svc.drift.issues.count { it.severity == DriftSeverity.WARNING }
        val infos = svc.drift.issues.count { it.severity == DriftSeverity.INFO }
        return when {
            warnings == 0 && infos == 0 -> "CBD" to false
            warnings > 0 -> "CBD 绑定 $warnings" to true
            else -> "CBD 核对 $infos" to false
        }
    }

    private fun syncForEditor(forceFocus: Boolean) {
        if (syncing) return
        if (svc.rangePicker.isPicking()) return
        val store = svc.storeOrNull() ?: return
        if (!store.exists()) return
        val file = FileEditorManager.getInstance(svc.project).selectedFiles.firstOrNull() ?: return
        val rel = store.toWorkspaceRelative(file.toNioPath()) ?: return
        if (store.isUnderDocsPath(rel)) return
        if (!isEnabled() && !forceFocus) {
            updateStatus()
            return
        }
        syncing = true
        try {
            revealDocForFile(file, forceFocus)
        } finally {
            syncing = false
        }
    }

    private fun showUnbound(
        rel: String,
        forceFocus: Boolean,
        canCreate: Boolean,
        dirDoc: Map<String, String>?,
    ) {
        if (!isEnabled() && !forceFocus) return
        ensureToolWindow(forceFocus)
        svc.markdownPane.showUnbound(rel, canCreate, dirDoc, forceFocus)
        updateStatus()
    }

    private fun ensureToolWindow(activate: Boolean) {
        val tw = ToolWindowManager.getInstance(svc.project).getToolWindow("CodeBind Docs") ?: return
        if (activate || isEnabled()) tw.show()
    }

    private fun onEdt(action: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            action()
        } else {
            app.invokeLater {
                if (!svc.project.isDisposed) action()
            }
        }
    }

    private fun shouldOfferBind(rel: String, docsPath: String): Boolean =
        svc.settings.stored.promptWhenUnbound && isBindableSourceRel(rel, docsPath)

    private fun dirDoc(store: com.codebinddocs.core.IndexStore, rel: String, index: com.codebinddocs.core.CbdIndex): Map<String, String>? {
        val b = findDirectoryBindingForRel(index, rel) ?: return null
        return mapOf("doc" to b.doc, "dirPath" to b.target.path)
    }

    override fun dispose() {
        caretFuture?.cancel(false)
        scheduler.shutdownNow()
    }
}
