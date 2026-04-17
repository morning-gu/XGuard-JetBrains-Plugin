package com.xguard.plugin.ui.quickfix

import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.JavaPsiFacade

/**
 * 应用安全话术模板的快速修复
 */
class ApplySafeTemplateFix(
    private val fixTitle: String,
    private val replacementCode: String
) : LocalQuickFix {

    override fun getFamilyName(): String = "XGuard: Apply Safe Template"

    override fun getName(): String = "XGuard: $fixTitle"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val factory = JavaPsiFacade.getElementFactory(project)

        // 替换字符串字面量
        if (element is PsiLiteralExpression && element.value is String) {
            val newExpression = factory.createExpressionFromText(
                "\"${replacementCode.replace("\"", "\\\"")}\"",
                element.context
            )
            element.replace(newExpression)
        }
    }

    override fun availableInBatchMode(): Boolean = true
}
