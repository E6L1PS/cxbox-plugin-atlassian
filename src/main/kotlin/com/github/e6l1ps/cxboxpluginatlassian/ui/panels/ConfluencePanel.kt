package com.github.e6l1ps.cxboxpluginatlassian.ui.panels

import com.github.e6l1ps.cxboxpluginatlassian.model.MetaFileParser
import com.github.e6l1ps.cxboxpluginatlassian.model.MetaFilesResult
import com.github.e6l1ps.cxboxpluginatlassian.services.ConfluenceService
import com.github.e6l1ps.cxboxpluginatlassian.services.PluginStateService
import com.github.e6l1ps.cxboxpluginatlassian.ui.dialogs.ConfluenceConfigDialog
import com.github.e6l1ps.cxboxpluginatlassian.util.ConfluenceMarkupGenerator
import com.intellij.openapi.application.ReadAction
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
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
    private lateinit var statsLabel: JBLabel
    val panel: JPanel = JPanel(BorderLayout(5, 5))

    private val executor = Executors.newFixedThreadPool(8)

    init {
        panel.border = BorderFactory.createTitledBorder("Step 3: Confluence Integration")
        buildPanel()
        updateStatus()
        updateButtonStates()
    }

    private fun buildPanel() {
        statusLabel = JBLabel()
        statsLabel = JBLabel("")
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

        val statusPanel = JPanel(BorderLayout())
        statusPanel.add(statusLabel, BorderLayout.NORTH)
        statusPanel.add(statsLabel, BorderLayout.CENTER)

        buttons.add(configureConfluenceButton)
        buttons.add(previewWidgetsButton)
        buttons.add(exportToConfluenceButton)

        panel.add(statusPanel, BorderLayout.NORTH)
        panel.add(buttons, BorderLayout.CENTER)
    }

    private fun exportToConfluence() {
        confluenceService.resetCounters()

        ProgressManager.getInstance().run(object : Task.Modal(project, "Exporting CxBox Documentation", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                // Разбор meta и поиск уже опубликованных страниц — внутри фоновой задачи:
                // resolvePageUrlsByTitles делает HTTP-запросы, на EDT они вешали UI.
                indicator.text = "Parsing meta files..."
                val result = parseMetaFiles()

                indicator.text = "Resolving existing pages..."
                val combinedUrlsMap = mutableMapOf<String, String?>()
                result.getTitles().chunked(100).forEach { batch ->
                    combinedUrlsMap.putAll(confluenceService.resolvePageUrlsByTitles(batch))
                }

                // Генерируем документацию
                indicator.text = "Generating hierarchical documentation..."
                val pages = ConfluenceMarkupGenerator.generateHierarchicalDocumentation(result, project, combinedUrlsMap)

                val pageIds = mutableMapOf<String, String>()

                // Шаг 1: Главная страница (последовательно)
                indicator.text = "Creating MAIN page..."
                val mainPageId = createPageWithRetry("CxBox Meta Documentation", pages["MAIN"]!!.first, null)
                if (mainPageId != null) pageIds["MAIN"] = mainPageId
                indicator.fraction = 0.05
                updateIndicatorStats(indicator) // Обновляем статистику

                // Шаг 2: Категории (параллельно)
                indicator.text = "Creating category pages..."
                val categories = listOf(
                    Triple("Screens", pages["CATEGORY_SCREENS"]!!.first, mainPageId),
                    Triple("Views", pages["CATEGORY_VIEWS"]!!.first, mainPageId),
                    Triple("Widgets", pages["CATEGORY_WIDGETS"]!!.first, mainPageId)
                )

                val categoryFutures = categories.map { (title, content, parentId) ->
                    CompletableFuture.supplyAsync({
                        title to createPageWithRetry(title, content, parentId)
                    }, executor)
                }

                CompletableFuture.allOf(*categoryFutures.toTypedArray()).join()

                categoryFutures.forEach { future ->
                    val (title, id) = future.get()
                    id?.let { pageIds["CATEGORY_${title.uppercase()}"] = it }
                }
                indicator.fraction = 0.1
                updateIndicatorStats(indicator)

                val screensCategoryId = categoryFutures[0].get().second
                val viewsCategoryId = categoryFutures[1].get().second
                val widgetsCategoryId = categoryFutures[2].get().second

                // Шаг 3: Screens
                indicator.text = "Creating screen pages..."
                createPagesParallel(
                    result.screens.map { "Screen: ${it.name}" to pages["SCREEN_${it.name}"]!!.first },
                    screensCategoryId, indicator, 0.1, 0.4, "Screen", pageIds
                )

                // Шаг 4: Views
                indicator.text = "Creating view pages..."
                createPagesParallel(
                    result.views.map { "View: ${it.name}" to pages["VIEW_${it.name}"]!!.first },
                    viewsCategoryId, indicator, 0.4, 0.7, "View", pageIds
                )

                // Шаг 5: Widgets
                indicator.text = "Creating widget pages..."
                createPagesParallel(
                    result.widgets.map { "Widget: ${it.name}" to pages["WIDGET_${it.name}"]!!.first },
                    widgetsCategoryId, indicator, 0.7, 1.0, "Widget", pageIds
                )

                indicator.text = "Export complete! Created ${pageIds.size} pages."
                indicator.fraction = 1.0
                updateIndicatorStats(indicator)
                updateStatsLabel()
            }
        })
    }

    /** Обновляет текст второй строки индикатора со статистикой */
    private fun updateIndicatorStats(indicator: ProgressIndicator) {
        val created = confluenceService.getCreatedCount()
        val updated = confluenceService.getUpdatedCount()
        val skipped = confluenceService.getSkippedCount()
        val failed = confluenceService.getFailedCount()
        indicator.text2 = "✅ Created: $created | 📝 Updated: $updated | ⏭️ Skipped: $skipped | ❌ Failed: $failed"
    }

    /** Параллельное создание страниц с контролем прогресса */
    private fun createPagesParallel(
        items: List<Pair<String, String>>,
        parentId: String?,
        indicator: ProgressIndicator,
        startFraction: Double,
        endFraction: Double,
        stepName: String,
        pageIds: MutableMap<String, String>
    ) {
        val total = items.size
        if (total == 0) return

        val chunkSize = when {
            total > 50 -> 15
            total > 20 -> 10
            else -> 5
        }
        val chunks = items.chunked(chunkSize)

        chunks.forEachIndexed { chunkIndex, chunk ->
            indicator.checkCanceled()

            val chunkFutures = chunk.map { (title, content) ->
                CompletableFuture.supplyAsync({
                    title to createPageWithRetry(title, content, parentId)
                }, executor)
            }

            CompletableFuture.allOf(*chunkFutures.toTypedArray()).join()

            chunkFutures.forEach { future ->
                val (title, pageId) = future.get()
                pageId?.let { pageIds[title] = it }
            }

            val processedItems = (chunkIndex + 1) * chunkSize.coerceAtMost(chunk.size)
            val fraction = startFraction + (processedItems.toDouble() / total) * (endFraction - startFraction)
            indicator.fraction = fraction.coerceIn(0.0, 1.0)

            // Обновляем прогресс и статистику вместе
            indicator.text = "$stepName progress: $processedItems/$total"
            updateIndicatorStats(indicator) // Добавляем статистику во вторую строку

            Thread.sleep(100)
        }
    }

    private fun updateStatsLabel() {
        val created = confluenceService.getCreatedCount()
        val updated = confluenceService.getUpdatedCount()
        val skipped = confluenceService.getSkippedCount()
        val failed = confluenceService.getFailedCount()
        val total = confluenceService.getTotalProcessedCount()

        statsLabel.text = """
            <html>
            <div style='padding: 5px; margin-top: 5px; background: #f8f9fa; border-radius: 3px;'>
                <b>Last export statistics:</b><br>
                ✅ Created: <b>$created</b> | 
                📝 Updated: <b>$updated</b> | 
                ⏭️ Skipped: <b>$skipped</b> |
                ❌ Failed: <b>$failed</b> | 
                📊 Total: <b>$total</b>
            </div>
            </html>
        """.trimIndent()

        javax.swing.SwingUtilities.invokeLater {
            statsLabel.repaint()
            panel.revalidate()
        }
    }

    private fun parseMetaFiles(): MetaFilesResult {
        return ReadAction.compute<MetaFilesResult, RuntimeException> {
            val parser = MetaFileParser()
            parser.parseFiles(projectService.foundJsonFiles)
        }
    }

    /**
     * Создаёт страницу с повтором при неудаче.
     *
     * [ConfluenceService.createOrUpdatePage] не бросает исключение, а возвращает `null`
     * (внутри все ошибки HTTP логируются и гасятся), поэтому раньше повтор не срабатывал
     * никогда — исключение просто не долетало. Повтор идемпотентен: страница ищется по
     * заголовку, поэтому уже созданная не будет продублирована.
     */
    private fun createPageWithRetry(title: String, content: String, parentPageId: String?): String? {
        var lastFailure: String? = null

        repeat(PAGE_CREATE_ATTEMPTS) { attempt ->
            try {
                val pageId = confluenceService.createOrUpdatePage(title, content, parentPageId)
                if (pageId != null) return pageId
                lastFailure = "Confluence не вернул id страницы"
            } catch (e: Exception) {
                lastFailure = e.message
            }

            if (attempt < PAGE_CREATE_ATTEMPTS - 1) {
                Thread.sleep(PAGE_CREATE_RETRY_DELAY_MS)
            }
        }

        thisLogger().error("Failed to create page '$title' after $PAGE_CREATE_ATTEMPTS attempts: $lastFailure")
        return null
    }

    fun updateStatus() {
        val configured = confluenceService.isConfigured()
        statusLabel.text = if (configured) "✓ Confluence: Configured" else "✗ Confluence: Not configured"
        statusLabel.foreground = if (configured) JBColor.GREEN else JBColor.RED
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
            updateStatus()
        }
        dialog.show()
    }

    fun updateButtons() {
        updateButtonStates()
    }

    fun dispose() {
        executor.shutdown()
    }

    private companion object {
        /** Сколько всего попыток создать страницу (1 попытка + повторы). */
        const val PAGE_CREATE_ATTEMPTS = 2

        /** Пауза перед повтором создания страницы. */
        const val PAGE_CREATE_RETRY_DELAY_MS = 1000L
    }
}