package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 配置本地化缓存，参考 ClassInfoCache 实现
 * 配置持久化到 ~/.lingma-plus/lingma-config.json
 */
object LingmaConfigCache {
    private val logger = thisLogger()
    private const val CONFIG_FILE = "lingma-config.json"
    
    private fun getConfigFile(): File {
        val configDir = Paths.get(
            System.getProperty("user.home"),
            ".lingma-plus"
        )
        Files.createDirectories(configDir)
        return configDir.resolve(CONFIG_FILE).toFile()
    }
    
    fun loadConfig(): LingmaConfigData? {
        try {
            val configFile = getConfigFile()
            if (!configFile.exists()) {
                logger.info("配置文件不存在，使用默认值")
                return null
            }
            val json = configFile.readText()
            val minDelay = extractIntValue(json, "minDelaySeconds")
            val maxDelay = extractIntValue(json, "maxDelaySeconds")
            val gitDaysBack = extractIntValue(json, "gitDaysBack")
            val gitAuthor = extractStringValue(json, "gitAuthor") ?: ""
            val pageSize = extractIntValue(json, "pageSize") ?: 20
            if (minDelay != null && maxDelay != null && gitDaysBack != null) {
                logger.info("已从本地加载配置: 间隔${minDelay}-${maxDelay}秒, Git${gitDaysBack}天, 页容量${pageSize}")
                return LingmaConfigData(minDelay, maxDelay, gitDaysBack, gitAuthor, pageSize)
            }
        } catch (e: Exception) {
            logger.error("加载配置失败", e)
        }
        return null
    }
    
    fun saveConfig(data: LingmaConfigData) {
        try {
            val configFile = getConfigFile()
            val json = """
                {
                  "minDelaySeconds": ${data.minDelaySeconds},
                  "maxDelaySeconds": ${data.maxDelaySeconds},
                  "gitDaysBack": ${data.gitDaysBack},
                  "gitAuthor": "${escapeJson(data.gitAuthor)}",
                  "pageSize": ${data.pageSize}
                }
            """.trimIndent()
            configFile.writeText(json)
            logger.info("配置已保存到本地")
        } catch (e: Exception) {
            logger.error("保存配置失败", e)
        }
    }
    
    data class LingmaConfigData(
        val minDelaySeconds: Int,
        val maxDelaySeconds: Int,
        val gitDaysBack: Int,
        val gitAuthor: String,
        val pageSize: Int = 20
    )
    
    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    private fun extractStringValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\[\\\\\"nrt])*)\""
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        val value = match.groupValues[1]
        return value.replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
    
    private fun extractIntValue(json: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}
