package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * 打开零码聊天框后，向 ChatInputTextArea 注入“纯文本”并模拟点击发送按钮。
 * 用于“自定义提示词提问”批量自动提问。
 */
object CosyChatUiAutoSender {
    private const val TOOL_WINDOW_ID = "Code Search"

    private val logger = thisLogger()

    private const val CHAT_INPUT_TEXT_AREA_CLASS =
        "com.alibabacloud.intellij.cosy.ui.search.generate.input.ChatInputTextArea"
    private const val CHAT_INPUT_OPERATION_PANEL_CLASS =
        "com.alibabacloud.intellij.cosy.ui.search.generate.input.ChatInputOperationPanel"

    fun tryAutoSend(project: Project, prompt: String): Boolean {
        val text = prompt.trim()
        if (text.isEmpty()) return false

        return try {
            val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return false
            val contentComponent = toolWindow.contentManager.contents.firstOrNull()?.component ?: return false

            val textArea = findFirstComponent(contentComponent, CHAT_INPUT_TEXT_AREA_CLASS) ?: return false
            val cozyLoader = textArea.javaClass.classLoader

            val chatInputElementClass = Class.forName(
                "com.alibabacloud.intellij.cosy.ui.search.model.ChatInputElement",
                true,
                cozyLoader
            )
            // ChatInputElement(String text, ChatAskTag tag, String type)
            val textElement = chatInputElementClass
                .getConstructor(String::class.java, Class.forName(
                    "com.alibabacloud.intellij.cosy.ui.search.model.tag.ChatAskTag",
                    true,
                    cozyLoader
                ), String::class.java)
                .newInstance(text, null, "text")

            val list = java.util.Collections.singletonList(textElement)

            val recoverElements = textArea.javaClass.getMethod("recoverElements", List::class.java)
            recoverElements.invoke(textArea, list)

            // 如果 send 不可用，canSend() 会返回 false
            val canSend = runCatching {
                textArea.javaClass.getMethod("canSend").invoke(textArea) as? Boolean ?: false
            }.getOrDefault(false)
            if (!canSend) {
                return false
            }

            val operationPanel = findFirstComponent(contentComponent, CHAT_INPUT_OPERATION_PANEL_CLASS) ?: return false
            val sendLabel = operationPanel.javaClass.getMethod("getSendLabel").invoke(operationPanel) ?: return false
            if (sendLabel !is Component) return false

            // Swing click 触发 ChatInputOperationPanel 的 send listener
            sendLabel.dispatchEvent(
                MouseEvent(
                    sendLabel,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    5,
                    5,
                    1,
                    false,
                    MouseEvent.BUTTON1
                )
            )
            true
        } catch (e: Throwable) {
            logger.warn("自动注入并发送失败（prompt长度=${text.length}）", e)
            false
        }
    }

    private fun findFirstComponent(root: Component, className: String): Component? {
        if (root.javaClass.name == className) return root
        if (root is Container) {
            val children = root.components
            for (child in children) {
                val found = findFirstComponent(child, className)
                if (found != null) return found
            }
        }
        return null
    }
}

