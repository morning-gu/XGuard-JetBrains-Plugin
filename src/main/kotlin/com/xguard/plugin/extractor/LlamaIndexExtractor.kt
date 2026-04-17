package com.xguard.plugin.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType

/**
 * LlamaIndex 框架 Prompt 提取器
 * 识别 LlamaIndex 的 PromptTemplate、ChatPrompt 等模式
 */
class LlamaIndexExtractor : PromptExtractor {

    companion object {
        private val LLAMAINDEX_PATTERNS = setOf(
            "PromptTemplate", "RichPromptTemplate", "ChatPromptTemplate"
        )
    }

    override fun extractPrompts(project: Project, editor: Editor, psiFile: PsiFile): List<PromptContext> {
        val results = mutableListOf<PromptContext>()
        val filePath = psiFile.virtualFile?.path ?: ""
        val fileText = psiFile.text

        if (!fileText.contains("llama_index") && !fileText.contains("llamaindex")) return results

        PsiTreeUtil.processElements(psiFile) { element ->
            val text = element.text
            for (pattern in LLAMAINDEX_PATTERNS) {
                if (text.contains(pattern)) {
                    extractPromptFromElement(element, filePath)?.let { results.add(it) }
                }
            }
            true
        }

        return results
    }

    override fun supports(psiFile: PsiFile): Boolean {
        val name = psiFile.name.lowercase()
        return name.endsWith(".py")
    }

    private fun extractPromptFromElement(element: PsiElement, filePath: String): PromptContext? {
        val text = element.text
        val templateRegex = Regex("""template\s*=\s*["'](.+?)["']""", RegexOption.DOT_MATCHES_ALL)
        val match = templateRegex.find(text) ?: return null

        val templateText = match.groupValues[1]
        if (templateText.length < 10) return null

        return PromptContext(
            promptText = templateText,
            startOffset = element.textRange.startOffset + match.range.first,
            endOffset = element.textRange.startOffset + match.range.last,
            sourceType = PromptSourceType.LLAMA_INDEX,
            filePath = filePath
        )
    }
}
