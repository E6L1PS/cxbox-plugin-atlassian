package com.github.e6l1ps.cxboxpluginatlassian.ui.toolWindow

import com.github.e6l1ps.cxboxpluginatlassian.services.ConfluenceService
import com.github.e6l1ps.cxboxpluginatlassian.services.PluginStateService
import com.github.e6l1ps.cxboxpluginatlassian.ui.panels.ConfluencePanel
import com.github.e6l1ps.cxboxpluginatlassian.ui.panels.ModuleSelectionPanel
import com.github.e6l1ps.cxboxpluginatlassian.ui.panels.ScanMetaFilesPanel
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class MyToolWindow(toolWindow: ToolWindow) {

    private val project = toolWindow.project
    private val projectService = project.service<PluginStateService>()
    private val confluenceService = project.service<ConfluenceService>()

    private lateinit var scanMetaFilesPanel: ScanMetaFilesPanel
    private lateinit var confluencePanel: ConfluencePanel
    private lateinit var modulePanel: ModuleSelectionPanel

    fun getContent(): JComponent {
        modulePanel = ModuleSelectionPanel(project, projectService) { updateButtons() }
        scanMetaFilesPanel = ScanMetaFilesPanel(project, projectService)
        confluencePanel = ConfluencePanel(project, confluenceService) { updateButtons() }

        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            val titleLabel = JBLabel("JSON to DTO Generator").apply {
                font = font.deriveFont(Font.BOLD, 16f)
                border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
            }

            add(titleLabel, BorderLayout.NORTH)

            val mainPanel = JPanel(BorderLayout(10, 10)).apply {
                preferredSize = Dimension(800, 600)

                add(modulePanel.panel, BorderLayout.NORTH)
                add(scanMetaFilesPanel.panel, BorderLayout.CENTER)
                add(confluencePanel.panel, BorderLayout.SOUTH)
            }

            val scrollPane = JBScrollPane(mainPanel)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    private fun updateButtons() {
        if (::scanMetaFilesPanel.isInitialized) {
            scanMetaFilesPanel.updateButtonStates()
        }
        if (::confluencePanel.isInitialized) {
            confluencePanel.updateStatus()
        }
    }
}