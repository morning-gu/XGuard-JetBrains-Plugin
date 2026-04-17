package com.xguard.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.xguard.plugin.strategy.StrategyManager
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.File
import javax.swing.*

/**
 * 策略配置面板
 * 展示和编辑当前策略配置
 */
class XGuardPolicyPanel(private val project: Project) : JBPanel<Nothing>(BorderLayout()) {

    private val policyTextArea = JTextArea(20, 60)
    private val statusLabel = JLabel("Ready")

    init {
        setupUI()
        loadCurrentPolicy()
    }

    private fun setupUI() {
        policyTextArea.isEditable = true

        val scrollPane = JScrollPane(policyTextArea)
        add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel()
        val loadButton = JButton("Load from File")
        val saveButton = JButton("Apply Policy")
        val exportButton = JButton("Export to File")

        loadButton.addActionListener { loadPolicyFromFile() }
        saveButton.addActionListener { applyPolicy() }
        exportButton.addActionListener { exportPolicyToFile() }

        buttonPanel.add(loadButton)
        buttonPanel.add(saveButton)
        buttonPanel.add(exportButton)
        buttonPanel.add(Box.createHorizontalStrut(20))
        buttonPanel.add(statusLabel)

        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun loadCurrentPolicy() {
        val strategyManager = StrategyManager.getInstance()
        val yaml = strategyManager.exportPolicyToYaml()
        policyTextArea.text = yaml
    }

    private fun loadPolicyFromFile() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "XGuard Policy Files", "yaml", "yml"
        )
        fileChooser.currentDirectory = File(project.basePath ?: ".")

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                val strategyManager = StrategyManager.getInstance()
                val policy = strategyManager.loadPolicyFromFile(file)
                policyTextArea.text = strategyManager.exportPolicyToYaml(policy)
                statusLabel.text = "Policy loaded from: ${file.name}"
            } catch (e: Exception) {
                statusLabel.text = "Error loading policy: ${e.message}"
            }
        }
    }

    private fun applyPolicy() {
        try {
            val strategyManager = StrategyManager.getInstance()
            strategyManager.loadPolicyFromContent(policyTextArea.text)
            statusLabel.text = "Policy applied successfully"
        } catch (e: Exception) {
            statusLabel.text = "Error applying policy: ${e.message}"
        }
    }

    private fun exportPolicyToFile() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "XGuard Policy Files", "yaml", "yml"
        )
        fileChooser.currentDirectory = File(project.basePath ?: ".")

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            file.writeText(policyTextArea.text)
            statusLabel.text = "Policy exported to: ${file.name}"
        }
    }
}
