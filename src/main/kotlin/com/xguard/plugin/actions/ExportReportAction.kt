package com.xguard.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.xguard.plugin.inspection.RiskDetectionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 导出安全审计报告 Action
 */
class ExportReportAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RiskDetectionService.getInstance(project)
        val allRisks = service.getAllRiskyResults()

        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Markdown Files", "md")
        fileChooser.selectedFile = File("xguard-security-report.md")

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val report = generateMarkdownReport(allRisks)
            fileChooser.selectedFile.writeText(report)

            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("XGuard.Notification")
                .createNotification(
                    "XGuard Report Exported",
                    "Report saved to: ${fileChooser.selectedFile.name}",
                    NotificationType.INFORMATION
                )
            notification.notify(project)
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun generateMarkdownReport(
        allRisks: Map<String, List<com.xguard.plugin.inspection.PromptRisk>>
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        sb.appendLine("# XGuard Security Audit Report")
        sb.appendLine()
        sb.appendLine("**Generated:** ${dateFormat.format(Date())}")
        sb.appendLine()

        // 摘要
        val totalRisks = allRisks.values.sumOf { it.size }
        val highCount = allRisks.values.flatten().count { it.result.severity == com.xguard.plugin.model.RiskSeverity.HIGH }
        val mediumCount = allRisks.values.flatten().count { it.result.severity == com.xguard.plugin.model.RiskSeverity.MEDIUM }
        val lowCount = allRisks.values.flatten().count { it.result.severity == com.xguard.plugin.model.RiskSeverity.LOW }

        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("| Metric | Count |")
        sb.appendLine("|--------|-------|")
        sb.appendLine("| Total Risks | $totalRisks |")
        sb.appendLine("| High | $highCount |")
        sb.appendLine("| Medium | $mediumCount |")
        sb.appendLine("| Low | $lowCount |")
        sb.appendLine()

        // 详细结果
        sb.appendLine("## Detailed Results")
        sb.appendLine()

        for ((filePath, risks) in allRisks) {
            sb.appendLine("### $filePath")
            sb.appendLine()

            for ((idx, promptRisk) in risks.withIndex()) {
                val result = promptRisk.result
                sb.appendLine("#### Risk ${idx + 1}: ${result.riskTag}")
                sb.appendLine("- **Score:** ${String.format("%.4f", result.riskScore)}")
                sb.appendLine("- **Severity:** ${result.severity}")
                sb.appendLine("- **Prompt:** `${promptRisk.prompt.promptText.take(100)}...`")
                if (result.explanation.isNotEmpty()) {
                    sb.appendLine("- **Explanation:** ${result.explanation}")
                }
                if (result.suggestions.isNotEmpty()) {
                    sb.appendLine("- **Suggestions:**")
                    for (s in result.suggestions) {
                        sb.appendLine("  - ${s.title}: ${s.description}")
                    }
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
