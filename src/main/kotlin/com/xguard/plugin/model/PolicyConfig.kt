package com.xguard.plugin.model

import kotlinx.serialization.Serializable

/**
 * 策略配置
 */
@Serializable
data class PolicyConfig(
    val version: String = "1.0",
    val customCategories: List<CustomRiskCategory> = emptyList(),
    val thresholds: RiskThresholds = RiskThresholds(),
    val ignoreRules: List<IgnoreRule> = emptyList()
)

/**
 * 自定义风险类别
 */
@Serializable
data class CustomRiskCategory(
    val name: String,
    val label: String,
    val definition: String,
    val examples: List<String> = emptyList(),
    val severity: String = "HIGH",
    val autoFixTemplate: String = ""
)

/**
 * 风险阈值配置
 */
@Serializable
data class RiskThresholds(
    val high: Float = 0.8f,
    val medium: Float = 0.5f,
    val low: Float = 0.3f
)

/**
 * 忽略规则
 */
@Serializable
data class IgnoreRule(
    val riskTag: String,
    val scope: IgnoreScope = IgnoreScope.PROJECT,
    val filePath: String = ""
)

enum class IgnoreScope {
    LINE, FILE, PROJECT
}
