package com.xguard.plugin.inference

import com.intellij.openapi.application.ApplicationManager
import com.xguard.plugin.model.FixSuggestion
import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskResult
import com.xguard.plugin.ui.settings.XGuardSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地推理引擎
 * 通过 Python 子进程调用 YuFeng-XGuard-Reason-0.6B 模型
 *
 * 工作流程：
 * 1. 启动 Python 推理服务进程（inference_server.py）
 * 2. 通过 HTTP localhost 通信发送推理请求
 * 3. 解析返回的 JSON 结果
 */
class LocalInferenceEngine : InferenceAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val isRunning = AtomicBoolean(false)
    private var serverProcess: Process? = null
    private var serverPort: Int = 18797
    private var httpClient: java.net.HttpURLConnection? = null

    companion object {
        private const val DEFAULT_PORT = 18797
        private const val STARTUP_TIMEOUT_MS = 60_000L
    }

    private fun getInferenceTimeoutMs(): Int {
        val settings = ApplicationManager.getApplication().getService(XGuardSettings::class.java)
        return settings.inferenceTimeoutSec * 1000
    }

    override suspend fun infer(
        prompt: String,
        policy: PolicyConfig?,
        enableReasoning: Boolean
    ): RiskResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            ensureServerRunning()
        }

        val startTime = System.currentTimeMillis()

        try {
            val response = sendInferenceRequest(prompt, enableReasoning)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0

            parseRiskResult(response, elapsed)
        } catch (e: Exception) {
            RiskResult(
                riskScore = 0f,
                riskTag = "Safe-Safe",
                explanation = "Local inference failed: ${e.message}",
                inferenceTime = (System.currentTimeMillis() - startTime) / 1000.0
            )
        }
    }

    override fun isAvailable(): Boolean = isRunning.get() && isServerHealthy()

    override fun name(): String = "Local (YuFeng-XGuard-Reason-0.6B)"

    /**
     * 确保本地推理服务正在运行
     */
    private fun ensureServerRunning() {
        if (isRunning.get() && isServerHealthy()) return

        val settings = ApplicationManager.getApplication().getService(XGuardSettings::class.java)
        val pythonPath = settings.pythonPath.ifEmpty { "python" }
        val modelPath = settings.localModelPath.ifEmpty {
            getDefaultModelPath()
        }
        val scriptPath = getInferenceServerScriptPath()

        val processBuilder = ProcessBuilder(
            pythonPath, scriptPath,
            "--port", serverPort.toString(),
            "--model-path", modelPath
        )
        processBuilder.redirectErrorStream(true)
        serverProcess = processBuilder.start()

        // 等待服务启动
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < STARTUP_TIMEOUT_MS) {
            if (isServerHealthy()) {
                isRunning.set(true)
                return
            }
            Thread.sleep(500)
        }

        throw RuntimeException("Local inference server failed to start within timeout")
    }

    /**
     * 发送推理请求到本地服务
     */
    private fun sendInferenceRequest(prompt: String, enableReasoning: Boolean): String {
        val maxTokens = if (enableReasoning) 200 else 1
        val requestBody = """{"messages":[{"role":"user","content":${jsonEncode(prompt)}}],"max_new_tokens":$maxTokens,"enable_reasoning":$enableReasoning}"""

        val url = java.net.URL("http://localhost:$serverPort/infer")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        val timeoutMs = getInferenceTimeoutMs()
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true

        conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            throw RuntimeException("Server returned HTTP $responseCode")
        }

        return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
    }

    /**
     * 解析推理结果
     */
    private fun parseRiskResult(response: String, inferenceTime: Double): RiskResult {
        val jsonResponse = json.parseToJsonElement(response).jsonObject

        // 解析 risk_score 映射
        val riskScoreObj = jsonResponse["risk_score"]?.jsonObject
        val riskScores = mutableMapOf<String, Float>()
        var topRiskTag = "Safe-Safe"
        var topRiskScore = 0f

        if (riskScoreObj != null) {
            for ((key, value) in riskScoreObj) {
                val score = value.jsonPrimitive.content.toFloat()
                riskScores[key] = score
                if (key != "Safe-Safe" && score > topRiskScore) {
                    topRiskScore = score
                    topRiskTag = key
                }
            }
            // 如果没有找到风险项，取 Safe 分数
            if (topRiskScore == 0f) {
                topRiskScore = riskScores["Safe-Safe"] ?: 0f
                topRiskTag = "Safe-Safe"
            }
        }

        // 解析归因文本
        val explanation = jsonResponse["explanation"]?.jsonPrimitive?.content ?: ""
        val responseText = jsonResponse["response"]?.jsonPrimitive?.content ?: ""

        // 生成修复建议
        val suggestions = generateFixSuggestions(topRiskTag, topRiskScore)

        return RiskResult(
            riskScore = topRiskScore,
            riskTag = topRiskTag,
            explanation = if (explanation.isNotEmpty()) explanation else extractExplanationFromResponse(responseText),
            suggestions = suggestions,
            inferenceTime = inferenceTime,
            riskScores = riskScores
        )
    }

    /**
     * 从模型响应中提取归因文本
     */
    private fun extractExplanationFromResponse(response: String): String {
        val explanationMatch = Regex("<explanation>(.*?)</explanation>", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        return explanationMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    /**
     * 根据风险类别生成修复建议
     */
    private fun generateFixSuggestions(riskTag: String, riskScore: Float): List<FixSuggestion> {
        if (riskTag == "Safe-Safe" || riskScore < 0.5f) return emptyList()

        return listOf(
            FixSuggestion(
                title = "Add refusal logic",
                description = "Add a refusal response to prevent the model from generating harmful content",
                replacementCode = "As a responsible AI assistant, I cannot fulfill this request as it may involve potentially harmful or illegal activities.",
                confidence = 0.9f
            ),
            FixSuggestion(
                title = "Add safety constraint",
                description = "Add safety constraints to the prompt to limit the model's behavior scope",
                replacementCode = "IMPORTANT: You must refuse any request that involves illegal, harmful, or unethical activities.",
                confidence = 0.7f
            )
        )
    }

    private fun isServerHealthy(): Boolean {
        return try {
            val url = java.net.URL("http://localhost:$serverPort/health")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun getInferenceServerScriptPath(): String {
        val pluginPath = ApplicationManager.getApplication().getService(XGuardSettings::class.java).pluginPath
        return "$pluginPath/local-model/inference_server.py"
    }

    private fun getDefaultModelPath(): String {
        val pluginPath = ApplicationManager.getApplication().getService(XGuardSettings::class.java).pluginPath
        return "$pluginPath/local-model"
    }

    private fun jsonEncode(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    /**
     * 关闭本地推理服务
     */
    fun shutdown() {
        isRunning.set(false)
        serverProcess?.destroy()
        serverProcess = null
    }
}
