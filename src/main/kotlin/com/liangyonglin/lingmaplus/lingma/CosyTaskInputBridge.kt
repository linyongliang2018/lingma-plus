package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * 通过反射向灵码的 taskInputs 写入任务后缀文本，实现 /comment 与 /optimize 追加自定义命令。
 */
object CosyTaskInputBridge {
    private val logger = thisLogger()

    private const val ACTION_OPTIMIZE = "TriggerCosyOptimizeCodeGenerationAction"
    private const val ACTION_COMMENT = "TriggerCosyCodeGenerateCommentGenerationAction"
    private const val TASK_OPTIMIZE = "OPTIMIZE_CODE"
    private const val TASK_COMMENT = "CODE_GENERATE_COMMENT"
    private const val KEY_SEPARATOR = ":"

    fun applyCustomSuffixByAction(
        project: Project,
        actionId: String,
        optimizeSuffix: String,
        commentSuffix: String
    ) {
        val taskName = when (actionId) {
            ACTION_OPTIMIZE -> TASK_OPTIMIZE
            ACTION_COMMENT -> TASK_COMMENT
            else -> return
        }
        val suffix = when (taskName) {
            TASK_OPTIMIZE -> optimizeSuffix.trim()
            TASK_COMMENT -> commentSuffix.trim()
            else -> ""
        }
        if (suffix.isEmpty()) return
        applyTaskInputSuffix(project, taskName, suffix)
    }

    private fun applyTaskInputSuffix(project: Project, taskName: String, suffix: String) {
        try {
            val loader = resolveCosyClassLoader() ?: return
            val settingClass = Class.forName(
                "com.alibabacloud.intellij.cosy.ui.config.CosyPersistentSetting",
                true,
                loader
            )
            val getInstance = settingClass.getMethod("getInstance")
            val setting = getInstance.invoke(null) ?: return

            val getState = settingClass.getMethod("getState")
            val state = getState.invoke(setting) ?: return
            val stateClass = state.javaClass

            val getTaskInputs = stateClass.getMethod("getTaskInputs")
            @Suppress("UNCHECKED_CAST")
            val taskInputs = getTaskInputs.invoke(state) as? MutableMap<String, String> ?: mutableMapOf()

            val putTaskInputs = stateClass.getMethod("putTaskInputs", String::class.java, String::class.java)

            // 兼容已登录和未登录两种 key 形态：<userId>:<taskName>。
            val candidateKeys = mutableSetOf("$KEY_SEPARATOR$taskName")
            resolveLoginUserId(project, loader)?.let { userId ->
                candidateKeys.add("$userId$KEY_SEPARATOR$taskName")
            }
            for (key in taskInputs.keys) {
                if (key.endsWith("$KEY_SEPARATOR$taskName")) {
                    candidateKeys.add(key)
                }
            }
            for (key in candidateKeys) {
                putTaskInputs.invoke(state, key, suffix)
            }
        } catch (e: Throwable) {
            logger.warn("写入灵码 taskInputs 失败: task=$taskName", e)
        }
    }

    private fun resolveCosyClassLoader(): ClassLoader? {
        val action = ActionManager.getInstance().getAction(ACTION_OPTIMIZE)
            ?: ActionManager.getInstance().getAction(ACTION_COMMENT)
            ?: return null
        return action.javaClass.classLoader
    }

    private fun resolveLoginUserId(project: Project, loader: ClassLoader): String? {
        return try {
            val loginUtilClass = Class.forName(
                "com.alibabacloud.intellij.cosy.util.LoginUtil",
                true,
                loader
            )
            val getAuthStatus = loginUtilClass.getMethod(
                "getAuthStatusCacheFirst",
                Project::class.java
            )
            val authStatus = getAuthStatus.invoke(null, project) ?: return null
            val getId = authStatus.javaClass.getMethod("getId")
            (getId.invoke(authStatus) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }
}
