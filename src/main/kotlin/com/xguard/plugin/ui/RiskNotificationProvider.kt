package com.xguard.plugin.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.xguard.plugin.inspection.RiskDetectionService
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * 编辑器通知提供者
 * 在编辑器顶部显示风险摘要通知
 */
class RiskNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<FileEditor, JComponent?>? {
        val service = RiskDetectionService.getInstance(project)
        val results = service.getResults(file.path)

        if (results.isEmpty()) return null

        val riskyCount = results.count { it.result.isRisky() }
        val failedCount = results.count { it.result.riskTag.startsWith("Error-") }

        // 有风险、有错误、或全部 Safe 都显示通知栏
        return Function { _ ->
            when {
                failedCount > 0 -> JLabel("XGuard: $failedCount inference error(s) in this file. Check if the local Python service is running.").also {
                    it.foreground = java.awt.Color.RED
                }
                riskyCount > 0 -> JLabel("XGuard: $riskyCount risk(s) detected in this file").also {
                    it.foreground = java.awt.Color(204, 0, 0)
                }
                else -> JLabel("XGuard: Scan complete - no risks found").also {
                    it.foreground = java.awt.Color(0, 128, 0)
                }
            }
        }
    }
}
