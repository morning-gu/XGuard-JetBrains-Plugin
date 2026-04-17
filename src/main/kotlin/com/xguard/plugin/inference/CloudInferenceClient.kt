package com.xguard.plugin.inference

import com.xguard.plugin.model.FixSuggestion
import com.xguard.plugin.model.PolicyConfig
import com.xguard.plugin.model.RiskResult
import com.xguard.plugin.ui.settings.XGuardSettings
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 云端推理客户端
 * 调用 XGuard Cloud API（OpenAI 兼容格式）
 */
class CloudInferenceClient : InferenceAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val DEFAULT_ENDPOINT = "https://api.xguard.ai/v1/guardrail/infer"
        private const val TIMEOUT_MS = 3000
        private const val MAX_RETRIES = 2
    }

    override suspend fun infer(
        prompt: String,
        policy: PolicyConfig?,
        enableReasoning: Boolean
    ): RiskResult = withContext(Dispatchers.IO) {
        val settings = ApplicationManager.getApplication().getService(XGuardSettings::class.java)
        val endpoint = settings.cloudEndpoint.ifEmpty { DEFAULT_ENDPOINT }
        val apiKey = settings.cloudApiKey

        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            try {
                val response = sendCloudRequest(endpoint, apiKey, prompt, policy, enableReasoning)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                return@withContext parseCloudResponse(response, elapsed)
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(500L * (attempt + 1))
                }
            }
        }

        RiskResult(
            riskScore = 0f,
            riskTag = "Safe-Safe",
            explanation = "Cloud inference failed: ${lastException?.message}",
            inferenceTime = (System.currentTimeMillis() - startTime) / 1000.0
        )
    }

    override fun isAvailable(): Boolean {
        val settings = ApplicationManager.getApplication().getService(XGuardSettings::class.java)
        return settings.cloudEndpoint.isNotEmpty() && settings.cloudApiKey.isNotEmpty()
    }

    override fun name(): String = "Cloud (XGuard API)"

    private fun sendCloudRequest(
        endpoint: String,
        apiKey: String,
        prompt: String,
        policy: PolicyConfig?,
        enableReasoning: Boolean
    ): String {
        val requestBody = buildRequestBody(prompt, policy, enableReasoning)

        val url = java.net.URL(endpoint)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS * 2
        conn.doOutput = true

        conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val errorBody = try {
                BufferedReader(InputStreamReader(conn.errorStream, Charsets.UTF_8)).readText()
            } catch (_: Exception) { "" }
            throw RuntimeException("Cloud API returned HTTP $responseCode: $errorBody")
        }

        return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
    }

    private fun buildRequestBody(
        prompt: String,
        policy: PolicyConfig?,
        enableReasoning: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("""{"messages":[{"role":"user","content":${jsonEncode(prompt)}}],"enable_reasoning":$enableReasoning,"stream":false""")

        if (policy != null && policy.customCategories.isNotEmpty()) {
            sb.append(""","policy":{"custom_categories":[""")
            policy.customCategories.forEachIndexed { idx, cat ->
                if (idx > 0) sb.append(",")
                sb.append("""{"name":"${cat.name}","definition":"${jsonEncode(cat.definition)}"}""")
            }
            sb.append("]}")
        }

        sb.append("}")
        return sb.toString()
    }

    private fun parseCloudResponse(response: String, inferenceTime: Double): RiskResult {
        val jsonResponse = json.parseToJsonElement(response).jsonObject

        val riskScore = jsonResponse["risk_score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        val riskTag = jsonResponse["risk_tag"]?.jsonPrimitive?.content ?: "Safe-Safe"
        val explanation = jsonResponse["explanation"]?.jsonPrimitive?.content ?: ""
        val policyApplied = jsonResponse["policy_applied"]?.jsonPrimitive?.content ?: "default"

        val riskScores = mutableMapOf<String, Float>()
        val riskScoreObj = jsonResponse["risk_scores"]?.jsonObject
        if (riskScoreObj != null) {
            for ((key, value) in riskScoreObj) {
                riskScores[key] = value.jsonPrimitive.content.toFloatOrNull() ?: 0f
            }
        }

        val suggestions = mutableListOf<FixSuggestion>()
        val suggestionsArr = jsonResponse["suggestions"]?.jsonArray
        if (suggestionsArr != null) {
            for (item in suggestionsArr) {
                val obj = item.jsonObject
                suggestions.add(FixSuggestion(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    replacementCode = obj["replacement_code"]?.jsonPrimitive?.content ?: "",
                    confidence = obj["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.8f
                ))
            }
        }

        if (suggestions.isEmpty() && riskTag != "Safe-Safe" && riskScore >= 0.5f) {
            suggestions.add(FixSuggestion(
                title = "Add refusal logic",
                description = "Add a refusal response to prevent harmful content generation",
                replacementCode = "As a responsible AI assistant, I cannot fulfill this request.",
                confidence = 0.9f
            ))
        }

        return RiskResult(
            riskScore = riskScore,
            riskTag = riskTag,
            explanation = explanation,
            suggestions = suggestions,
            inferenceTime = inferenceTime,
            policyVersion = policyApplied,
            riskScores = riskScores
        )
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
}
