package com.xguard.plugin.inference

import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 推理适配器接口
 * 统一本地/云端推理接口，支持自动降级
 */
interface InferenceAdapter {
    /**
     * 执行风险检测推理
     * @param prompt 待检测的 Prompt 文本
     * @param policy 策略配置（可选）
     * @param enableReasoning 是否启用归因推理
     * @return 风险检测结果
     */
    suspend fun infer(
        prompt: String,
        policy: PolicyConfig? = null,
        enableReasoning: Boolean = true
    ): RiskResult

    /** 推理引擎是否可用 */
    fun isAvailable(): Boolean

    /** 获取推理引擎名称 */
    fun name(): String

    /**
     * 预热引擎：启动服务并做健康检查，不发送推理请求
     */
    fun warmUp() {}
}
