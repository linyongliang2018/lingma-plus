package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.diagnostic.thisLogger

/**
 * 构建灵码的 ChatAskInput，但不携带任何 tag/element，
 * 避免 UI 把文本渲染成“指令/标签”（橙色高亮）。
 */
object CosyChatAskInputFactory {
    private val logger = thisLogger()

    fun buildTextOnlyAskInput(cosyClassLoader: ClassLoader, prompt: String): Any? {
        val text = prompt.trim()
        if (text.isEmpty()) return null
        return try {
            val chatAskInputClass = Class.forName(
                "com.alibabacloud.intellij.cosy.chat.model.ChatAskInput",
                true,
                cosyClassLoader
            )
            val empty = java.util.Collections.emptyList<Any>()

            val askInput = chatAskInputClass.getConstructor().newInstance()
            // 注意：只保留文本，tag/element/contextTags 都置空。
            chatAskInputClass.getMethod("setTags", List::class.java).invoke(askInput, empty)
            chatAskInputClass
                .getMethod("setChatInputElements", List::class.java)
                .invoke(askInput, empty)

            // 如果存在 setContextTags，则也置空；不同版本可能方法签名略不同
            try {
                chatAskInputClass.getMethod("setContextTags", List::class.java).invoke(askInput, empty)
            } catch (_: Throwable) {
            }

            chatAskInputClass.getMethod("setText", String::class.java).invoke(askInput, text)
            askInput
        } catch (e: Throwable) {
            logger.warn("构建 ChatAskInput 失败", e)
            null
        }
    }
}

