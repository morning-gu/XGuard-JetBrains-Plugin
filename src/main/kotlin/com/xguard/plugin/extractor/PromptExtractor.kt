package com.xguard.plugin.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.xguard.plugin.model.PromptContext
import com.xguard.plugin.model.PromptSourceType

/**
 * Prompt 提取器接口
 * 从代码中智能提取待检测的 Prompt 内容
 */
interface PromptExtractor {
    /**
     * 从编辑器当前上下文提取 Prompt
     * @param project 当前项目
     * @param editor 当前编辑器
     * @param psiFile PSI 文件
     * @return 提取的 Prompt 片段列表
     */
    fun extractPrompts(project: Project, editor: Editor, psiFile: PsiFile): List<PromptContext>

    /** 该提取器是否支持当前文件类型 */
    fun supports(psiFile: PsiFile): Boolean
}

/**
 * Prompt 提取器注册中心
 * 管理所有已注册的提取器，按优先级匹配
 */
class PromptExtractorRegistry {
    private val extractors = mutableListOf<PromptExtractor>()

    fun register(extractor: PromptExtractor) {
        extractors.add(extractor)
    }

    fun getExtractorsForFile(psiFile: PsiFile): List<PromptExtractor> {
        return extractors.filter { it.supports(psiFile) }
    }

    companion object {
        @Volatile
        private var instance: PromptExtractorRegistry? = null

        fun getInstance(): PromptExtractorRegistry {
            return instance ?: synchronized(this) {
                instance ?: PromptExtractorRegistry().also {
                    it.register(RawStringExtractor())
                    it.register(LangChainExtractor())
                    it.register(LlamaIndexExtractor())
                    it.register(CommentPromptExtractor())
                    instance = it
                }
            }
        }
    }
}
