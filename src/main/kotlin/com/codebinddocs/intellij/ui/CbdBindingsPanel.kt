package com.codebinddocs.intellij.ui

import com.codebinddocs.core.Binding
import com.codebinddocs.core.BindingKind
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.CbdRefreshListener
import com.codebinddocs.intellij.CbdTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class CbdBindingsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CbdBindingsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.setTitleActions(
            listOfNotNull(
                ActionManager.getInstance().getAction("Cbd.OpenDocsIndex"),
                ActionManager.getInstance().getAction("Cbd.RefreshTree"),
            ),
        )
    }
}

class CbdBindingsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val tree = Tree()
    private val model = DefaultTreeModel(DefaultMutableTreeNode("CodeBind Docs"))
    private val connection = project.messageBus.connect(this)

    init {
        tree.model = model
        tree.isRootVisible = false
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
                if (e.clickCount >= 2) handleClick(node.userObject, openDoc = true)
                else if (e.clickCount == 1) handleClick(node.userObject, openDoc = false)
            }
        })
        connection.subscribe(
            CbdTopics.REFRESH,
            object : CbdRefreshListener {
                override fun refreshed() = rebuild()
            },
        )
        rebuild()
    }

    private fun rebuild() {
        val svc = CbdProjectService.getInstance(project)
        val store = svc.storeOrNull()
        val root = DefaultMutableTreeNode("root")
        if (store == null || !store.exists()) {
            root.add(DefaultMutableTreeNode("尚未初始化（运行 CBD: Initialize）"))
            model.setRoot(root)
            return
        }
        val index = store.read()
        val bound = DefaultMutableTreeNode("已绑定（${index.bindings.size}）")
        for (b in index.bindings) {
            bound.add(DefaultMutableTreeNode(BindingNode(b)))
        }
        root.add(bound)
        val coverage = store.scanCoverage(index)
        val unbound = DefaultMutableTreeNode("待绑定（${coverage.unbound.size}）")
        coverage.unbound.take(40).forEach { unbound.add(DefaultMutableTreeNode(UnboundNode(it))) }
        if (coverage.unbound.size > 40) {
            unbound.add(DefaultMutableTreeNode("还有 ${coverage.unbound.size - 40} 个未绑定…"))
        }
        if (coverage.unbound.isEmpty()) {
            unbound.add(DefaultMutableTreeNode("全部已覆盖"))
        }
        root.add(unbound)
        model.setRoot(root)
        expandFirst(tree, root)
    }

    private fun expandFirst(tree: JTree, root: DefaultMutableTreeNode) {
        if (root.childCount == 0) return
        tree.expandPath(TreePath(arrayOf(root, root.getChildAt(0))))
    }

    private fun handleClick(obj: Any?, openDoc: Boolean) {
        val svc = CbdProjectService.getInstance(project)
        when (obj) {
            is BindingNode -> {
                if (openDoc) svc.commands.openDoc(obj.binding.doc)
                else svc.commands.revealSourceRange(
                    obj.binding.target.path,
                    obj.binding.target.startLine,
                    obj.binding.target.endLine,
                    obj.binding.target.kind,
                )
            }
            is UnboundNode -> svc.commands.bindCurrentFile(obj.sourceRel)
            else -> {
                val text = obj?.toString().orEmpty()
                if (text.startsWith("还有")) svc.commands.openDocsIndex()
            }
        }
    }

    override fun dispose() {
        connection.dispose()
    }
}

data class BindingNode(val binding: Binding) {
    override fun toString(): String {
        val b = binding
        return when {
            b.target.kind == BindingKind.RANGE && b.target.startLine != null ->
                "${b.target.path}  ${b.doc}  L${b.target.startLine}-${b.target.endLine}"
            b.target.kind == BindingKind.DIRECTORY -> "${b.target.path}  ${b.doc}（目录）"
            else -> "${b.target.path}  ${b.doc}"
        }
    }
}

data class UnboundNode(val sourceRel: String) {
    override fun toString(): String = sourceRel
}
