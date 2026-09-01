package com.codebinddocs.intellij

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class CbdConfigurable(private val project: Project) : BoundSearchableConfigurable("CodeBind Docs", "codebinddocs") {
    override fun createPanel(): DialogPanel {
        val state = CbdSettings.getInstance(project).stored
        return panel {
            group("路径") {
                row("文档目录 docsPath:") { textField().bindText(state::docsPath) }
                row("资源目录 assetsPath:") { textField().bindText(state::assetsPath).comment("留空则 {docsPath}/assets") }
                row("模板目录 templatesPath:") { textField().bindText(state::templatesPath).comment("留空则 {docsPath}/_templates") }
            }
            group("分栏同步") {
                row { checkBox("打开源文件时自动显示绑定文档").bindSelected(state::splitSyncEnabled) }
                row { checkBox("无绑定时显示新建入口").bindSelected(state::promptWhenUnbound) }
                row("文档面板位置 (Beside/Two):") { textField().bindText(state::viewColumn) }
            }
            group("文档面板") {
                row("编辑模式 (ir/source):") { textField().bindText(state::docPaneMode) }
                row { checkBox("即时渲染右侧显示大纲").bindSelected(state::docPaneOutline) }
            }
        }
    }
}
