package com.xguard.plugin.strategy

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.xguard.plugin.model.CustomRiskCategory
import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskThresholds
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 策略管理器（Application-level Service）
 * 管理动态风险策略，支持热更新
 */
@Service(Service.Level.APP)
class StrategyManager {

    @Volatile
    private var currentPolicy: PolicyConfig = PolicyConfig()

    /** 策略文件监听器 */
    private var fileWatcher: java.nio.file.WatchService? = null

    /** 自定义类别注册表 */
    private val customCategories = ConcurrentHashMap<String, CustomRiskCategory>()

    companion object {
        const val POLICY_FILE_EXTENSION = ".xguard-policy.yaml"
        fun getInstance(): StrategyManager = ApplicationManager.getApplication().service()
    }

    /**
     * 加载策略配置
     * @param configSource 配置来源（文件路径或 YAML 内容）
     */
    fun loadPolicy(configSource: String): PolicyConfig {
        val file = File(configSource)
        return if (file.exists()) {
            loadPolicyFromFile(file)
        } else {
            loadPolicyFromContent(configSource)
        }
    }

    /**
     * 从文件加载策略
     */
    fun loadPolicyFromFile(file: File): PolicyConfig {
        val yaml = Yaml()
        val content = file.readText(Charsets.UTF_8)
        val data = yaml.load<Map<String, Any>>(content)

        val policy = parsePolicyConfig(data)
        currentPolicy = policy
        return policy
    }

    /**
     * 从 YAML 内容加载策略
     */
    fun loadPolicyFromContent(yamlContent: String): PolicyConfig {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlContent)
        val policy = parsePolicyConfig(data)
        currentPolicy = policy
        return policy
    }

    /**
     * 动态添加自定义风险类别（无需重启）
     */
    fun registerCustomRiskCategory(
        name: String,
        definition: String,
        examples: List<String>,
        severity: String = "HIGH",
        autoFixTemplate: String = ""
    ): Boolean {
        val category = CustomRiskCategory(
            name = name,
            label = name,
            definition = definition,
            examples = examples,
            severity = severity,
            autoFixTemplate = autoFixTemplate
        )
        customCategories[name] = category

        // 更新当前策略
        val updatedCategories = (currentPolicy.customCategories.filter { it.name != name } + category)
        currentPolicy = currentPolicy.copy(customCategories = updatedCategories)

        return true
    }

    /**
     * 移除自定义风险类别
     */
    fun unregisterCustomRiskCategory(name: String): Boolean {
        customCategories.remove(name)
        val updatedCategories = currentPolicy.customCategories.filter { it.name != name }
        currentPolicy = currentPolicy.copy(customCategories = updatedCategories)
        return true
    }

    /**
     * 获取当前策略配置
     */
    fun getCurrentPolicy(): PolicyConfig = currentPolicy

    /**
     * 更新风险阈值
     */
    fun updateThresholds(thresholds: RiskThresholds) {
        currentPolicy = currentPolicy.copy(thresholds = thresholds)
    }

    /**
     * 在项目目录中搜索策略文件
     */
    fun findPolicyFiles(projectPath: String): List<File> {
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) return emptyList()

        return projectDir.walkTopDown()
            .filter { it.name.endsWith(POLICY_FILE_EXTENSION) || it.name == ".xguard-policy.yaml" }
            .toList()
    }

    /**
     * 导出策略为 YAML
     */
    fun exportPolicyToYaml(policy: PolicyConfig = currentPolicy): String {
        val yaml = Yaml()
        val data = linkedMapOf<String, Any>(
            "version" to policy.version
        )

        if (policy.customCategories.isNotEmpty()) {
            data["custom_categories"] = policy.customCategories.map { cat ->
                linkedMapOf<String, Any>(
                    "name" to cat.name,
                    "definition" to cat.definition,
                    "examples" to cat.examples,
                    "severity" to cat.severity,
                    "auto_fix_template" to cat.autoFixTemplate
                )
            }
        }

        data["thresholds"] = linkedMapOf<String, Any>(
            "high" to policy.thresholds.high,
            "medium" to policy.thresholds.medium,
            "low" to policy.thresholds.low
        )

        return yaml.dump(data)
    }

    /**
     * 解析 YAML 配置为 PolicyConfig
     */
    @Suppress("UNCHECKED_CAST")
    private fun parsePolicyConfig(data: Map<String, Any>): PolicyConfig {
        val version = data["version"] as? String ?: "1.0"

        val categories = mutableListOf<CustomRiskCategory>()
        val rawCategories = data["custom_categories"] as? List<Map<String, Any>>
        if (rawCategories != null) {
            for (cat in rawCategories) {
                categories.add(CustomRiskCategory(
                    name = cat["name"] as? String ?: "",
                    label = cat["label"] as? String ?: cat["name"] as? String ?: "",
                    definition = cat["definition"] as? String ?: "",
                    examples = (cat["examples"] as? List<String>) ?: emptyList(),
                    severity = cat["severity"] as? String ?: "HIGH",
                    autoFixTemplate = cat["auto_fix_template"] as? String ?: ""
                ))
            }
        }

        val thresholdsData = data["thresholds"] as? Map<String, Any>
        val thresholds = if (thresholdsData != null) {
            RiskThresholds(
                high = (thresholdsData["high"] as? Number)?.toFloat() ?: 0.8f,
                medium = (thresholdsData["medium"] as? Number)?.toFloat() ?: 0.5f,
                low = (thresholdsData["low"] as? Number)?.toFloat() ?: 0.3f
            )
        } else {
            RiskThresholds()
        }

        return PolicyConfig(
            version = version,
            customCategories = categories,
            thresholds = thresholds
        )
    }
}
