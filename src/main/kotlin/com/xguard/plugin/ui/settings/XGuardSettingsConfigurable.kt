package com.xguard.plugin.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.xguard.plugin.model.InferenceMode
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

/**
 * XGuard 设置页面
 */
class XGuardSettingsConfigurable : Configurable {

    private val settings = XGuardSettings.getInstance()

    private val inferenceModeCombo = ComboBox(DefaultComboBoxModel(InferenceMode.entries.toTypedArray()))
    private val cloudEndpointField = JBTextField()
    private val cloudApiKeyField = JBPasswordField()
    private val localModelPathField = JBTextField()
    private val pythonPathField = JBTextField()
    private val sensitivitySlider = javax.swing.JSlider(0, 100, 50)
    private val enableRealTimeCheckBox = javax.swing.JCheckBox("Enable real-time detection", true)
    private val enableReasoningCheckBox = javax.swing.JCheckBox("Enable reasoning (explanation)", false)
    private val inferenceTimeoutField = JBTextField()

    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "XGuard"

    override fun createComponent(): JPanel {
        mainPanel = panel {
            titledRow("Inference Settings") {
                row("Inference Mode:") { inferenceModeCombo() }
                row("Cloud API Endpoint:") { cloudEndpointField() }
                row("Cloud API Key:") { cloudApiKeyField() }
                row("Local Model Path:") { localModelPathField() }
                row("Python Interpreter:") { pythonPathField() }
                row("Inference Timeout (sec):") { inferenceTimeoutField() }
            }
            titledRow("Detection Settings") {
                row("Sensitivity:") { sensitivitySlider() }
                row("") { enableRealTimeCheckBox() }
                row("") { enableReasoningCheckBox() }
            }
        }
        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        return inferenceModeCombo.selectedItem != settings.inferenceMode ||
                cloudEndpointField.text != settings.cloudEndpoint ||
                String(cloudApiKeyField.password) != settings.cloudApiKey ||
                localModelPathField.text != settings.localModelPath ||
                pythonPathField.text != settings.pythonPath ||
                sensitivitySlider.value != (settings.sensitivity * 100).toInt() ||
                enableRealTimeCheckBox.isSelected != settings.enableRealTimeDetection ||
                enableReasoningCheckBox.isSelected != settings.enableReasoning ||
                inferenceTimeoutField.text.toIntOrNull() != settings.inferenceTimeoutSec
    }

    override fun apply() {
        settings.inferenceMode = inferenceModeCombo.selectedItem as InferenceMode
        settings.cloudEndpoint = cloudEndpointField.text
        settings.cloudApiKey = String(cloudApiKeyField.password)
        settings.localModelPath = localModelPathField.text
        settings.pythonPath = pythonPathField.text
        settings.sensitivity = sensitivitySlider.value / 100f
        settings.enableRealTimeDetection = enableRealTimeCheckBox.isSelected
        settings.enableReasoning = enableReasoningCheckBox.isSelected
        settings.inferenceTimeoutSec = inferenceTimeoutField.text.toIntOrNull() ?: 300
    }

    override fun reset() {
        inferenceModeCombo.selectedItem = settings.inferenceMode
        cloudEndpointField.text = settings.cloudEndpoint
        cloudApiKeyField.text = settings.cloudApiKey
        localModelPathField.text = settings.localModelPath
        pythonPathField.text = settings.pythonPath
        sensitivitySlider.value = (settings.sensitivity * 100).toInt()
        enableRealTimeCheckBox.isSelected = settings.enableRealTimeDetection
        enableReasoningCheckBox.isSelected = settings.enableReasoning
        inferenceTimeoutField.text = settings.inferenceTimeoutSec.toString()
    }
}
