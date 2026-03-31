package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.*
import java.awt.*
import java.awt.FlowLayout

/**
 * 配置对话框，用于设置时间间隔的最小值和最大值
 */
class LingmaConfigDialog(project: Project?) : DialogWrapper(project) {
    
    private val settings = LingmaSettings.getInstance()
    
    private val minDelayField = JTextField(settings.getMinDelaySeconds().toString(), 10)
    private val maxDelayField = JTextField(settings.getMaxDelaySeconds().toString(), 10)
    private val gitDaysField = JTextField(settings.getGitDaysBack().toString(), 10)
    private val gitAuthorField = JTextField(settings.getGitAuthor(), 20)
    private val pageSizeField = JTextField(settings.getPageSize().toString(), 10)
    private val optimizeSuffixField = JTextField(settings.getOptimizeCommandSuffix(), 28)
    private val commentSuffixField = JTextField(settings.getCommentCommandSuffix(), 28)
    private val customAskPromptField = JTextField(settings.getCustomAskPrompt(), 28)
    
    init {
        title = "LingmaHelper 配置"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        panel.preferredSize = Dimension(620, 420)
        
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        // 时间间隔
        val label1 = JLabel("设置每次提问之间的时间间隔（秒）")
        label1.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        contentPanel.add(label1)
        
        val minPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        minPanel.add(JLabel("最小值: "))
        minDelayField.preferredSize = Dimension(100, 25)
        minDelayField.toolTipText = "时间间隔的最小值（秒）"
        minPanel.add(minDelayField)
        minPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(minPanel)
        
        val maxPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        maxPanel.add(JLabel("最大值: "))
        maxDelayField.preferredSize = Dimension(100, 25)
        maxDelayField.toolTipText = "时间间隔的最大值（秒）"
        maxPanel.add(maxDelayField)
        maxPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(maxPanel)
        
        // Git 扫描配置
        val gitLabel = JLabel("Git 扫描配置（用于「扫描最近git修改」）")
        gitLabel.border = BorderFactory.createEmptyBorder(15, 0, 10, 0)
        contentPanel.add(gitLabel)
        
        val gitDaysPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        gitDaysPanel.add(JLabel("回溯天数: "))
        gitDaysField.preferredSize = Dimension(80, 25)
        gitDaysField.toolTipText = "扫描最近N天内修改的Java文件（1-1000天）"
        gitDaysPanel.add(gitDaysField)
        gitDaysPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(gitDaysPanel)
        
        val gitAuthorPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        gitAuthorPanel.add(JLabel("作者过滤: "))
        gitAuthorField.preferredSize = Dimension(200, 25)
        gitAuthorField.toolTipText = "Git 作者名，留空表示当前用户"
        gitAuthorPanel.add(gitAuthorField)
        gitAuthorPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(gitAuthorPanel)
        
        // 分页配置
        val pageLabel = JLabel("JavaClassScanner 分页")
        pageLabel.border = BorderFactory.createEmptyBorder(15, 0, 10, 0)
        contentPanel.add(pageLabel)
        val pageSizePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        pageSizePanel.add(JLabel("页容量: "))
        pageSizeField.preferredSize = Dimension(80, 25)
        pageSizeField.toolTipText = "每页显示的类数量（1-500），例如 20"
        pageSizePanel.add(pageSizeField)
        pageSizePanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(pageSizePanel)
        
        // 自定义命令后缀配置
        val commandLabel = JLabel("灵码命令后缀（会追加在 /optimize 或 /comment 后）")
        commandLabel.border = BorderFactory.createEmptyBorder(15, 0, 10, 0)
        contentPanel.add(commandLabel)
        
        val optimizeSuffixPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        optimizeSuffixPanel.add(JLabel("优化后缀: "))
        optimizeSuffixField.preferredSize = Dimension(420, 25)
        optimizeSuffixField.toolTipText = "示例：保留代码原始结构，仅优化变量名"
        optimizeSuffixPanel.add(optimizeSuffixField)
        optimizeSuffixPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(optimizeSuffixPanel)
        
        val commentSuffixPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        commentSuffixPanel.add(JLabel("注释后缀: "))
        commentSuffixField.preferredSize = Dimension(420, 25)
        commentSuffixField.toolTipText = "示例：注释使用中文，保留关键英文术语"
        commentSuffixPanel.add(commentSuffixField)
        commentSuffixPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(commentSuffixPanel)
        
        // 自定义提示词提问
        val customPromptLabel = JLabel("自定义提示词提问（用于批量发起同一条普通问答）")
        customPromptLabel.border = BorderFactory.createEmptyBorder(15, 0, 10, 0)
        contentPanel.add(customPromptLabel)
        
        val customAskPromptPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        customAskPromptPanel.add(JLabel("提问提示词: "))
        customAskPromptField.preferredSize = Dimension(420, 25)
        customAskPromptField.toolTipText = "示例：请解释这段代码的核心逻辑、边界条件和潜在风险"
        customAskPromptPanel.add(customAskPromptField)
        customAskPromptPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(customAskPromptPanel)
        
        // 提示信息
        val hintLabel = JLabel(
            "<html><small>时间: ${settings.getMinDelaySeconds()}-${settings.getMaxDelaySeconds()}秒 | Git: ${settings.getGitDaysBack()}天 ${if (settings.getGitAuthor().isNotEmpty()) "作者:${settings.getGitAuthor()}" else "当前用户"} | 页容量: ${settings.getPageSize()} | 自定义后缀已启用</small></html>"
        )
        hintLabel.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
        contentPanel.add(hintLabel)
        
        panel.add(contentPanel, BorderLayout.CENTER)
        return panel
    }
    
    override fun doOKAction() {
        try {
            val min = minDelayField.text.toInt()
            val max = maxDelayField.text.toInt()
            
            if (min < 0 || max < 0) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    contentPanel,
                    "时间间隔不能为负数",
                    "配置错误"
                )
                return
            }
            
            if (min > max) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    contentPanel,
                    "最小值不能大于最大值",
                    "配置错误"
                )
                return
            }
            
            val gitDays = gitDaysField.text.toIntOrNull() ?: 5
            val gitAuthor = gitAuthorField.text.trim()
            
            if (gitDays < 1 || gitDays > 1000) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    contentPanel,
                    "Git回溯天数应在1-1000之间",
                    "配置错误"
                )
                return
            }
            
            val pageSize = pageSizeField.text.toIntOrNull() ?: 20
            if (pageSize < 1 || pageSize > 500) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    contentPanel,
                    "页容量应在1-500之间",
                    "配置错误"
                )
                return
            }
            
            settings.setDelayRange(min, max)
            settings.setGitConfig(gitDays, gitAuthor)
            settings.setPageSize(pageSize)
            settings.setCommandSuffixes(
                optimizeSuffix = optimizeSuffixField.text,
                commentSuffix = commentSuffixField.text
            )
            settings.setCustomAskPrompt(customAskPromptField.text)
            super.doOKAction()
        } catch (e: NumberFormatException) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                contentPanel,
                "请输入有效的数字",
                "配置错误"
            )
        }
    }
}

