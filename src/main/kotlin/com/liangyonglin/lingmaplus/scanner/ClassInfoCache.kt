package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 文件来源类型
 */
enum class FileSource {
    PROJECT,      // 项目文件
    LIBRARY,      // 第三方库
    JDK          // JDK
}

/**
 * 缓存类信息的数据类（用于序列化）
 */
data class CachedClassInfo(
    val className: String,
    val filePath: String,
    val methodCount: Int,
    var navigateCount: Int = 0,
    val fileSource: String = FileSource.PROJECT.name  // 默认项目文件
)

/**
 * 缓存管理器
 */
object ClassInfoCache {
    private val logger = thisLogger()
    private const val CACHE_FILE_PREFIX = "java_class_scanner_cache_"
    private const val CACHE_FILE_SUFFIX = ".json"
    
    /**
     * 获取项目标识（基于项目路径的hash）
     */
    private fun getProjectId(projectBasePath: String?): String {
        if (projectBasePath == null || projectBasePath.isEmpty()) {
            return "default"
        }
        // 使用项目路径的hash作为标识
        return projectBasePath.hashCode().toString().replace("-", "n")
    }
    
    /**
     * 获取缓存文件路径
     */
    private fun getCacheFile(projectBasePath: String?): File {
        // 使用项目配置目录
        val configDir = Paths.get(
            System.getProperty("user.home"),
            ".lingma-plus",
            "scanner-cache"
        )
        Files.createDirectories(configDir)
        val projectId = getProjectId(projectBasePath)
        val cacheFileName = "${CACHE_FILE_PREFIX}${projectId}${CACHE_FILE_SUFFIX}"
        return configDir.resolve(cacheFileName).toFile()
    }
    
    /**
     * 保存缓存
     */
    fun saveCache(classes: List<CachedClassInfo>, projectBasePath: String?) {
        try {
            val cacheFile = getCacheFile(projectBasePath)
            val json = buildString {
                appendLine("[")
                classes.forEachIndexed { index, info ->
                    appendLine("  {")
                    appendLine("    \"className\": \"${escapeJson(info.className)}\",")
                    appendLine("    \"filePath\": \"${escapeJson(info.filePath)}\",")
                    appendLine("    \"methodCount\": ${info.methodCount},")
                    appendLine("    \"navigateCount\": ${info.navigateCount},")
                    appendLine("    \"fileSource\": \"${info.fileSource}\"")
                    append(if (index < classes.size - 1) "  }," else "  }")
                    appendLine()
                }
                appendLine("]")
            }
            cacheFile.writeText(json)
            logger.info("缓存已保存: ${classes.size} 个类 (项目: ${getProjectId(projectBasePath)})")
        } catch (e: Exception) {
            logger.error("保存缓存失败", e)
        }
    }
    
    /**
     * 加载缓存
     */
    fun loadCache(projectBasePath: String?): List<CachedClassInfo> {
        try {
            val cacheFile = getCacheFile(projectBasePath)
            if (!cacheFile.exists()) {
                logger.info("缓存文件不存在 (项目: ${getProjectId(projectBasePath)})")
                return emptyList()
            }
            
            val json = cacheFile.readText()
            val classes = mutableListOf<CachedClassInfo>()
            
            // 简单的JSON解析（手动解析，避免依赖）
            var i = 0
            while (i < json.length) {
                val objStart = json.indexOf("{", i)
                if (objStart == -1) break
                
                val objEnd = findMatchingBrace(json, objStart)
                if (objEnd == -1) break
                
                val objJson = json.substring(objStart, objEnd + 1)
                val className = extractStringValue(objJson, "className")
                val filePath = extractStringValue(objJson, "filePath")
                val methodCount = extractIntValue(objJson, "methodCount")
                val navigateCount = extractIntValue(objJson, "navigateCount")
                val fileSource = extractStringValue(objJson, "fileSource") ?: FileSource.PROJECT.name
                
                if (className != null && filePath != null && methodCount != null) {
                    classes.add(
                        CachedClassInfo(
                            className = className,
                            filePath = filePath,
                            methodCount = methodCount,
                            navigateCount = navigateCount ?: 0,
                            fileSource = fileSource
                        )
                    )
                }
                
                i = objEnd + 1
            }
            
            logger.info("缓存已加载: ${classes.size} 个类 (项目: ${getProjectId(projectBasePath)})")
            return classes
        } catch (e: Exception) {
            logger.error("加载缓存失败", e)
            return emptyList()
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache(projectBasePath: String?) {
        try {
            val cacheFile = getCacheFile(projectBasePath)
            if (cacheFile.exists()) {
                cacheFile.delete()
                logger.info("缓存已清除 (项目: ${getProjectId(projectBasePath)})")
            }
        } catch (e: Exception) {
            logger.error("清除缓存失败", e)
        }
    }
    
    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    private fun findMatchingBrace(json: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
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

