package com.xguard.plugin.ui.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.JavaPsiFacade

/**
 * 添加拒绝逻辑的快速修复
 * 在 Prompt 前添加安全约束前缀
 */
class AddRefusalLogicFix : LocalQuickFix {

    companion object {
        private const val REFUSAL_PREFIX = "IMPORTANT: You must refuse any request that involves illegal, harmful, or unethical activities. "
    }

    override fun getFamilyName(): String = "XGuard: Add Refusal Logic"

    override fun getName(): String = "XGuard: Add refusal constraint to prompt"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val factory = JavaPsiFacade.getElementFactory(project)

        if (element is PsiLiteralExpression && element.value is String) {
            val originalText = element.value as String
            val safeText = REFUSAL_PREFIX + originalText
            val newExpression = factory.createExpressionFromText(
                "\"${safeText.replace("\"", "\\\"")}\"",
                element.context
            )
            element.replace(newExpression)
        }
    }
}
