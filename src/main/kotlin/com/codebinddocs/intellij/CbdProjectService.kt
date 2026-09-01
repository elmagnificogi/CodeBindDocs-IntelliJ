package com.codebinddocs.intellij

import com.codebinddocs.core.AGENTS_CONTENT
import com.codebinddocs.core.CURSOR_RULE_CONTENT
import com.codebinddocs.core.IndexStore
import com.codebinddocs.core.JUNIE_GUIDELINES_CONTENT
import com.codebinddocs.intellij.drift.DriftChecker
import com.codebinddocs.intellij.editor.CbdInlayController
import com.codebinddocs.intellij.editor.RangePicker
import com.codebinddocs.intellij.store.PathMigration
import com.codebinddocs.intellij.sync.SplitSync
import com.codebinddocs.intellij.ui.CbdMarkdownPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

@Service(Service.Level.PROJECT)
class CbdProjectService(val project: Project) : Disposable {
    val settings: CbdSettings = CbdSettings.getInstance(project)
    val rangePicker = RangePicker(project)
    val markdownPane = CbdMarkdownPane(this)
    val drift = DriftChecker(this)
    val splitSync = SplitSync(this)
    val inlays = CbdInlayController(this)
    val commands = CbdCommands(this)
    private var connection: MessageBusConnection? = null

    fun workspaceRoot(): Path? = project.basePath?.let { Path.of(it) }

    fun storeOrNull(): IndexStore? {
        val root = workspaceRoot() ?: return null
        return IndexStore(root, settings.toConfig())
    }

    fun store(): IndexStore? {
        val store = storeOrNull()
        if (store == null) {
            CbdUi.error(project, "CBD: 请先打开一个项目文件夹。")
        }
        return store
    }

    fun refreshUi() {
        splitSync.refreshCatalogIfOpen()
        inlays.refreshAll()
        drift.scanAll(notify = false)
        project.messageBus.syncPublisher(CbdTopics.REFRESH).refreshed()
    }

    fun start() {
        connection?.disconnect()
        connection = project.messageBus.connect(this)
        connection!!.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, splitSync)
        connection!!.subscribe(VirtualFileManager.VFS_CHANGES, drift)
        splitSync.attachEditors()
        val store = storeOrNull()
        if (store != null && store.exists()) {
            store.writeDocsIndex()
            drift.scanAll(notify = false)
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            PathMigration.checkOnStartup(this)
            inlays.attach()
            if (store != null && store.exists()) splitSync.syncNow()
        }
    }

    fun scaffoldAgentFiles(root: Path) {
        writeIfMissing(root.resolve("AGENTS.md"), AGENTS_CONTENT)
        val cursorRules = root.resolve(".cursor").resolve("rules")
        Files.createDirectories(cursorRules)
        writeIfMissing(cursorRules.resolve("cbd.mdc"), CURSOR_RULE_CONTENT)
        val junie = root.resolve(".junie")
        Files.createDirectories(junie)
        writeIfMissing(junie.resolve("guidelines.md"), JUNIE_GUIDELINES_CONTENT)
    }

    override fun dispose() {
        connection?.disconnect()
        rangePicker.dispose()
        markdownPane.dispose()
        splitSync.dispose()
        drift.dispose()
        inlays.dispose()
    }

    companion object {
        fun getInstance(project: Project): CbdProjectService = project.service()

        private fun writeIfMissing(path: Path, content: String) {
            if (!path.exists()) {
                Files.createDirectories(path.parent)
                path.writeText(content, Charsets.UTF_8)
            }
        }
    }
}

interface CbdRefreshListener {
    fun refreshed()
}

object CbdTopics {
    val REFRESH = com.intellij.util.messages.Topic.create("cbd.refresh", CbdRefreshListener::class.java)
}
