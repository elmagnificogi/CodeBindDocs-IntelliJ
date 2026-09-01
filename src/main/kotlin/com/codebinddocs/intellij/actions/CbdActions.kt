package com.codebinddocs.intellij.actions

import com.codebinddocs.intellij.CbdProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction

abstract class CbdAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    protected fun svc(e: AnActionEvent) = e.project?.let { CbdProjectService.getInstance(it) }
}

class CbdInitializeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.initialize() }
}

class CbdBindCurrentFileAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.bindCurrentFile() }
}

class CbdBindCurrentFolderAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isDirectory }
        svc(e)?.commands?.bindCurrentFolder(folder)
    }
}

class CbdRevealBoundDocAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.revealBoundDoc() }
}

class CbdToggleSplitSyncAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.toggleSplitSync() }
}

class CbdRefreshTreeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.refreshTree() }
}

class CbdOpenDocsIndexAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.openDocsIndex() }
}

class CbdRevealSourceRangeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.revealSourceRange() }
}

class CbdDeleteDocAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.deleteDoc() }
}

class CbdRebindDocAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.rebindDoc() }
}

class CbdRetightenRangeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.retightenRange() }
}

class CbdShowDriftAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.showDrift() }
}

class CbdRefreshDocHashAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.refreshDocHash() }
}

class CbdRefreshAllHashesAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.commands?.refreshAllDocHashes() }
}

class CbdAcceptRangeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.rangePicker?.accept() }
}

class CbdCancelRangeAction : CbdAction() {
    override fun actionPerformed(e: AnActionEvent) { svc(e)?.rangePicker?.cancel() }
}
