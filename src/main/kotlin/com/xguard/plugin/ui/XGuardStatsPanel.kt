package com.xguard.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.xguard.plugin.inspection.RiskDetectionService
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 统计面板
 * 展示项目风险分布、修复进度等指标
 */
class XGuardStatsPanel(private val project: Project) : JBPanel<Nothing>(BorderLayout()) {

    private val totalLabel = JLabel("0")
    private val highLabel = JLabel("0")
    private val mediumLabel = JLabel("0")
    private val lowLabel = JLabel("0")
    private val safeLabel = JLabel("0")

    /** 结果更新监听器，当 RiskDetectionService 有新结果时自动刷新 */
    private val resultListener = java.util.function.Consumer<String> {
        javax.swing.SwingUtilities.invokeLater { refreshData() }
    }

    init {
        setupUI()
        // 注册监听器，检测结果更新时自动刷新统计
        RiskDetectionService.getInstance(project).addResultListener(resultListener)
        refreshData()
    }

    private fun setupUI() {
        val statsPanel = JPanel(GridLayout(5, 2, 10, 5))

        statsPanel.add(JLabel("Total Prompts Scanned:"))
        statsPanel.add(totalLabel)

        statsPanel.add(JLabel("High Risk:"))
        highLabel.foreground = java.awt.Color.RED
        statsPanel.add(highLabel)

        statsPanel.add(JLabel("Medium Risk:"))
        mediumLabel.foreground = java.awt.Color.ORANGE
        statsPanel.add(mediumLabel)

        statsPanel.add(JLabel("Low Risk:"))
        lowLabel.foreground = java.awt.Color.BLUE
        statsPanel.add(lowLabel)

        statsPanel.add(JLabel("Safe:"))
        safeLabel.foreground = java.awt.Color(0, 128, 0)
        statsPanel.add(safeLabel)

        add(statsPanel, BorderLayout.CENTER)
    }

    fun refreshData() {
        val service = RiskDetectionService.getInstance(project)
        val allResults = service.getAllResults()

        var high = 0
        var medium = 0
        var low = 0
        var safe = 0

        for ((_, risks) in allResults) {
            for (promptRisk in risks) {
                when (promptRisk.result.severity) {
                    com.xguard.plugin.model.RiskSeverity.HIGH -> high++
                    com.xguard.plugin.model.RiskSeverity.MEDIUM -> medium++
                    com.xguard.plugin.model.RiskSeverity.LOW -> low++
                    com.xguard.plugin.model.RiskSeverity.NONE -> safe++
                }
            }
        }

        totalLabel.text = (high + medium + low + safe).toString()
        highLabel.text = high.toString()
        mediumLabel.text = medium.toString()
        lowLabel.text = low.toString()
        safeLabel.text = safe.toString()
    }
}
