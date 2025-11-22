package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * ToolWindow factory for Java Class Scanner
 */
class JavaClassScannerToolWindow : ToolWindowFactory {
    companion object {
        const val TOOL_WINDOW_ID = "JavaClassScanner"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JavaClassScannerPanel(project)
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

