package com.github.e6l1ps.cxboxpluginatlassian.ui.panels

import com.github.e6l1ps.cxboxpluginatlassian.services.PluginStateService
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class ModuleSelectionPanel(
    private val project: Project,
    private val service: PluginStateService,
    private val onModuleChanged: () -> Unit
) {

    val panel: JPanel = JPanel(BorderLayout(5, 5))
    private lateinit var comboBox: ComboBox<String>

    init {
        panel.border = BorderFactory.createTitledBorder("Step 1: Select Module")
        buildPanel()
    }

    private fun buildPanel() {
        val modules = ModuleManager.getInstance(project).modules.toList()
        val moduleNames = modules.map { it.name }.toTypedArray()

        comboBox = ComboBox(moduleNames).apply {
            isEnabled = modules.isNotEmpty()
            if (modules.isNotEmpty()) {
                selectedIndex = 0
                service.selectedModule = selectedItem?.toString()
            }
        }

        val infoLabel = JBLabel().apply {
            text = if (modules.isEmpty()) "No modules found" else "Selected: ${moduleNames.firstOrNull()}"
            foreground = if (modules.isEmpty()) JBColor.RED else JBColor.GRAY
        }

        comboBox.addActionListener {
            val selected = comboBox.selectedItem?.toString()
            service.selectedModule = selected
            infoLabel.text = "Selected: $selected"
            onModuleChanged()
        }

        panel.add(JBLabel("Module:"), BorderLayout.WEST)
        panel.add(comboBox, BorderLayout.CENTER)
        panel.add(infoLabel, BorderLayout.SOUTH)
    }
}