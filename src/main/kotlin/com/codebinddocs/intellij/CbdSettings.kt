package com.codebinddocs.intellij

import com.codebinddocs.core.CbdConfig
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CodeBindDocsSettings", storages = [Storage("codebinddocs.xml")])
class CbdSettings : PersistentStateComponent<CbdSettings.State> {
    data class State(
        var docsPath: String = "docs/cbd",
        var assetsPath: String = "",
        var templatesPath: String = "",
        var splitSyncEnabled: Boolean = true,
        var promptWhenUnbound: Boolean = true,
        var viewColumn: String = "Beside",
        var docPaneMode: String = "ir",
        var docPaneOutline: Boolean = true,
        var lastDocsPath: String? = null,
        var lastAssetsPath: String? = null,
        var lastTemplatesPath: String? = null,
    )

    var stored: State = State()
        private set

    override fun getState(): State = stored

    override fun loadState(state: State) {
        stored = state
    }

    fun toConfig(): CbdConfig = CbdConfig(
        docsPath = stored.docsPath,
        assetsPath = stored.assetsPath,
        templatesPath = stored.templatesPath,
    )

    companion object {
        fun getInstance(project: Project): CbdSettings = project.service()
    }
}
