package com.codebinddocs.intellij.editor

import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.CbdUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.JComponent

class RangePicker(private val project: Project) : Disposable {
    private var pending: Pending? = null
    private var banner: Pair<FileEditor, JComponent>? = null

    data class Pending(
        val file: VirtualFile,
        val onDone: (Pair<Int, Int>?) -> Unit,
    )

    fun isPicking(): Boolean = pending != null

    fun pick(
        file: VirtualFile,
        @Suppress("UNUSED_PARAMETER") message: String,
        preselect: Pair<Int, Int>? = null,
        onDone: (Pair<Int, Int>?) -> Unit,
    ) {
        cancel()
        val descriptor = OpenFileDescriptor(project, file)
        val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        if (editor == null) {
            onDone(null)
            return
        }
        if (preselect != null) {
            val doc = editor.document
            val start = (preselect.first - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
            val end = (preselect.second - 1).coerceIn(start, (doc.lineCount - 1).coerceAtLeast(0))
            editor.selectionModel.setSelection(doc.getLineStartOffset(start), doc.getLineEndOffset(end))
            editor.caretModel.moveToOffset(doc.getLineStartOffset(start))
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
        pending = Pending(file, onDone)
        attachBanner(file)
        tryAddStatusWidgets()
        refreshStatus()
        focusEditor()
    }

    fun accept() {
        val p = pending ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (editor == null || file != p.file) {
            CbdUi.warn(project, "CBD: 请先聚焦目标源文件，并选中要绑定的代码后再确认。")
            return
        }
        val sel = editor.selectionModel
        val doc = editor.document
        val startOff = if (sel.hasSelection()) sel.selectionStart else editor.caretModel.offset
        val endOff = if (sel.hasSelection()) sel.selectionEnd else editor.caretModel.offset
        var startLine = doc.getLineNumber(startOff) + 1
        var endLine = doc.getLineNumber(endOff.coerceAtMost(doc.textLength.coerceAtLeast(0))) + 1
        if (sel.hasSelection() && sel.selectionEnd > 0 && doc.getLineStartOffset(doc.getLineNumber(sel.selectionEnd.coerceAtMost(doc.textLength))) == sel.selectionEnd &&
            doc.getLineNumber(sel.selectionEnd) > doc.getLineNumber(sel.selectionStart)
        ) {
            endLine = doc.getLineNumber(sel.selectionEnd)
        }
        if (endLine < startLine) endLine = startLine
        finish(startLine to endLine)
    }

    fun cancel() {
        finish(null)
    }

    private fun finish(result: Pair<Int, Int>?) {
        val p = pending
        pending = null
        clearUi()
        refreshStatus()
        p?.onDone(result)
    }

    override fun dispose() = cancel()

    private fun attachBanner(file: VirtualFile) {
        val mgr = FileEditorManager.getInstance(project)
        val fileEditor = mgr.getEditors(file).firstOrNull { it is TextEditor } ?: mgr.getSelectedEditor(file) ?: return
        val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
        panel.text = "请在编辑器中选中要绑定的代码，然后点击「确认选区」"
        panel.createActionLabel("确认选区") { accept() }
        panel.createActionLabel("取消") { cancel() }
        mgr.addTopComponent(fileEditor, panel)
        banner = fileEditor to panel
    }

    private fun tryAddStatusWidgets() {
        try {
            val bar = WindowManager.getInstance().getStatusBar(project) ?: return
            bar.addWidget(ConfirmWidget(), "before Position", this)
            bar.addWidget(CancelWidget(), "before Position", this)
        } catch (_: Exception) {
        }
    }

    private fun clearUi() {
        try {
            banner?.let { (fe, comp) ->
                FileEditorManager.getInstance(project).removeTopComponent(fe, comp)
            }
        } catch (_: Exception) {
        }
        banner = null
        try {
            WindowManager.getInstance().getStatusBar(project)?.let {
                it.removeWidget(CONFIRM_ID)
                it.removeWidget(CANCEL_ID)
            }
        } catch (_: Exception) {
        }
    }

    private fun focusEditor() {
        val app = ApplicationManager.getApplication()
        val run = Runnable {
            if (project.isDisposed || pending == null) return@Runnable
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@Runnable
            IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
        }
        if (app.isDispatchThread) app.invokeLater(run) else app.invokeLater(run)
    }

    private fun refreshStatus() {
        try {
            CbdProjectService.getInstance(project).splitSync.updateStatus()
        } catch (_: Exception) {
        }
    }

    inner class ConfirmWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
        override fun ID(): String = CONFIRM_ID
        override fun install(statusBar: StatusBar) {}
        override fun dispose() {}
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String = "确认代码块选区"
        override fun getAlignment(): Float = 0f
        override fun getTooltipText(): String = "在编辑器中选好代码后点击确认"
        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { accept() }
    }

    inner class CancelWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
        override fun ID(): String = CANCEL_ID
        override fun install(statusBar: StatusBar) {}
        override fun dispose() {}
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String = "取消选区"
        override fun getAlignment(): Float = 0f
        override fun getTooltipText(): String = "取消代码块绑定"
        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { cancel() }
    }

    companion object {
        const val CONFIRM_ID = "CbdRangeConfirm"
        const val CANCEL_ID = "CbdRangeCancel"
    }
}
