package com.liangyonglin.lingmaplus.lingma

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * 插件配置类，配置持久化到本地 ~/.lingma-plus/lingma-config.json
 * 参考 ClassInfoCache 实现，重启后自动加载
 */
@Service
class LingmaSettings {
    
    private var minDelaySeconds: Int = 20
    private var maxDelaySeconds: Int = 65
    private var gitDaysBack: Int = 5
    private var gitAuthor: String = ""  // 空表示当前用户
    
    init {
        loadFromCache()
    }
    
    private fun loadFromCache() {
        val cached = LingmaConfigCache.loadConfig()
        if (cached != null) {
            minDelaySeconds = cached.minDelaySeconds
            maxDelaySeconds = cached.maxDelaySeconds
            gitDaysBack = cached.gitDaysBack
            gitAuthor = cached.gitAuthor
        }
    }
    
    private fun saveToCache() {
        LingmaConfigCache.saveConfig(
            LingmaConfigCache.LingmaConfigData(
                minDelaySeconds = minDelaySeconds,
                maxDelaySeconds = maxDelaySeconds,
                gitDaysBack = gitDaysBack,
                gitAuthor = gitAuthor
            )
        )
    }
    
    companion object {
        fun getInstance(): LingmaSettings {
            return ApplicationManager.getApplication().getService(LingmaSettings::class.java)
        }
    }
    
    fun getMinDelaySeconds(): Int = minDelaySeconds
    fun getMaxDelaySeconds(): Int = maxDelaySeconds
    fun getGitDaysBack(): Int = gitDaysBack
    fun getGitAuthor(): String = gitAuthor
    
    fun setDelayRange(min: Int, max: Int) {
        minDelaySeconds = min
        maxDelaySeconds = max
        saveToCache()
    }
    
    fun setGitConfig(daysBack: Int, author: String) {
        gitDaysBack = daysBack
        gitAuthor = author
        saveToCache()
    }
}

