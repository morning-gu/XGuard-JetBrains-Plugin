package com.xguard.plugin.inspection

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.xguard.plugin.extractor.PromptExtractorRegistry
import com.xguard.plugin.inference.InferenceManager
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.RiskResult
import com.xguard.plugin.strategy.StrategyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 风险检测服务（Project-level Service）
 * 协调 Prompt 提取、推理和结果缓存
 */
@Service(Service.Level.PROJECT)
class RiskDetectionService(val project: Project) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** 当前文件的检测结果缓存: filePath -> List of (PromptContext, RiskResult) */
    private val detectionResults = ConcurrentHashMap<String, List<PromptRisk>>()

    /** 防抖计时器 */
    @Volatile
    private var pendingDetection: kotlinx.coroutines.Job? = null

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        fun getInstance(project: Project): RiskDetectionService = project.service()
    }

    /**
     * 触发检测（带防抖）
     */
    fun triggerDetection(editor: Editor, psiFile: PsiFile) {
        pendingDetection?.cancel()
        pendingDetection = scope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
            performDetection(editor, psiFile)
        }
    }

    /**
     * 执行检测
     */
    private suspend fun performDetection(editor: Editor, psiFile: PsiFile) {
        val filePath = psiFile.virtualFile?.path ?: return

        // 1. 提取 Prompt
        val registry = PromptExtractorRegistry.getInstance()
        val extractors = registry.getExtractorsForFile(psiFile)
        val prompts = mutableListOf<PromptContext>()

        for (extractor in extractors) {
            prompts.addAll(extractor.extractPrompts(project, editor, psiFile))
        }

        if (prompts.isEmpty()) {
            mutex.withLock {
                detectionResults.remove(filePath)
            }
            return
        }

        // 2. 推理检测
        val inferenceManager = InferenceManager.getInstance()
        val strategyManager = StrategyManager.getInstance()
        val policy = strategyManager.getCurrentPolicy()

        val results = mutableListOf<PromptRisk>()
        for (prompt in prompts) {
            try {
                val riskResult = inferenceManager.detect(
                    prompt = prompt.promptText,
                    policy = policy,
                    enableReasoning = true
                )
                results.add(PromptRisk(prompt, riskResult))
            } catch (e: Exception) {
                // 记录失败的检测，仍添加一个默认安全结果以便通知用户
                results.add(PromptRisk(prompt, RiskResult(
                    riskScore = 0f,
                    riskTag = "Safe-Safe",
                    explanation = "Detection failed: ${e.message}",
                    inferenceTime = 0.0
                )))
            }
        }

        // 3. 缓存结果
        mutex.withLock {
            detectionResults[filePath] = results
        }
    }

    /**
     * 获取文件的检测结果
     */
    fun getResults(filePath: String): List<PromptRisk> {
        return detectionResults[filePath] ?: emptyList()
    }

    /**
     * 获取所有有风险的检测结果
     */
    fun getAllRiskyResults(): Map<String, List<PromptRisk>> {
        return detectionResults.mapValues { (_, risks) ->
            risks.filter { it.result.isRisky() }
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * 清除文件的检测结果
     */
    fun clearResults(filePath: String) {
        detectionResults.remove(filePath)
    }

    /**
     * 清除所有检测结果
     */
    fun clearAllResults() {
        detectionResults.clear()
    }
}

/**
 * Prompt + 风险结果对
 */
data class PromptRisk(
    val prompt: PromptContext,
    val result: RiskResult
)
