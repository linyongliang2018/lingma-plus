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
     * 获取尚未提交到 Git 的 .java 文件（包含暂存区 + 工作区修改）
     * - 暂存区：已 git add 的修改
     * - 工作区：已修改但未 add 的文件（IDE 中显示为蓝色的）
     * 忽略未加入 Git 管理的文件（untracked）
     */
    fun getUncommittedJavaFiles(projectBasePath: String?): List<String> {
        if (projectBasePath.isNullOrBlank()) return emptyList()
        
        val baseDir = File(projectBasePath)
        if (!baseDir.isDirectory) return emptyList()
        
        val gitDir = File(baseDir, ".git")
        if (!gitDir.exists()) {
            logger.warn("项目目录下未找到 .git: $projectBasePath")
            return emptyList()
        }
        
        // 暂存区：已 add 的修改
        val staged = runGitAndParseJavaFiles(baseDir, listOf(
            "git", "diff", "--cached", "--name-only", "--", "*.java"
        ))
        // 工作区：已修改但未 add 的文件（蓝色）
        val unstaged = runGitAndParseJavaFiles(baseDir, listOf(
            "git", "diff", "--name-only", "--", "*.java"
        ))
        return (staged + unstaged).distinct()
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
