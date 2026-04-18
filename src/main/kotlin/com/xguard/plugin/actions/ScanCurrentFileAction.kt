package com.xguard.plugin.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorNotifications
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
        val detectionJob = service.triggerDetection(editor, psiFile)

        scope.launch {
            // 直接等待检测协程完成，不再轮询
            try {
                detectionJob.join()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 检测被取消（如用户再次点击扫描），继续用当前结果
            }

            val results = service.getResults(psiFile.virtualFile?.path ?: "")
            val timedOut = results.isEmpty()
            val riskyCount = results.count { it.result.isRisky() }
            val failedCount = results.count { it.result.riskTag.startsWith("Error-") }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // 1. 自动激活 XGuard Report 工具窗口
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("XGuard Report")
                toolWindow?.activate(null)

                // 2. 直接刷新报告面板
                //    Content.component 对于 SimpleToolWindowPanel 可能返回包装器而非面板本身，
                //    因此需要遍历组件树查找 XGuardReportPanel 实例
                toolWindow?.contentManager?.contents?.forEach { content ->
                    val panel = findReportPanel(content.component)
                    panel?.refreshData()
                }

                // 3. 显示完成通知
                val (title, message, type) = when {
                    timedOut -> Triple("XGuard Scan Timeout", "Scan timed out for ${psiFile.name}. The local Python service may not be running. Please check XGuard settings.", NotificationType.ERROR)
                    failedCount > 0 -> Triple("XGuard Scan Error", "$failedCount inference request(s) failed in ${psiFile.name}. Check if the local Python service is running.", NotificationType.ERROR)
                    riskyCount > 0 -> Triple("XGuard Scan Complete", "Found $riskyCount risk(s) in ${psiFile.name}", NotificationType.WARNING)
                    else -> Triple("XGuard Scan Complete", "No risks found in ${psiFile.name}", NotificationType.INFORMATION)
                }
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("XGuard.Notification")
                    .createNotification(title, message, type)

                notification.addAction(com.intellij.notification.NotificationAction.createSimple("Show Report") {
                    val tw = ToolWindowManager.getInstance(project).getToolWindow("XGuard Report")
                    tw?.activate(null)
                })

                notification.notify(project)

                // 4. 触发编辑器重新标注（让 RiskAnnotator 刷新高亮）
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile)

                // 5. 触发编辑器顶部通知栏更新（让 RiskNotificationProvider 刷新）
                psiFile.virtualFile?.let { vf ->
                    EditorNotifications.getInstance(project).updateNotifications(vf)
                }
            }
        }
    }

    /**
     * 递归查找 XGuardReportPanel 实例
     * Content.component 对于 SimpleToolWindowPanel 可能返回内部包装器，需要遍历子组件树
     */
    private fun findReportPanel(component: java.awt.Component): com.xguard.plugin.ui.XGuardReportPanel? {
        if (component is com.xguard.plugin.ui.XGuardReportPanel) return component
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findReportPanel(child)
                if (found != null) return found
            }
        }
        return null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}
