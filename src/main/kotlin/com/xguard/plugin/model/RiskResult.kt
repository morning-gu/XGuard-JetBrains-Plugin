package com.xguard.plugin.model

import kotlinx.serialization.Serializable

/**
 * 风险检测结果
 */
@Serializable
data class RiskResult(
    /** 风险置信度 [0, 1] */
    val riskScore: Float,
    /** 细粒度风险类别标签 */
    val riskTag: String,
    /** 归因分析文本 */
    val explanation: String,
    /** 修复建议列表 */
    val suggestions: List<FixSuggestion> = emptyList(),
    /** 推理耗时（秒） */
    val inferenceTime: Double = 0.0,
    /** 策略版本 */
    val policyVersion: String = "default",
    /** 各风险维度的分值映射 */
    val riskScores: Map<String, Float> = emptyMap()
) {
    /** 是否存在风险（非安全且置信度超过阈值） */
    fun isRisky(threshold: Float = 0.5f): Boolean {
        return riskTag != "Safe-Safe" && riskScore >= threshold
    }

    /** 获取风险严重级别 */
    val severity: RiskSeverity
        get() {
            val category = XGuardRiskCategory.fromDisplayName(riskTag)
            return if (category == XGuardRiskCategory.SAFE) RiskSeverity.NONE
            else category.severity
        }
}

/**
 * 修复建议
 */
@Serializable
data class FixSuggestion(
    /** 建议标题 */
    val title: String,
    /** 详细说明 */
    val description: String,
    /** 可执行的代码替换文本 */
    val replacementCode: String = "",
    /** 建议置信度 */
    val confidence: Float = 0.8f
)

/**
 * Prompt 提取上下文
 */
data class PromptContext(
    /** 提取的 Prompt 文本内容 */
    val promptText: String,
    /** Prompt 在文档中的起始偏移 */
    val startOffset: Int,
    /** Prompt 在文档中的结束偏移 */
    val endOffset: Int,
    /** Prompt 来源类型 */
    val sourceType: PromptSourceType,
    /** 所属文件路径 */
    val filePath: String = ""
)

/**
 * Prompt 来源类型
 */
enum class PromptSourceType {
    /** LangChain 框架中的 Prompt */
    LANG_CHAIN,
    /** LlamaIndex 框架中的 Prompt */
    LLAMA_INDEX,
    /** 普通字符串字面量 */
    RAW_STRING,
    /** 注释中的 Prompt */
    COMMENT,
    /** 模板字符串 */
    TEMPLATE_LITERAL
}

/**
 * 检测模式
 */
enum class InferenceMode {
    /** 本地推理（YuFeng-XGuard-Reason-0.6B） */
    LOCAL,
    /** 云端推理（XGuard API） */
    CLOUD,
    /** 自动选择（优先云端，降级本地） */
    AUTO
}
