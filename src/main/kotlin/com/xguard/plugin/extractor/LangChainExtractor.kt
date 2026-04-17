package com.xguard.plugin.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType

/**
 * LangChain 框架 Prompt 提取器
 * 识别 LangChain 特有的 PromptTemplate、ChatPromptTemplate 等模式
 */
class LangChainExtractor : PromptExtractor {

    companion object {
        private val LANGCHAIN_CLASSES = setOf(
            "PromptTemplate", "ChatPromptTemplate", "FewShotPromptTemplate",
            "FewShotChatMessagePromptTemplate", "ChatMessagePromptTemplate",
            "SystemMessagePromptTemplate", "HumanMessagePromptTemplate",
            "AIMessagePromptTemplate"
        )
        private val LANGCHAIN_IMPORTS = setOf(
            "langchain", "langchain_core", "langchain_community"
        )
    }

    override fun extractPrompts(project: Project, editor: Editor, psiFile: PsiFile): List<PromptContext> {
        val results = mutableListOf<PromptContext>()
        val filePath = psiFile.virtualFile?.path ?: ""
        val fileText = psiFile.text

        // 检查是否导入了 LangChain
        if (!hasLangChainImport(fileText)) return results

        // 提取 PromptTemplate 构造中的 template 参数
        PsiTreeUtil.processElements(psiFile) { element ->
            val text = element.text
            for (cls in LANGCHAIN_CLASSES) {
                if (text.contains(cls)) {
                    extractTemplateFromElement(element, filePath)?.let { results.add(it) }
                }
            }
            true
        }

        return results
    }

    override fun supports(psiFile: PsiFile): Boolean {
        val name = psiFile.name.lowercase()
        return name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".ts")
    }

    private fun hasLangChainImport(fileText: String): Boolean {
        return LANGCHAIN_IMPORTS.any { fileText.contains(it) }
    }

    private fun extractTemplateFromElement(element: PsiElement, filePath: String): PromptContext? {
        val text = element.text
        // 匹配 template="..." 或 template="""...""" 模式
        val templateRegex = Regex("""template\s*=\s*["'](.+?)["']""", RegexOption.DOT_MATCHES_ALL)
        val match = templateRegex.find(text) ?: return null

        val templateText = match.groupValues[1]
        if (templateText.length < 10) return null

        return PromptContext(
            promptText = templateText,
            startOffset = element.textRange.startOffset + match.range.first,
            endOffset = element.textRange.startOffset + match.range.last,
            sourceType = PromptSourceType.LANG_CHAIN,
            filePath = filePath
        )
    }
}
