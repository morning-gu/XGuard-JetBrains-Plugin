package com.xguard.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.xguard.plugin.inspection.RiskDetectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 扫描当前文件 Action
 */
class ScanCurrentFileAction : AnAction() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // 立即通知用户扫描已开始
        val startNotification = NotificationGroupManager.getInstance()
            .getNotificationGroup("XGuard.Notification")
            .createNotification(
                "XGuard Scan Started",
                "Scanning ${psiFile.name}...",
                NotificationType.INFORMATION
            )
        startNotification.notify(project)

        val service = RiskDetectionService.getInstance(project)
        service.triggerDetection(editor, psiFile)

        scope.launch {
            // 等待检测完成（通过轮询结果，最多等待配置的超时时间）
            val settings = com.xguard.plugin.ui.settings.XGuardSettings.getInstance()
            val maxWaitMs = settings.inferenceTimeoutSec * 1000L + 5000L
            val startTime = System.currentTimeMillis()
            var results: List<com.xguard.plugin.inspection.PromptRisk>

            do {
                kotlinx.coroutines.delay(500)
                results = service.getResults(psiFile.virtualFile?.path ?: "")
            } while (results.isEmpty() && System.currentTimeMillis() - startTime < maxWaitMs)

            val riskyCount = results.count { it.result.isRisky() }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("XGuard.Notification")
                    .createNotification(
                        "XGuard Scan Complete",
                        if (results.isEmpty()) "No prompts found in ${psiFile.name}" else "Found $riskyCount risk(s) in ${psiFile.name}",
                        if (riskyCount > 0) NotificationType.WARNING else NotificationType.INFORMATION
                    )
                notification.notify(project)
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}
