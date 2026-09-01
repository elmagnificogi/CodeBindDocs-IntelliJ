package com.codebinddocs.intellij.editor

import com.codebinddocs.core.BindingKind
import com.codebinddocs.core.findBindingsForTarget
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.CbdRefreshListener
import com.codebinddocs.intellij.CbdTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class CbdInlayController(private val svc: CbdProjectService) : Disposable {
    private val inlays = mutableMapOf<Editor, MutableList<Inlay<*>>>()
    private val listeners = mutableMapOf<Editor, MouseAdapter>()
    private val connection = svc.project.messageBus.connect(this)

    fun attach() {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) = refresh(event.editor)
            override fun editorReleased(event: EditorFactoryEvent) = clear(event.editor)
        }, this)
        connection.subscribe(CbdTopics.REFRESH, object : CbdRefreshListener {
            override fun refreshed() = refreshAll()
        })
        refreshAll()
    }

    fun refreshAll() {
        EditorFactory.getInstance().allEditors.filter { it.project == svc.project }.forEach { refresh(it) }
    }

    private fun refresh(editor: Editor) {
        if (editor.isDisposed || editor.project != svc.project) return
        clear(editor)
        val vf = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (!vf.isInLocalFileSystem) return
        val store = svc.storeOrNull() ?: return
        val rel = store.toWorkspaceRelative(vf.toNioPath()) ?: return
        if (store.isUnderDocsPath(rel) || !store.exists()) return
        val bindings = findBindingsForTarget(store.read(), rel)
        if (bindings.isEmpty()) return
        val list = mutableListOf<Inlay<*>>()
        val extra = if (bindings.size > 1) "（${bindings.size} 篇）" else ""
        add(editor, 0, "CBD: 打开文档$extra") { svc.splitSync.revealDocForFile(vf, true) }?.let { list += it }
        for (b in bindings.filter { it.target.kind == BindingKind.RANGE && it.target.startLine != null }) {
            val line = (b.target.startLine!! - 1).coerceAtLeast(0)
            if (line >= editor.document.lineCount) continue
            val offset = editor.document.getLineStartOffset(line)
            val symbol = b.anchors.firstOrNull()?.symbol
            val label = if (symbol != null) {
                "CBD: $symbol (${b.target.startLine}-${b.target.endLine})"
            } else {
                "CBD: 代码块文档 L${b.target.startLine}-${b.target.endLine}"
            }
            add(editor, offset, label) { svc.commands.openDoc(b.doc) }?.let { list += it }
        }
        inlays[editor] = list
        ensureMouse(editor)
    }

    private fun add(editor: Editor, offset: Int, text: String, onClick: () -> Unit): Inlay<*>? {
        return editor.inlayModel.addBlockElement(offset, true, true, 1, LabelRenderer(text, onClick))
    }

    private fun ensureMouse(editor: Editor) {
        if (editor in listeners) return
        val adapter = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                for (inl in inlays[editor].orEmpty()) {
                    val bounds = inl.bounds ?: continue
                    if (bounds.contains(e.point)) {
                        (inl.renderer as? LabelRenderer)?.onClick?.invoke()
                        break
                    }
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val hit = inlays[editor].orEmpty().any { it.bounds?.contains(e.point) == true }
                editor.contentComponent.cursor =
                    if (hit) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
            }
        }
        editor.contentComponent.addMouseListener(adapter)
        editor.contentComponent.addMouseMotionListener(adapter)
        listeners[editor] = adapter
    }

    private fun clear(editor: Editor) {
        inlays.remove(editor)?.forEach { it.dispose() }
        listeners.remove(editor)?.let { adapter ->
            editor.contentComponent.removeMouseListener(adapter)
            editor.contentComponent.removeMouseMotionListener(adapter)
        }
    }

    override fun dispose() {
        inlays.keys.toList().forEach { clear(it) }
        connection.dispose()
    }

    private class LabelRenderer(
        private val text: String,
        val onClick: () -> Unit,
    ) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val editor = inlay.editor
            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            return editor.contentComponent.getFontMetrics(font).stringWidth(text) + 16
        }

        override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            g.drawString(text, targetRegion.x + 8, targetRegion.y + inlay.editor.ascent)
        }
    }
}
