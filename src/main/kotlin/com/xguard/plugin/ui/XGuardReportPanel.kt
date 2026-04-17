package com.xguard.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.xguard.plugin.inspection.RiskDetectionService
import com.xguard.plugin.model.RiskSeverity
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * 风险报告面板
 * 展示当前项目的所有风险检测结果
 */
class XGuardReportPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val tableModel = object : DefaultTableModel(
        arrayOf("File", "Risk Tag", "Score", "Severity", "Explanation"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val table = JBTable(tableModel)

    init {
        setupUI()
    }

    private fun setupUI() {
        table.autoCreateRowSorter = true

        // 设置列宽
        table.columnModel.getColumn(0).preferredWidth = 200
        table.columnModel.getColumn(1).preferredWidth = 250
        table.columnModel.getColumn(2).preferredWidth = 60
        table.columnModel.getColumn(3).preferredWidth = 80
        table.columnModel.getColumn(4).preferredWidth = 400

        // 严重级别渲染器
        table.columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                when (value as? String) {
                    "HIGH" -> foreground = java.awt.Color.RED
                    "MEDIUM" -> foreground = java.awt.Color.ORANGE
                    "LOW" -> foreground = java.awt.Color.BLUE
                }
                return c
            }
        }

        val scrollPane = JBScrollPane(table)
        setContent(scrollPane)

        // 刷新按钮
        val toolbar = JToolBar()
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refreshData() }
        toolbar.add(refreshButton)
        toolbar.add(Box.createHorizontalGlue())
        add(toolbar, BorderLayout.NORTH)
    }

    /**
     * 刷新数据
     */
    fun refreshData() {
        tableModel.rowCount = 0

        val service = RiskDetectionService.getInstance(project)
        val allRisks = service.getAllRiskyResults()

        for ((filePath, risks) in allRisks) {
            for (promptRisk in risks) {
                val result = promptRisk.result
                val fileName = filePath.substringAfterLast('/')
                tableModel.addRow(arrayOf(
                    fileName,
                    result.riskTag,
                    String.format("%.4f", result.riskScore),
                    result.severity.name,
                    result.explanation.take(100)
                ))
            }
        }
    }
}
