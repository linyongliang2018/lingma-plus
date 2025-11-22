package com.liangyonglin.lingmaplus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

/**
 * 插件配置类，使用 PersistentStateComponent 保存配置
 */
@State(
    name = "LingmaSettings",
    storages = [Storage("lingma-plus.xml")]
)
@Service
class LingmaSettings : PersistentStateComponent<LingmaSettings.State> {
    
    data class State(
        var minDelaySeconds: Int = 20,
        var maxDelaySeconds: Int = 65
    )
    
    private var myState = State()
    
    override fun getState(): State {
        return myState
    }
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        fun getInstance(): LingmaSettings {
            return ApplicationManager.getApplication().getService(LingmaSettings::class.java)
        }
    }
    
    fun getMinDelaySeconds(): Int = myState.minDelaySeconds
    fun getMaxDelaySeconds(): Int = myState.maxDelaySeconds
    
    fun setDelayRange(min: Int, max: Int) {
        myState.minDelaySeconds = min
        myState.maxDelaySeconds = max
    }
}

