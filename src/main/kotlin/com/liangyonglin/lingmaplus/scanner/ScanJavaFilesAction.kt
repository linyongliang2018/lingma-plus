package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to scan Java files and display classes with more than 20 methods
 */
class ScanJavaFilesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project: Project = event.project ?: return
        
        // 显示ToolWindow
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(JavaClassScannerToolWindow.TOOL_WINDOW_ID)
        
        toolWindow?.show {
            // 获取内容面板并开始扫描
            val contentManager = toolWindow.contentManager
            val content = contentManager.findContent("JavaClassScanner") 
                ?: contentManager.contents.firstOrNull()
            
            if (content != null) {
                val component = content.component
                if (component is JavaClassScannerPanel) {
                    component.startScan()
                }
            }
        }
    }
}

