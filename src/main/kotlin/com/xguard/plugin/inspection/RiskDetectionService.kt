package com.xguard.plugin.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.xguard.plugin.extractor.PromptExtractorRegistry
import com.xguard.plugin.inference.InferenceManager
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType
import com.xguard.plugin.model.RiskResult
import com.xguard.plugin.strategy.StrategyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

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

    /** 结果更新监听器列表 */
    private val resultListeners = mutableListOf<Consumer<String>>()

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        fun getInstance(project: Project): RiskDetectionService = project.service()
    }

    /**
     * 注册结果更新监听器
     * 当检测结果更新时，监听器会收到更新的文件路径
     */
    fun addResultListener(listener: Consumer<String>) {
        resultListeners.add(listener)
    }

    /**
     * 移除结果更新监听器
     */
    fun removeResultListener(listener: Consumer<String>) {
        resultListeners.remove(listener)
    }

    /**
     * 触发检测（带防抖）
     * @return 检测任务的 Job，调用方可以 await 等待完成
     */
    fun triggerDetection(editor: Editor, psiFile: PsiFile): kotlinx.coroutines.Job {
        pendingDetection?.cancel()
        pendingDetection = scope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
            performDetection(editor, psiFile)
        }
        return pendingDetection!!
    }

    /**
     * 执行检测
     */
    private suspend fun performDetection(editor: Editor, psiFile: PsiFile) {
        // 1. 在 Read Action 中提取 Prompt（PSI 树访问必须在 Read Action 中执行）
        val filePath = ApplicationManager.getApplication().runReadAction<String?> {
            psiFile.virtualFile?.path
        } ?: return

        val prompts = ApplicationManager.getApplication().runReadAction<List<PromptContext>> {
            val registry = PromptExtractorRegistry.getInstance()
            val extractors = registry.getExtractorsForFile(psiFile)
            val result = mutableListOf<PromptContext>()

            for (extractor in extractors) {
                result.addAll(extractor.extractPrompts(project, editor, psiFile))
            }

            // 如果提取器未找到任何 Prompt，将整个文件内容作为待检测文本
            if (result.isEmpty()) {
                val document = editor.document
                val fileContent = document.text.trim()
                if (fileContent.isNotEmpty()) {
                    result.add(PromptContext(
                        promptText = fileContent,
                        startOffset = 0,
                        endOffset = document.textLength,
                        sourceType = PromptSourceType.RAW_STRING,
                        filePath = filePath
                    ))
                }
            }

            result
        }

        // 提取结果为空，清除缓存并返回
        if (prompts.isEmpty()) {
            mutex.withLock {
                detectionResults.remove(filePath)
            }
            return
        }

        // 2. 推理检测（在后台线程执行，不需要 Read Action）
        val inferenceManager = InferenceManager.getInstance()
        val strategyManager = StrategyManager.getInstance()
        val policy = strategyManager.getCurrentPolicy()

        val results = mutableListOf<PromptRisk>()
        for (prompt in prompts) {
            try {
                val riskResult = inferenceManager.detect(
                    prompt = prompt.promptText,
                    policy = policy,
                    enableReasoning = com.xguard.plugin.ui.settings.XGuardSettings.getInstance().enableReasoning
                )
                results.add(PromptRisk(prompt, riskResult))
            } catch (e: Exception) {
                // 推理失败时使用特殊标签标记，以便用户能感知到服务不可用
                results.add(PromptRisk(prompt, RiskResult(
                    riskScore = -1f,
                    riskTag = "Error-InferenceFailed",
                    explanation = "Detection failed: ${e.message}. Please check if the local Python service is running.",
                    inferenceTime = 0.0
                )))
            }
        }

        // 3. 缓存结果
        mutex.withLock {
            detectionResults[filePath] = results
        }

        // 4. 通知监听器
        for (listener in resultListeners) {
            listener.accept(filePath)
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
     * 获取所有检测结果（包含 Safe 的）
     */
    fun getAllResults(): Map<String, List<PromptRisk>> {
        return detectionResults.toMap()
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
