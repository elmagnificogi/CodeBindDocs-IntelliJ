package com.codebinddocs.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.ListSelectionModel

object CbdUi {
    fun info(project: Project?, message: String) {
        Messages.showInfoMessage(project, message, "CodeBind Docs")
    }

    fun notify(project: Project?, message: String) {
        if (project == null) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodeBind Docs")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    fun warn(project: Project?, message: String) {
        Messages.showWarningDialog(project, message, "CodeBind Docs")
    }

    fun error(project: Project?, message: String) {
        Messages.showErrorDialog(project, message, "CodeBind Docs")
    }

    fun confirm(project: Project?, message: String, okText: String = "确定"): Boolean {
        return Messages.showYesNoDialog(project, message, "CodeBind Docs", okText, "取消", null) == Messages.YES
    }

    fun choose(project: Project?, message: String, vararg options: String): String? {
        val idx = Messages.showDialog(project, message, "CodeBind Docs", options, 0, null)
        return if (idx < 0) null else options[idx]
    }

    fun input(project: Project?, prompt: String, initial: String = ""): String? {
        return Messages.showInputDialog(project, prompt, "CodeBind Docs", null, initial, null)
    }

    fun <T> popupChoose(project: Project?, title: String, items: List<T>, label: (T) -> String): T? {
        if (items.isEmpty()) return null
        val dialog = object : DialogWrapper(project, true) {
            private val list = JBList(items)
            init {
                this.title = title
                list.selectionMode = ListSelectionModel.SINGLE_SELECTION
                list.cellRenderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: javax.swing.JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ): java.awt.Component {
                        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        @Suppress("UNCHECKED_CAST")
                        text = label(value as T)
                        return c
                    }
                }
                list.selectedIndex = 0
                list.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        if (e.clickCount >= 2) doOKAction()
                    }
                })
                init()
            }

            override fun createCenterPanel(): JComponent {
                val scroll = JBScrollPane(list)
                scroll.preferredSize = Dimension(520, 280)
                return scroll
            }

            fun selected(): T? = list.selectedValue
        }
        return if (dialog.showAndGet()) dialog.selected() else null
    }
}
