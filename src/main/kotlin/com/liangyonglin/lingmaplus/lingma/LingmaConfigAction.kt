package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.Project

/**
 * 配置 Action，用于打开配置对话框
 */
class LingmaConfigAction : AnAction("LingmaHelper 配置") {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(event: AnActionEvent) {
        val project: Project = event.project ?: return
        val dialog = LingmaConfigDialog(project)
        dialog.showAndGet()
    }
}

