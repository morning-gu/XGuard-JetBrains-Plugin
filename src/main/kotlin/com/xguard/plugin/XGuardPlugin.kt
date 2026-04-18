package com.xguard.plugin

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.xguard.plugin.inspection.RiskDetectionService
import com.xguard.plugin.inference.InferenceManager
import com.xguard.plugin.strategy.StrategyManager
import com.xguard.plugin.ui.settings.XGuardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * XGuard 插件主入口
 * 管理插件生命周期和事件监听
 */
class XGuardPlugin : ProjectActivity {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun execute(project: Project) {
        loadProjectPolicy(project)
        registerEditorListeners(project)
        warmUpInferenceEngine()
    }

    private fun loadProjectPolicy(project: Project) {
        val basePath = project.basePath ?: return
        val strategyManager = StrategyManager.getInstance()
        val policyFiles = strategyManager.findPolicyFiles(basePath)
        if (policyFiles.isNotEmpty()) {
            strategyManager.loadPolicyFromFile(policyFiles.first())
        }
    }

    private fun registerEditorListeners(project: Project) {
        val settings = XGuardSettings.getInstance()
        if (!settings.enableRealTimeDetection) return

        // 使用 EditorFactory 的多项目监听器
        val parentDisposable = Disposer.newDisposable("XGuardDocumentListener")
        Disposer.register(project, parentDisposable)

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (!settings.enableRealTimeDetection) return
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(event.document) ?: return
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                    val service = RiskDetectionService.getInstance(project)
                    service.triggerDetection(editor, psiFile)
                }
            },
            parentDisposable
        )
    }

    private fun warmUpInferenceEngine() {
        scope.launch {
            try {
                val inferenceManager = InferenceManager.getInstance()
                inferenceManager.warmUp()
            } catch (_: Exception) {
                // 预热失败不影响插件使用
            }
        }
    }
}
