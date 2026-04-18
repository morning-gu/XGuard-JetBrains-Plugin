package com.xguard.plugin.inspection

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.xguard.plugin.model.RiskSeverity

/**
 * 风险标注器
 * 实时在编辑器中标注风险代码段
 */
class RiskAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        val project = file.project
        val service = RiskDetectionService.getInstance(project)
        val filePath = file.virtualFile?.path ?: return

        val results = service.getResults(filePath)

        for (promptRisk in results) {
            val prompt = promptRisk.prompt
            val result = promptRisk.result

            // 跳过 Safe 结果，不标注
            if (!result.isRisky() && !result.riskTag.startsWith("Error-")) continue

            // 检查当前元素是否在风险 Prompt 范围内
            val elementStart = element.textRange.startOffset
            val elementEnd = element.textRange.endOffset

            if (elementStart >= prompt.startOffset && elementEnd <= prompt.endOffset) {
                val severity = when {
                    result.riskTag.startsWith("Error-") -> HighlightSeverity.ERROR
                    else -> when (result.severity) {
                        RiskSeverity.HIGH -> HighlightSeverity.ERROR
                        RiskSeverity.MEDIUM -> HighlightSeverity.WARNING
                        RiskSeverity.LOW -> HighlightSeverity.WEAK_WARNING
                        RiskSeverity.NONE -> continue
                    }
                }

                val message = buildAnnotationMessage(result)

                val annotation = holder.newAnnotation(severity, message)
                    .range(element.textRange)

                // 添加 Tooltip
                annotation.tooltip(buildTooltipText(result))

                annotation.create()
            }
        }
    }

    private fun buildAnnotationMessage(result: com.xguard.plugin.model.RiskResult): String {
        return "XGuard: ${result.riskTag} (${String.format("%.2f", result.riskScore)})"
    }

    private fun buildTooltipText(result: com.xguard.plugin.model.RiskResult): String {
        return buildString {
            append("<b>XGuard Security Warning</b><br>")
            append("<b>Risk:</b> ${result.riskTag}<br>")
            append("<b>Score:</b> ${String.format("%.4f", result.riskScore)}<br>")
            if (result.explanation.isNotEmpty()) {
                append("<b>Explanation:</b> ${result.explanation.take(500)}<br>")
            }
            if (result.suggestions.isNotEmpty()) {
                append("<br><b>Suggestions:</b><br>")
                for (s in result.suggestions) {
                    append("- ${s.title}: ${s.description.take(100)}<br>")
                }
            }
        }
    }
}
