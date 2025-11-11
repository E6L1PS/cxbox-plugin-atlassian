package com.github.e6l1ps.cxboxpluginatlassian.ui.panels

import com.github.e6l1ps.cxboxpluginatlassian.model.MetaFileParser
import com.github.e6l1ps.cxboxpluginatlassian.model.MetaFilesResult
import com.github.e6l1ps.cxboxpluginatlassian.services.ConfluenceService
import com.github.e6l1ps.cxboxpluginatlassian.services.PluginStateService
import com.github.e6l1ps.cxboxpluginatlassian.ui.dialogs.ConfluenceConfigDialog
import com.github.e6l1ps.cxboxpluginatlassian.util.ConfluenceMarkupGenerator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ConfluencePanel(
    private val project: Project,
    private val confluenceService: ConfluenceService,
    private val onStatusChanged: () -> Unit
) {
    private val projectService = project.service<PluginStateService>()
    private lateinit var statusLabel: JBLabel

    private lateinit var configureConfluenceButton: JButton
    private lateinit var previewWidgetsButton: JButton
    private lateinit var exportToConfluenceButton: JButton
    val panel: JPanel = JPanel(BorderLayout(5, 5))

    init {
        panel.border = BorderFactory.createTitledBorder("Step 3: Confluence Integration")
        buildPanel()
        updateStatus()
        updateButtonStates() // Добавляем первоначальное обновление кнопок
    }

    private fun buildPanel() {
        statusLabel = JBLabel()
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT))

        configureConfluenceButton = JButton("Configure").apply {
            addActionListener { showConfluenceConfigDialog() }
        }

        previewWidgetsButton = JButton("Preview Widgets").apply {
            addActionListener { Messages.showInfoMessage(project, "TODO: Preview", "Preview") }
            isEnabled = false
        }

        exportToConfluenceButton = JButton("Export to Confluence").apply {
            addActionListener { exportToConfluence() }
            isEnabled = false
        }

        buttons.add(configureConfluenceButton)
        buttons.add(previewWidgetsButton)
        buttons.add(exportToConfluenceButton)

        panel.add(statusLabel, BorderLayout.NORTH)
        panel.add(buttons, BorderLayout.CENTER)
    }

    private fun exportToConfluence() {
        val result = parseMetaFiles()

        ProgressManager.getInstance().run(object : Task.Modal(project, "Exporting CxBox Documentation", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                // Общий шаг: генерируем страницы
                indicator.text = "Generating hierarchical documentation..."
                val pages = ConfluenceMarkupGenerator.generateHierarchicalDocumentation(result, project)

                val pageIds = mutableMapOf<String, String>()

                // Step 1: MAIN page
                indicator.text = "Creating MAIN page..."
                val mainPageId = createPageWithRetry("CxBox Meta Documentation", pages["MAIN"]!!.first, null)
                if (mainPageId != null) pageIds["MAIN"] = mainPageId
                indicator.fraction = 0.05

                // Step 2: Category pages
                indicator.text = "Creating category pages..."
                val screensCategoryId = createPageWithRetry("Screens", pages["CATEGORY_SCREENS"]!!.first, mainPageId)
                val viewsCategoryId = createPageWithRetry("Views", pages["CATEGORY_VIEWS"]!!.first, mainPageId)
                val widgetsCategoryId = createPageWithRetry("Widgets", pages["CATEGORY_WIDGETS"]!!.first, mainPageId)

                screensCategoryId?.let { pageIds["CATEGORY_SCREENS"] = it }
                viewsCategoryId?.let { pageIds["CATEGORY_VIEWS"] = it }
                widgetsCategoryId?.let { pageIds["CATEGORY_WIDGETS"] = it }
                indicator.fraction = 0.1

                // Step 3: Screens
                indicator.text = "Creating screen pages..."
                createPagesWithGuiProgress(
                    result.screens.map { "Screen: ${it.name}" to pages["SCREEN_${it.name}"]!!.first },
                    screensCategoryId, indicator, 0.1, 0.4, "Screen", pageIds
                )

                // Step 4: Views
                indicator.text = "Creating view pages..."
                createPagesWithGuiProgress(
                    result.views.map { "View: ${it.name}" to pages["VIEW_${it.name}"]!!.first },
                    viewsCategoryId, indicator, 0.4, 0.7, "View", pageIds
                )

                // Step 5: Widgets
                indicator.text = "Creating widget pages..."
                createPagesWithGuiProgress(
                    result.widgets.map { "Widget: ${it.name}" to pages["WIDGET_${it.name}"]!!.first },
                    widgetsCategoryId, indicator, 0.7, 1.0, "Widget", pageIds
                )

                indicator.text = "Export complete! Created ${pageIds.size} pages."
                indicator.fraction = 1.0
            }
        })
    }

    private fun parseMetaFiles(): MetaFilesResult {
        val parser = MetaFileParser()
        return parser.parseFiles(projectService.foundJsonFiles)
    }

    private fun createPageWithRetry(title: String, content: String, parentPageId: String?): String? {
        return try {
            confluenceService.createOrUpdatePage(title, content, parentPageId)
        } catch (e: Exception) {
            thisLogger().warn("Failed to create page '$title', retrying...")
            try {
                // Повторная попытка через 1 секунду
                Thread.sleep(1000)
                confluenceService.createOrUpdatePage(title, content, parentPageId)
            } catch (e2: Exception) {
                thisLogger().error("Failed to create page '$title' after retry: ${e2.message}")
                null
            }
        }
    }

    /** Создание страниц с обновлением ProgressIndicator */
    private fun createPagesWithGuiProgress(
        items: List<Pair<String, String>>,
        parentId: String?,
        indicator: ProgressIndicator,
        startFraction: Double,
        endFraction: Double,
        stepName: String,
        pageIds: MutableMap<String, String>
    ) {
        val total = items.size
        items.forEachIndexed { index, (title, content) ->
            indicator.checkCanceled() // чтобы прервать при отмене пользователем
            val pageId = createPageWithRetry(title, content, parentId)
            if (pageId != null) pageIds[title] = pageId

            val fraction = startFraction + (index + 1).toDouble() / total * (endFraction - startFraction)
            indicator.fraction = fraction
            indicator.text2 = "$stepName progress: ${index + 1}/$total"
        }
    }

    fun updateStatus() {
        val configured = confluenceService.isConfigured()
        statusLabel.text = if (configured) "✓ Confluence: Configured" else "✗ Confluence: Not configured"
        statusLabel.foreground = if (configured) JBColor.GREEN else JBColor.RED

        // Обновляем кнопки при обновлении статуса
        updateButtonStates()
        onStatusChanged()
    }

    private fun updateButtonStates() {
        val hasJsonFiles = projectService.foundJsonFiles.isNotEmpty()
        val isConfluenceConfigured = confluenceService.isConfigured()
        val hasSelectedModule = projectService.selectedModule != null

        configureConfluenceButton.isEnabled = hasSelectedModule
        previewWidgetsButton.isEnabled = hasJsonFiles && isConfluenceConfigured
        exportToConfluenceButton.isEnabled = hasJsonFiles && isConfluenceConfigured

        // Обновляем текст кнопок для лучшей обратной связи
        if (hasSelectedModule) {
            configureConfluenceButton.text = "Configure Confluence"
        } else {
            configureConfluenceButton.text = "Select Module First"
        }

        if (exportToConfluenceButton.isEnabled) {
            exportToConfluenceButton.text = "Export to Confluence (${projectService.foundJsonFiles.size} files)"
        } else {
            exportToConfluenceButton.text = "Export to Confluence"
        }
    }

    private fun showConfluenceConfigDialog() {
        val dialog = ConfluenceConfigDialog(project) {
            // Callback при успешной конфигурации
            updateStatus() // Это вызовет updateButtonStates() внутри updateStatus()
        }
        dialog.show()
    }

    // Публичный метод для обновления кнопок извне
    fun updateButtons() {
        updateButtonStates()
    }
}
