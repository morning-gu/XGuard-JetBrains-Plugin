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

    /** CardLayout 面板，用于在表格和空状态提示之间切换 */
    private lateinit var cardPanel: JPanel
    private lateinit var cardLayout: java.awt.CardLayout

    /** 无结果时的提示标签 */
    private val emptyLabel = JLabel("No scan results yet. Click \"Scan Current File\" to start scanning.").also {
        it.horizontalAlignment = SwingConstants.CENTER
        it.foreground = java.awt.Color.GRAY
    }

    /** 结果更新监听器，当 RiskDetectionService 有新结果时自动刷新（在EDT线程执行） */
    private val resultListener = java.util.function.Consumer<String> {
        javax.swing.SwingUtilities.invokeLater { refreshData() }
    }

    init {
        setupUI()
        // 注册监听器，检测结果更新时自动刷新表格
        RiskDetectionService.getInstance(project).addResultListener(resultListener)
    }

    private fun setupUI() {
        layout = BorderLayout()

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
                    "NONE" -> foreground = java.awt.Color.GRAY
                }
                return c
            }
        }

        val scrollPane = JBScrollPane(table)

        // 使用 CardLayout 在表格和空状态提示之间切换，避免布局覆盖
        cardLayout = java.awt.CardLayout()
        cardPanel = JPanel(cardLayout)
        cardPanel.add(scrollPane, "table")
        cardPanel.add(emptyLabel, "empty")
        cardLayout.show(cardPanel, "empty")

        setContent(cardPanel)

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
        val allResults = service.getAllResults()

        if (allResults.isEmpty()) {
            emptyLabel.text = "No scan results yet. Click \"Scan Current File\" to start scanning."
            cardLayout.show(cardPanel, "empty")
            return
        }

        for ((filePath, risks) in allResults) {
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

        // 切换到表格视图
        cardLayout.show(cardPanel, "table")

        // 如果所有结果都是 Safe，更新空状态提示文本（但不切换显示）
        val riskyCount = allResults.values.sumOf { risks -> risks.count { it.result.isRisky() } }
        if (riskyCount == 0) {
            emptyLabel.text = "Scan complete. No risks found - all prompts are safe."
        }
    }
}
