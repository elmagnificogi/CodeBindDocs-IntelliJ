package com.codebinddocs.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class CbdStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        CbdProjectService.getInstance(project).start()
    }
}
