package com.xguard.plugin.inference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 推理管理器（Application-level Service）
 * 管理推理引擎的生命周期，提供统一的推理入口
 */
@Service(Service.Level.APP)
class InferenceManager {

    private val localEngine = LocalInferenceEngine()
    private val cloudClient = CloudInferenceClient()
    private val router = SmartRouter(localEngine, cloudClient)

    private val mutex = Mutex()

    /** 结果缓存：LRU Cache 存储最近检测结果 */
    private val resultCache = object : LinkedHashMap<String, RiskResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RiskResult>?): Boolean {
            return size > 100
        }
    }

    companion object {
        fun getInstance(): InferenceManager = ApplicationManager.getApplication().service()
    }

    /**
     * 预热推理引擎
     * 仅启动本地服务并做健康检查，不发送推理请求
     */
    suspend fun warmUp() {
        router.warmUp()
    }

    /**
     * 执行风险检测
     * @param prompt 待检测文本
     * @param policy 策略配置
     * @param enableReasoning 是否启用归因
     * @param useCache 是否使用缓存
     */
    suspend fun detect(
        prompt: String,
        policy: PolicyConfig? = null,
        enableReasoning: Boolean = true,
        useCache: Boolean = true
    ): RiskResult {
        // 检查缓存
        if (useCache) {
            val cacheKey = buildCacheKey(prompt, policy)
            mutex.withLock {
                resultCache[cacheKey]?.let { return it }
            }
        }

        // 执行推理
        val result = router.infer(prompt, policy, enableReasoning)

        // 更新缓存
        if (useCache) {
            val cacheKey = buildCacheKey(prompt, policy)
            mutex.withLock {
                resultCache[cacheKey] = result
            }
        }

        return result
    }

    /**
     * 批量检测
     */
    suspend fun batchDetect(
        prompts: List<String>,
        policy: PolicyConfig? = null,
        enableReasoning: Boolean = true
    ): List<RiskResult> {
        return prompts.map { detect(it, policy, enableReasoning) }
    }

    /**
     * 获取当前推理引擎状态
     */
    fun getEngineStatus(): EngineStatus {
        return EngineStatus(
            localAvailable = localEngine.isAvailable(),
            cloudAvailable = cloudClient.isAvailable(),
            activeEngine = router.name(),
            cacheSize = resultCache.size
        )
    }

    /**
     * 关闭推理引擎
     */
    fun shutdown() {
        localEngine.shutdown()
        resultCache.clear()
    }

    private fun buildCacheKey(prompt: String, policy: PolicyConfig?): String {
        return "${prompt.hashCode()}_${policy?.version ?: "default"}"
    }
}

data class EngineStatus(
    val localAvailable: Boolean,
    val cloudAvailable: Boolean,
    val activeEngine: String,
    val cacheSize: Int
)
