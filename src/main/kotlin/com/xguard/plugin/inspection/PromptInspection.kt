package com.xguard.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.xguard.plugin.model.RiskSeverity
import com.xguard.plugin.ui.quickfix.ApplySafeTemplateFix

/**
 * XGuard Prompt 安全检查
 * LocalInspectionTool 实现，在 IDE 检查框架中运行
 */
class PromptInspection : LocalInspectionTool() {

    override fun checkFile(
        file: PsiFile,
        manager: com.intellij.codeInspection.InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        val project = file.project
        val service = RiskDetectionService.getInstance(project)
        val filePath = file.virtualFile?.path ?: return null

        val results = service.getResults(filePath)
        if (results.isEmpty()) return null

        val descriptors = mutableListOf<ProblemDescriptor>()

        for (promptRisk in results) {
            if (!promptRisk.result.isRisky()) continue

            val element = findElementAtOffset(file, promptRisk.prompt.startOffset) ?: continue

            val highlightType = when (promptRisk.result.severity) {
                RiskSeverity.HIGH -> ProblemHighlightType.ERROR
                RiskSeverity.MEDIUM -> ProblemHighlightType.WARNING
                RiskSeverity.LOW -> ProblemHighlightType.WEAK_WARNING
                RiskSeverity.NONE -> continue
            }

            val fixes = mutableListOf<com.intellij.codeInspection.LocalQuickFix>()
            for (suggestion in promptRisk.result.suggestions) {
                fixes.add(ApplySafeTemplateFix(suggestion.title, suggestion.replacementCode))
            }

            val descriptor = manager.createProblemDescriptor(
                element,
                buildTooltipText(promptRisk),
                fixes.toTypedArray(),
                highlightType,
                isOnTheFly
            )
            descriptors.add(descriptor)
        }

        return descriptors.toTypedArray()
    }

    private fun findElementAtOffset(file: PsiFile, offset: Int): PsiElement? {
        if (offset < 0 || offset >= file.textLength) return null
        return file.findElementAt(offset)
    }

    private fun buildTooltipText(promptRisk: PromptRisk): String {
        val result = promptRisk.result
        return buildString {
            append("XGuard: ")
            append(result.riskTag)
            append(" (")
            append(String.format("%.2f", result.riskScore))
            append(")")
            if (result.explanation.isNotEmpty()) {
                append(" - ")
                append(result.explanation.take(200))
            }
        }
    }
}
