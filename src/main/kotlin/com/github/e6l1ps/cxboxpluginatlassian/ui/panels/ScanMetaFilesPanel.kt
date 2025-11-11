package com.github.e6l1ps.cxboxpluginatlassian.ui.panels

import com.github.e6l1ps.cxboxpluginatlassian.services.PluginStateService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ScanMetaFilesPanel(
    private val project: Project,
    private val projectService: PluginStateService
) {

    val panel: JPanel = JPanel(BorderLayout(5, 5))
    private lateinit var textArea: JBTextArea
    private lateinit var startButton: JButton

    init {
        panel.border = BorderFactory.createTitledBorder("Step 2: Scan Meta Files")
        buildPanel()
    }

    private fun buildPanel() {
        textArea = JBTextArea(
            """
            Click 'Start' to begin scanning for JSON widget files...
            The plugin will search in: ./resources/meta/
            """.trimIndent()
        ).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        val scroll = JBScrollPane(textArea).apply {
            preferredSize = Dimension(600, 250)
        }

        startButton = JButton("Start").apply {
            addActionListener {
                val module = projectService.selectedModule
                if (module == null)
                    Messages.showWarningDialog(project, "Please select a module first", "No Module")
                else
                    scanModuleForJsonFiles(module)
            }
            isEnabled = projectService.selectedModule != null
        }

        val startPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(startButton)
        }

        panel.add(scroll, BorderLayout.CENTER)
        panel.add(startPanel, BorderLayout.SOUTH)
    }

    private fun scanModuleForJsonFiles(moduleName: String) {
        try {
            // Используем textArea вместо resultsTextArea
            textArea.text = "Scanning module: $moduleName\n\n"
            appendToResults("Searching for JSON files in resources...")

            val module = ModuleManager.getInstance(project).modules.find { it.name == moduleName }
            if (module == null) {
                appendToResults("ERROR: Module '$moduleName' not found!")
                return
            }

            val moduleRootManager = ModuleRootManager.getInstance(module)
            val sourceRoots = moduleRootManager.sourceRoots

            appendToResults("Found ${sourceRoots.size} source roots:")
            sourceRoots.forEach { root ->
                appendToResults("  - ${root.path} (${root.name})")
            }

            val jsonFiles = mutableListOf<VirtualFile>()

            // Ищем только в ресурсных путях
            for (sourceRoot in sourceRoots) {
                // Нас интересуют только пути с ресурсами
                if (sourceRoot.path.contains("/resources") || sourceRoot.name.equals("resources", ignoreCase = true)) {
                    appendToResults("\nSearching in resources root: ${sourceRoot.path}")

                    // Ищем папку meta внутри resources
                    val metaDir = sourceRoot.findChild("meta")
                    if (metaDir != null && metaDir.isDirectory) {
                        appendToResults("  Found meta directory: ${metaDir.path}")

                        // Рекурсивно ищем JSON файлы в meta
                        val files = findJsonFilesRecursively(metaDir)
                        jsonFiles.addAll(files)
                        appendToResults("  Found ${files.size} JSON files in meta directory")
                    } else {
                        appendToResults("  Meta directory not found in resources")
                    }
                } else {
                    appendToResults("Skipping non-resource root: ${sourceRoot.path}")
                }
            }

            // Сохраняем найденные файлы
            projectService.foundJsonFiles = jsonFiles

            // Обновляем UI
            appendToResults("\n=== SCAN COMPLETE ===")
            appendToResults("Total JSON files found: ${jsonFiles.size}")

            if (jsonFiles.isNotEmpty()) {
                appendToResults("\nFound files:")
                jsonFiles.forEach { file ->
                    appendToResults("  - ${file.name} (${file.path})")
                }

                updateButtonStates()
                Messages.showInfoMessage(
                    project,
                    "Found ${jsonFiles.size} JSON files in module $moduleName",
                    "Scan Complete"
                )
            } else {
                appendToResults("\nNo JSON files found in resources/meta/")
                Messages.showWarningDialog(
                    project,
                    "No JSON files found in resources/meta/ directory",
                    "No Files Found"
                )
            }

        } catch (e: Exception) {
            appendToResults("ERROR during scan: ${e.message}")
            thisLogger().error("Error scanning module", e)
        }
    }

    fun updateButtonStates() {
        startButton.isEnabled = projectService.selectedModule != null
    }

    private fun findJsonFilesRecursively(directory: VirtualFile): List<VirtualFile> {
        val jsonFiles = mutableListOf<VirtualFile>()

        for (child in directory.children) {
            if (child.isDirectory) {
                jsonFiles.addAll(findJsonFilesRecursively(child))
            } else if (child.extension?.equals("json", ignoreCase = true) == true) {
                jsonFiles.add(child)
            }
        }

        return jsonFiles
    }

    private fun appendToResults(text: String) {
        textArea.append("$text\n")
        // Прокручиваем до конца
        textArea.caretPosition = textArea.document.length
    }
}
