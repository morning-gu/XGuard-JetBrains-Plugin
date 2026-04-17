package com.xguard.plugin.inference

import com.xguard.plugin.model.InferenceMode
import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskResult
import com.xguard.plugin.ui.settings.XGuardSettings
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 智能路由器
 * 根据网络状态和配置自动选择推理后端
 * 支持自动降级：云端不可用时降级到本地
 */
class SmartRouter(
    private val local: LocalInferenceEngine,
    private val cloud: CloudInferenceClient
) : InferenceAdapter {

    @Volatile
    private var cloudFailureCount = 0
    @Volatile
    private var lastCloudFailureTime = 0L

    companion object {
        /** 连续失败次数阈值，超过后暂时禁用云端 */
        private const val CIRCUIT_BREAKER_THRESHOLD = 3
        /** 熔断恢复时间（毫秒） */
        private const val CIRCUIT_BREAKER_RESET_MS = 30_000L
    }

    override suspend fun infer(
        prompt: String,
        policy: PolicyConfig?,
        enableReasoning: Boolean
    ): RiskResult {
        val mode = getInferenceMode()

        return when (mode) {
            InferenceMode.LOCAL -> local.infer(prompt, policy, enableReasoning)
            InferenceMode.CLOUD -> {
                try {
                    val result = cloud.infer(prompt, policy, enableReasoning)
                    cloudFailureCount = 0
                    result
                } catch (e: Exception) {
                    recordCloudFailure()
                    // 降级到本地
                    local.infer(prompt, policy, enableReasoning)
                }
            }
            InferenceMode.AUTO -> {
                if (shouldUseCloud()) {
                    try {
                        val result = cloud.infer(prompt, policy, enableReasoning)
                        cloudFailureCount = 0
                        result
                    } catch (e: Exception) {
                        recordCloudFailure()
                        local.infer(prompt, policy, enableReasoning)
                    }
                } else {
                    local.infer(prompt, policy, enableReasoning)
                }
            }
        }
    }

    override fun isAvailable(): Boolean {
        return local.isAvailable() || cloud.isAvailable()
    }

    override fun name(): String = "SmartRouter"

    private fun getInferenceMode(): InferenceMode {
        val settings = ApplicationManager.getApplication().getService(XGuardSettings::class.java)
        return settings.inferenceMode
    }

    /**
     * 判断是否应使用云端
     * 考虑网络状态和熔断器状态
     */
    private fun shouldUseCloud(): Boolean {
        if (!isNetworkAvailable()) return false
        if (isCircuitBreakerOpen()) return false
        return cloud.isAvailable()
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val url = java.net.URL("https://www.google.com")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun isCircuitBreakerOpen(): Boolean {
        if (cloudFailureCount >= CIRCUIT_BREAKER_THRESHOLD) {
            val elapsed = System.currentTimeMillis() - lastCloudFailureTime
            if (elapsed < CIRCUIT_BREAKER_RESET_MS) {
                return true
            }
            // 半开状态：允许一次尝试
            cloudFailureCount = CIRCUIT_BREAKER_THRESHOLD - 1
        }
        return false
    }

    private fun recordCloudFailure() {
        cloudFailureCount++
        lastCloudFailureTime = System.currentTimeMillis()
    }
}
