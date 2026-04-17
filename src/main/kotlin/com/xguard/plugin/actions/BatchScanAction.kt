package com.xguard.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VfsUtil
import com.xguard.plugin.inspection.RiskDetectionService
import com.xguard.plugin.inference.InferenceManager
import com.xguard.plugin.strategy.StrategyManager

/**
 * 批量扫描项目 Action
 */
class BatchScanAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "XGuard Batch Scan", true) {
            override fun run(indicator: ProgressIndicator) {
                val basePath = project.basePath ?: return
                val service = RiskDetectionService.getInstance(project)
                val inferenceManager = InferenceManager.getInstance()
                val strategyManager = StrategyManager.getInstance()
                val policy = strategyManager.getCurrentPolicy()

                // 查找项目中的所有 Python/JS/TS 文件
                val targetExtensions = setOf("py", "js", "ts", "jsx", "tsx", "java", "kt")
                val baseDir = java.io.File(basePath)
                val files = baseDir.walkTopDown()
                    .filter { f ->
                        f.isFile && f.extension in targetExtensions &&
                                !f.path.contains("node_modules") &&
                                !f.path.contains(".git") &&
                                !f.path.contains("__pycache__")
                    }
                    .toList()

                indicator.isIndeterminate = false
                var scannedCount = 0
                var riskCount = 0

                for (file in files) {
                    if (indicator.isCanceled) break
                    indicator.text = "Scanning: ${file.name}"
                    indicator.fraction = scannedCount.toDouble() / files.size

                    try {
                        val content = file.readText(Charsets.UTF_8)
                        val result = kotlinx.coroutines.runBlocking {
                            inferenceManager.detect(content, policy, true)
                        }
                        if (result.isRisky()) riskCount++
                    } catch (_: Exception) {
                        // 跳过无法读取的文件
                    }
                    scannedCount++
                }

                // 通知结果
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("XGuard.Notification")
                    .createNotification(
                        "XGuard Batch Scan Complete",
                        "Scanned $scannedCount files, found $riskCount risk(s)",
                        if (riskCount > 0) NotificationType.WARNING else NotificationType.INFORMATION
                    )
                notification.notify(project)
            }
        })
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
