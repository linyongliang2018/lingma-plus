package com.liangyonglin.lingmaplus.lingma

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiJavaFile
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Action class for batch explaining methods using Lingma (通义灵码).
 * This action automatically selects each method in the current class and triggers Lingma explanation.
 */
class PopupDialogAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project: Project = event.project ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) as? PsiJavaFile ?: return

        // 1) 收集所有方法（包括构造方法，后面随机跳过）
        val allMethods = psiFile.classes.flatMap { cls -> cls.methods.toList() }
        
        // 构造方法也随机跳过（更随机，检测不出来）
        val shuffledMethods = allMethods.toMutableList()
        Collections.shuffle(shuffledMethods)
        
        // 随机决定保留比例（60%-80%），包括构造方法也可能被跳过
        val keepRatio = 0.6 + Math.random() * 0.2
        val keepCount = (shuffledMethods.size * keepRatio).toInt().coerceAtLeast(1)
        val selectedMethods = shuffledMethods.take(keepCount)
        
        // 计算非构造方法的数量（用于统计显示）
        val nonConstructorMethods = allMethods.filter { method ->
            psiFile.classes.none { cls -> method.name == cls.name }
        }
        
        if (allMethods.isEmpty()) {
            Messages.showInfoMessage(
                project, 
                "当前类中没有找到任何方法", 
                "LingmaHelper"
            )
            return
        }

        if (selectedMethods.size < 10) {
            val choice = Messages.showYesNoDialog(
                project,
                "筛选后方法数量为 ${selectedMethods.size} 个，不足 10 个。\n" +
                "是否仍然强制发起提问？（原始方法: ${allMethods.size} 个）",
                "LingmaHelper",
                "强制提问",
                "取消",
                null
            )
            if (choice != Messages.YES) {
                return
            }
        }

        // 3) 获取配置的时间间隔
        val settings = LingmaSettings.getInstance()
        val minDelay = settings.getMinDelaySeconds()
        val maxDelay = settings.getMaxDelaySeconds()
        val avgDelay = (minDelay + maxDelay) / 2

        // 4) 弹窗提示
        Messages.showInfoMessage(
            project,
            "将从 ${allMethods.size} 个方法中随机选择 ${selectedMethods.size} 个进行提问\n" +
            "时间间隔: ${minDelay}-${maxDelay} 秒\n" +
            "预计耗时: ${selectedMethods.size * avgDelay / 60} 分钟",
            "LingmaHelper"
        )

        // 5) 获取 Lingma Action
        val cosyAction = ActionManager.getInstance()
            .getAction("TriggerCosyExplainCodeGenerationAction") ?: run {
            Messages.showErrorDialog(
                project,
                "找不到 Lingma Explain Action (TriggerCosyExplainCodeGenerationAction)",
                "LingmaHelper"
            )
            return
        }

        // 6) 单线程调度
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val iterator = selectedMethods.iterator()

        fun scheduleNext() {
            if (!iterator.hasNext()) {
                scheduler.shutdown()
                return
            }
            val method = iterator.next()
            ApplicationManager.getApplication().invokeLater {
                // 获取当前激活的文本编辑器
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                if (editor == null) {
                    // 如果没有激活编辑器则停止
                    scheduler.shutdown()
                    return@invokeLater
                }

                // 计算安全的选区范围
                val document = editor.document
                val start = method.textRange.startOffset.coerceIn(0, document.textLength)
                val end = method.textRange.endOffset.coerceIn(0, document.textLength)

                // 清除旧选区并设定新选区
                editor.selectionModel.removeSelection()
                editor.caretModel.moveToOffset(start)
                editor.selectionModel.setSelection(start, end)

                // 构造事件并触发 Lingma 解释
                val dataContext = DataManager.getInstance().getDataContext(editor.component)
                val event = AnActionEvent(
                    null,
                    dataContext,
                    ActionPlaces.EDITOR_POPUP,
                    cosyAction.templatePresentation.clone(),
                    ActionManager.getInstance(),
                    0
                )
                cosyAction.actionPerformed(event)
            }
            // 使用配置的随机延时
            val delay = (minDelay..maxDelay).random().toLong()
            scheduler.schedule({ scheduleNext() }, delay, TimeUnit.SECONDS)
        }

        // 启动
        scheduleNext()
    }
}

