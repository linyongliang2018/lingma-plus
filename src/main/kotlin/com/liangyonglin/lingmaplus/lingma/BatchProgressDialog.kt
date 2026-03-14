package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * 批量请求进度显示对话框。
 * 显示：当前批次请求类、当前请求类、当前请求方法、剩余类、剩余方法、当前进度、剩余进度。
 */
class BatchProgressDialog(
    project: Project?,
    private val actionDesc: String,
    private val totalClasses: Int,
    private val totalTasks: Int
) : DialogWrapper(project) {

    private val currentBatchClassLabel = JLabel("—")
    private val currentRequestClassLabel = JLabel("—")
    private val currentMethodLabel = JLabel("—")
    private val remainingClassesLabel = JLabel("—")
    private val remainingMethodsLabel = JLabel("—")
    private val currentProgressLabel = JLabel("—")
    private val remainingProgressLabel = JLabel("—")
    private val progressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = "0%"
    }

    init {
        title = "Lingma 批量$actionDesc - 进度"
        setModal(false)
        init()
        remainingClassesLabel.text = "$totalClasses"
        remainingMethodsLabel.text = "$totalTasks"
        currentProgressLabel.text = "0 / $totalTasks"
        remainingProgressLabel.text = "$totalTasks / $totalTasks"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        panel.preferredSize = Dimension(520, 280)

        val grid = JPanel()
        grid.layout = BoxLayout(grid, BoxLayout.Y_AXIS)

        fun addRow(label: String, valueComp: JComponent) {
            val row = JPanel(FlowLayout(FlowLayout.LEFT))
            val l = JLabel("$label: ")
            l.preferredSize = Dimension(100, 22)
            row.add(l)
            valueComp.preferredSize = Dimension(380, 22)
            row.add(valueComp)
            grid.add(row)
        }

        addRow("当前批次请求类", currentBatchClassLabel)
        addRow("当前请求类", currentRequestClassLabel)
        addRow("当前请求方法", currentMethodLabel)
        addRow("剩余类", remainingClassesLabel)
        addRow("剩余方法", remainingMethodsLabel)
        addRow("当前进度", currentProgressLabel)
        addRow("剩余进度", remainingProgressLabel)

        grid.add(Box.createVerticalStrut(10))
        val barPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        barPanel.add(JLabel("进度条: "))
        progressBar.preferredSize = Dimension(400, 22)
        barPanel.add(progressBar)
        grid.add(barPanel)

        val scroll = JBScrollPane(grid)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    override fun createActions() = arrayOf<Action>(cancelAction)

    /**
     * 更新进度显示。必须在 EDT 中调用。
     */
    fun updateProgress(
        currentBatchClass: String,
        currentRequestClass: String,
        currentMethod: String,
        remainingClasses: Int,
        remainingMethods: Int,
        completedTasks: Int
    ) {
        currentBatchClassLabel.text = currentBatchClass
        currentRequestClassLabel.text = currentRequestClass
        currentMethodLabel.text = currentMethod
        remainingClassesLabel.text = "$remainingClasses"
        remainingMethodsLabel.text = "$remainingMethods"
        currentProgressLabel.text = "$completedTasks / $totalTasks"
        remainingProgressLabel.text = "$remainingMethods / $totalTasks"
        val percent = if (totalTasks > 0) (completedTasks * 100) / totalTasks else 0
        progressBar.value = percent
        progressBar.string = "$percent%"
    }

    /**
     * 标记为已完成。
     */
    fun setCompleted() {
        currentBatchClassLabel.text = "—"
        currentRequestClassLabel.text = "—"
        currentMethodLabel.text = "全部完成"
        remainingClassesLabel.text = "0"
        remainingMethodsLabel.text = "0"
        currentProgressLabel.text = "$totalTasks / $totalTasks"
        remainingProgressLabel.text = "0 / $totalTasks"
        progressBar.value = 100
        progressBar.string = "100%"
    }
}
