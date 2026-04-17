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
        val riskyCount = results.count { it.result.isRisky() }

        if (riskyCount == 0) return null

        return Function { _ ->
            JLabel("XGuard: $riskyCount risk(s) detected in this file").also {
                it.foreground = java.awt.Color(204, 0, 0)
            }
        }
    }
}
