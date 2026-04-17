package com.xguard.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * XGuard 工具窗口工厂
 * 创建风险报告、统计和策略配置面板
 */
class XGuardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // 风险报告 Tab
        val reportPanel = XGuardReportPanel(project)
        val reportContent = contentFactory.createContent(
            reportPanel,
            "Risk Report",
            false
        )
        toolWindow.contentManager.addContent(reportContent)

        // 统计 Tab
        val statsPanel = XGuardStatsPanel(project)
        val statsContent = contentFactory.createContent(
            statsPanel,
            "Statistics",
            false
        )
        toolWindow.contentManager.addContent(statsContent)

        // 策略 Tab
        val policyPanel = XGuardPolicyPanel(project)
        val policyContent = contentFactory.createContent(
            policyPanel,
            "Policy",
            false
        )
        toolWindow.contentManager.addContent(policyContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
