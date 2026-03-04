package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 通过 git 命令获取指定时间内、指定作者修改过的 Java 文件
 */
object GitModifiedFilesService {
    private val logger = thisLogger()
    
    /**
     * 获取最近 N 天内、某作者修改过的 .java 文件路径（去重）
     * @param projectBasePath 项目根目录
     * @param daysBack 回溯天数
     * @param author 作者名，空字符串表示使用当前 git 用户
     * @return 去重后的 .java 文件绝对路径列表
     */
    fun getModifiedJavaFiles(projectBasePath: String?, daysBack: Int, author: String): List<String> {
        if (projectBasePath.isNullOrBlank()) return emptyList()
        
        val baseDir = File(projectBasePath)
        if (!baseDir.isDirectory) return emptyList()
        
        val gitDir = File(baseDir, ".git")
        if (!gitDir.exists()) {
            logger.warn("项目目录下未找到 .git: $projectBasePath")
            return emptyList()
        }
        
        val effectiveAuthor = if (author.isBlank()) getCurrentGitUser(baseDir) else author
        val cmd = buildGitLogCommand(daysBack, effectiveAuthor)
        return runGitAndParseJavaFiles(baseDir, cmd)
    }
    
    /**
     * 获取当前 Git 暂存区（贮存区）中已暂存的 .java 文件的绝对路径列表
     * 仅包含已加入 Git 管理并添加到暂存区的修改/新增文件，忽略未跟踪文件
     */
    fun getStagedJavaFiles(projectBasePath: String?): List<String> {
        if (projectBasePath.isNullOrBlank()) return emptyList()
        
        val baseDir = File(projectBasePath)
        if (!baseDir.isDirectory) return emptyList()
        
        val gitDir = File(baseDir, ".git")
        if (!gitDir.exists()) {
            logger.warn("项目目录下未找到 .git: $projectBasePath")
            return emptyList()
        }
        
        val cmd = listOf(
            "git",
            "diff",
            "--cached",
            "--name-only",
            "--",
            "*.java"
        )
        return runGitAndParseJavaFiles(baseDir, cmd)
    }
    
    private fun getCurrentGitUser(workDir: File): String {
        return try {
            val process = ProcessBuilder("git", "config", "user.name")
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            val name = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor(3, TimeUnit.SECONDS)
            if (name.isNotBlank()) name else ""
        } catch (e: Exception) {
            logger.warn("无法获取当前 git 用户", e)
            ""
        }
    }
    
    private fun buildGitLogCommand(daysBack: Int, author: String): List<String> {
        val cmd = mutableListOf(
            "git",
            "log",
            "--since=${daysBack} days ago",
            "--name-only",
            "--pretty=format:"
        )
        if (author.isNotBlank()) {
            cmd.add("--author=$author")
        }
        cmd.add("--")
        cmd.add("*.java")
        return cmd
    }
    
    private fun runGitAndParseJavaFiles(baseDir: File, cmd: List<String>): List<String> {
        return try {
            val process = ProcessBuilder(cmd)
                .directory(baseDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exited = process.waitFor(15, TimeUnit.SECONDS)
            
            if (!exited || process.exitValue() != 0) {
                logger.warn("Git 命令执行异常: cmd=$cmd, exit=${process.exitValue()}, output=$output")
                return emptyList()
            }
            
            output.lineSequence()
                .map { it.trim() }
                .filter { it.endsWith(".java") }
                .distinct()
                .map { rel ->
                    File(baseDir, rel.replace("/", File.separator)).absolutePath
                }
                .toList()
        } catch (e: Exception) {
            logger.error("执行 git 命令失败: cmd=$cmd", e)
            emptyList()
        }
    }
}
