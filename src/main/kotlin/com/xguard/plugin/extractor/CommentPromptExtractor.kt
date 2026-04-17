package com.xguard.plugin.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType

/**
 * 注释中的 Prompt 提取器
 * 识别代码注释中包含的 Prompt 指令
 */
class CommentPromptExtractor : PromptExtractor {

    companion object {
        private const val MIN_PROMPT_LENGTH = 20
        /** Prompt 标记关键词 */
        private val PROMPT_MARKERS = listOf(
            "prompt:", "PROMPT:", "system:", "SYSTEM:",
            "instruction:", "INSTRUCTION:", "task:", "TASK:"
        )
    }

    override fun extractPrompts(project: Project, editor: Editor, psiFile: PsiFile): List<PromptContext> {
        val results = mutableListOf<PromptContext>()
        val filePath = psiFile.virtualFile?.path ?: ""

        PsiTreeUtil.processElements(psiFile) { element ->
            if (element is PsiComment) {
                val commentText = element.text
                val promptContent = extractPromptFromComment(commentText)
                if (promptContent != null && promptContent.length >= MIN_PROMPT_LENGTH) {
                    results.add(PromptContext(
                        promptText = promptContent,
                        startOffset = element.textRange.startOffset,
                        endOffset = element.textRange.endOffset,
                        sourceType = PromptSourceType.COMMENT,
                        filePath = filePath
                    ))
                }
            }
            true
        }

        return results
    }

    override fun supports(psiFile: PsiFile): Boolean = true

    private fun extractPromptFromComment(comment: String): String? {
        // 去除注释符号
        val cleaned = comment.lines()
            .map { line -> line.trimStart(' ', '/', '*', '#', '-', '>') }
            .joinToString("\n")
            .trim()

        // 检查是否包含 Prompt 标记
        val lowerCleaned = cleaned.lowercase()
        for (marker in PROMPT_MARKERS) {
            val idx = lowerCleaned.indexOf(marker.lowercase())
            if (idx >= 0) {
                return cleaned.substring(idx + marker.length).trim()
            }
        }

        return null
    }
}
