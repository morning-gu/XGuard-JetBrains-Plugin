package com.xguard.plugin.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiLiteralExpression
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType

/**
 * 普通字符串字面量提取器
 * 提取代码中的字符串字面量作为待检测 Prompt
 */
class RawStringExtractor : PromptExtractor {

    companion object {
        /** 最小 Prompt 长度（过短的字符串不检测） */
        private const val MIN_PROMPT_LENGTH = 10
        /** 最大 Prompt 长度（过长的字符串跳过，避免性能问题） */
        private const val MAX_PROMPT_LENGTH = 10000
    }

    override fun extractPrompts(project: Project, editor: Editor, psiFile: PsiFile): List<PromptContext> {
        val results = mutableListOf<PromptContext>()
        val filePath = psiFile.virtualFile?.path ?: ""

        PsiTreeUtil.processElements(psiFile) { element ->
            if (element is PsiLiteralExpression) {
                val value = element.value
                if (value is String) {
                    val trimmed = value.trim()
                    if (trimmed.length in MIN_PROMPT_LENGTH..MAX_PROMPT_LENGTH && looksLikePrompt(trimmed)) {
                        val textRange = element.textRange
                        results.add(PromptContext(
                            promptText = trimmed,
                            startOffset = textRange.startOffset,
                            endOffset = textRange.endOffset,
                            sourceType = PromptSourceType.RAW_STRING,
                            filePath = filePath
                        ))
                    }
                }
            }
            true
        }

        return results
    }

    override fun supports(psiFile: PsiFile): Boolean = true

    /**
     * 启发式判断字符串是否像 Prompt
     * 包含指令性词汇或足够长的文本
     */
    private fun looksLikePrompt(text: String): Boolean {
        if (text.length >= 50) return true

        val promptIndicators = listOf(
            "you are", "act as", "please", "help me", "generate",
            "write", "create", "translate", "summarize", "explain",
            "analyze", "how to", "what is", "can you", "I want",
            "prompt", "instruction", "system", "user", "assistant",
            "你是", "请", "帮我", "生成", "写", "创建", "翻译", "总结", "解释"
        )

        val lowerText = text.lowercase()
        return promptIndicators.any { lowerText.contains(it) }
    }
}
