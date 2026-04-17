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

        val service = RiskDetectionService.getInstance(project)
        service.triggerDetection(editor, psiFile)

        scope.launch {
            kotlinx.coroutines.delay(1000)
            val results = service.getResults(psiFile.virtualFile?.path ?: "")
            val riskyCount = results.count { it.result.isRisky() }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("XGuard.Notification")
                    .createNotification(
                        "XGuard Scan Complete",
                        "Found $riskyCount risk(s) in ${psiFile.name}",
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
