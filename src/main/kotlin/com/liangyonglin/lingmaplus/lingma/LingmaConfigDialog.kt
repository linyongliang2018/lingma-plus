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
    
    init {
        title = "LingmaHelper 配置"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        panel.preferredSize = Dimension(400, 120)
        
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        // 说明文字
        val label1 = JLabel("设置每次提问之间的时间间隔（秒）")
        label1.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        contentPanel.add(label1)
        
        // 最小值输入
        val minPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        minPanel.add(JLabel("最小值: "))
        minDelayField.preferredSize = Dimension(100, 25)
        minDelayField.toolTipText = "时间间隔的最小值（秒）"
        minPanel.add(minDelayField)
        minPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(minPanel)
        
        // 最大值输入
        val maxPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        maxPanel.add(JLabel("最大值: "))
        maxDelayField.preferredSize = Dimension(100, 25)
        maxDelayField.toolTipText = "时间间隔的最大值（秒）"
        maxPanel.add(maxDelayField)
        maxPanel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(maxPanel)
        
        // 提示信息
        val hintLabel = JLabel("<html><small>当前配置: ${settings.getMinDelaySeconds()}-${settings.getMaxDelaySeconds()} 秒</small></html>")
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
            
            settings.setDelayRange(min, max)
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

