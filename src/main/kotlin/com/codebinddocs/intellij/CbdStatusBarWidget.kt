package com.codebinddocs.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class CbdStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ID
    override fun getDisplayName(): String = "CodeBind Docs"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CbdStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget as Disposable)
    }
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        const val ID = "CodeBindDocs"
    }
}

class CbdStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation, Disposable {
    private var statusBar: StatusBar? = null
    private val connection = project.messageBus.connect(this)

    init {
        connection.subscribe(
            CbdTopics.REFRESH,
            object : CbdRefreshListener {
                override fun refreshed() {
                    statusBar?.updateWidget(ID())
                }
            },
        )
    }

    override fun ID(): String = CbdStatusBarWidgetFactory.ID
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getText(): String {
        val svc = CbdProjectService.getInstance(project)
        if (svc.rangePicker.isPicking()) return "确认代码块选区"
        return svc.splitSync.statusText().first
    }
    override fun getAlignment(): Float = 0.5f
    override fun getTooltipText(): String {
        val svc = CbdProjectService.getInstance(project)
        return if (svc.rangePicker.isPicking()) "选好代码后点击确认绑定范围"
        else "CodeBind Docs — 点击打开当前绑定文档（Ctrl+Alt+D）"
    }
    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val svc = CbdProjectService.getInstance(project)
        if (svc.rangePicker.isPicking()) svc.rangePicker.accept()
        else svc.commands.revealBoundDoc()
    }
    override fun dispose() {
        connection.dispose()
        statusBar = null
    }
}
