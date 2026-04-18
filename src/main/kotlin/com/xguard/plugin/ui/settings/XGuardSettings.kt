package com.xguard.plugin.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import com.xguard.plugin.model.InferenceMode

/**
 * XGuard 插件设置（持久化）
 */
@Service(Service.Level.APP)
@State(name = "XGuardSettings", storages = [Storage("xguard-settings.xml")])
class XGuardSettings : PersistentStateComponent<XGuardSettings> {

    /** 推理模式 */
    var inferenceMode: InferenceMode = InferenceMode.AUTO

    /** 云端 API 端点 */
    var cloudEndpoint: String = ""

    /** 云端 API Key */
    var cloudApiKey: String = ""

    /** 本地模型路径 */
    var localModelPath: String = ""

    /** Python 解释器路径 */
    var pythonPath: String = "python"

    /** 检测灵敏度 (0.0 - 1.0) */
    var sensitivity: Float = 0.5f

    /** 风险阈值 - 高 */
    var thresholdHigh: Float = 0.8f

    /** 风险阈值 - 中 */
    var thresholdMedium: Float = 0.5f

    /** 风险阈值 - 低 */
    var thresholdLow: Float = 0.3f

    /** 是否启用实时检测 */
    var enableRealTimeDetection: Boolean = true

    /** 防抖延迟（毫秒） */
    var debounceDelayMs: Long = 300L

    /** 插件安装路径（运行时设置） */
    var pluginPath: String = ""

    /** 是否启用归因推理 */
    var enableReasoning: Boolean = false

    /** 本地推理超时时间（秒） */
    var inferenceTimeoutSec: Int = 300

    override fun getState(): XGuardSettings = this

    override fun loadState(state: XGuardSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): XGuardSettings = ApplicationManager.getApplication().service()
    }
}
