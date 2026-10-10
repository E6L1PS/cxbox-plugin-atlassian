package com.github.e6l1ps.cxboxpluginatlassian.ui.dialogs

import com.github.e6l1ps.cxboxpluginatlassian.services.ConfluenceService
import com.github.e6l1ps.cxboxpluginatlassian.settings.ConfluenceAuthMode
import com.github.e6l1ps.cxboxpluginatlassian.settings.ConfluenceSettings
import com.github.e6l1ps.cxboxpluginatlassian.settings.CxboxSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ConfluenceConfigDialog(
    private val project: Project,
    private val onConfigured: () -> Unit
) : DialogWrapper(project) {

    private val settings = project.service<ConfluenceSettings>()
    private val cxboxSettings = project.service<CxboxSettings>()
    private val confluenceService = project.service<ConfluenceService>()

    private lateinit var urlField: JBTextField
    private lateinit var emailField: JBTextField
    private lateinit var apiTokenField: JBPasswordField
    private lateinit var spaceKeyField: JBTextField
    private lateinit var parentPageIdField: JBTextField
    private lateinit var restControllerField: JBTextField
    private lateinit var restControllerHint: JBLabel
    private lateinit var authModeCombo: ComboBox<ConfluenceAuthMode>
    private lateinit var testButton: JButton
    private lateinit var statusLabel: JBLabel

    init {
        title = "Confluence Configuration"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                insets = JBUI.insets(5)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
            }

            // ===== Секция: подключение к Confluence =====
            add(sectionHeader("Confluence connection"), sectionConstraints(0))

            // URL
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            add(JBLabel("Confluence URL:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            urlField = JBTextField(settings.baseUrl).apply {
                emptyText.setText("https://confluence.example.com")
                toolTipText = "Базовый URL инстанса Confluence, без завершающего слэша"
            }
            add(urlField, gbc)

            // Режим аутентификации
            gbc.gridx = 0
            gbc.gridy = 2
            gbc.weightx = 0.0
            add(JBLabel("Authentication:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            authModeCombo = ComboBox(ConfluenceAuthMode.entries.toTypedArray()).apply {
                selectedItem = settings.authMode
                toolTipText = "Bearer (Personal Access Token, Server/DC) или Basic (API-токен Cloud)"
                addActionListener { updateEmailFieldState() }
            }
            add(authModeCombo, gbc)

            // Email
            gbc.gridx = 0
            gbc.gridy = 3
            gbc.weightx = 0.0
            add(JBLabel("Email:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            emailField = JBTextField(settings.email).apply {
                emptyText.setText("user@example.com")
                toolTipText = "Только для Cloud (режим Basic): используется как email:token"
            }
            add(emailField, gbc)

            // API Token
            gbc.gridx = 0
            gbc.gridy = 4
            gbc.weightx = 0.0
            add(JBLabel("API Token:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            apiTokenField = JBPasswordField().apply {
                emptyText.setText(
                    if (settings.apiToken.isNotEmpty()) "сохранён — оставьте пустым" else "вставьте токен"
                )
                toolTipText = "Токен хранится в PasswordSafe и не попадает в файлы проекта. " +
                        "Пустое поле = использовать сохранённый токен"
            }
            add(apiTokenField, gbc)

            // Space Key
            gbc.gridx = 0
            gbc.gridy = 5
            gbc.weightx = 0.0
            add(JBLabel("Space Key:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            spaceKeyField = JBTextField(settings.spaceKey).apply {
                emptyText.setText("DEV")
                toolTipText = "Ключ пространства Confluence, чувствителен к регистру"
            }
            add(spaceKeyField, gbc)

            // Parent Page ID
            gbc.gridx = 0
            gbc.gridy = 6
            gbc.weightx = 0.0
            add(JBLabel("Parent Page ID (optional):"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            parentPageIdField = JBTextField(settings.parentPageId).apply {
                emptyText.setText("ID родительской страницы")
                toolTipText = "Необязательно: страница-родитель для новой документации"
            }
            add(parentPageIdField, gbc)

            // ===== Секция: проект CXBox =====
            add(sectionHeader("CXBox project"), sectionConstraints(7))

            // CXBox REST controller — класс с enum бизнес-компонентов
            gbc.gridx = 0
            gbc.gridy = 8
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            add(JBLabel("CXBox REST controller:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            restControllerField = JBTextField(cxboxSettings.restControllerClass).apply {
                emptyText.setText("com.example.app.controller.MyRestController")
                toolTipText = "FQN класса-контроллера CXBox, в котором объявлен enum бизнес-компонентов. " +
                        "По нему строится связь BC -> service -> DTO -> actions. Перекрывается " +
                        "переменной окружения ${CxboxSettings.ENV_REST_CONTROLLER_CLASS}"
            }
            add(restControllerField, gbc)

            // Подсказка по полю контроллера
            gbc.gridx = 0
            gbc.gridy = 9
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            restControllerHint = JBLabel(restControllerHintText()).apply {
                foreground = JBColor.GRAY
            }
            add(restControllerHint, gbc)

            // Status Label
            gbc.gridx = 0
            gbc.gridy = 10
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            statusLabel = JBLabel(initialStatusText()).apply {
                foreground = JBColor.GRAY
            }
            add(statusLabel, gbc)

            // Test Connection Button
            gbc.gridx = 1
            gbc.gridy = 11
            gbc.gridwidth = 1
            gbc.anchor = GridBagConstraints.EAST
            testButton = JButton("Test Connection").apply {
                addActionListener { saveAndTestConnection() }
            }
            add(testButton, gbc)

            updateEmailFieldState()
        }
    }

    /** Жирный заголовок секции диалога. */
    private fun sectionHeader(title: String) = JBLabel(title).apply {
        font = font.deriveFont(Font.BOLD)
    }

    /** Ограничения сетки для заголовка секции: на обе колонки, с отступом сверху. */
    private fun sectionConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        gridwidth = 2
        weightx = 1.0
        insets = JBUI.insets(12, 5, 2, 5)
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
    }

    /** Подсказка под полем контроллера: отдельно предупреждаем, когда значение перекрыто окружением. */
    private fun restControllerHintText(): String =
        if (cxboxSettings.isUsingEnvironment()) {
            "Значение задано переменной ${CxboxSettings.ENV_REST_CONTROLLER_CLASS} — поле перекрыто"
        } else {
            "Необязательно: без класса-контроллера раздел действий в документации будет пустым"
        }

    /** Email нужен только в режиме Basic — в Bearer-режиме поле неактивно. */
    private fun updateEmailFieldState() {
        val basic = selectedAuthMode() == ConfluenceAuthMode.BASIC
        emailField.isEnabled = basic
        if (!basic) {
            emailField.toolTipText = "Не используется: выбран режим Bearer (Personal Access Token)"
        } else {
            emailField.toolTipText = "Обязателен для Cloud: запрос уйдёт как Basic email:token"
        }
    }

    private fun selectedAuthMode(): ConfluenceAuthMode =
        authModeCombo.selectedItem as? ConfluenceAuthMode ?: ConfluenceAuthMode.BEARER

    private fun initialStatusText(): String = when {
        settings.isUsingEnvironment() ->
            "Активны значения из .env / переменных окружения — они имеют приоритет над полями ниже"
        settings.isConfigured() -> "Настройки сохранены. Можно проверить соединение."
        else -> "Заполните параметры подключения к Confluence"
    }

    /**
     * Переносит значения полей в персистентные настройки.
     * @return false, если валидация не прошла (сообщение выводится в statusLabel).
     */
    private fun applyFieldsToSettings(): Boolean {
        val url = urlField.text.trim()
        val email = emailField.text.trim()
        val spaceKey = spaceKeyField.text.trim()
        val token = String(apiTokenField.getPassword()).trim()
        val authMode = selectedAuthMode()
        val restController = restControllerField.text.trim()

        if (url.isEmpty() || spaceKey.isEmpty()) {
            showError("Confluence URL and Space Key are required")
            return false
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showError("Confluence URL must start with http:// or https://")
            return false
        }
        if (token.isEmpty() && settings.apiToken.isEmpty()) {
            showError("API Token is required")
            return false
        }
        if (authMode == ConfluenceAuthMode.BASIC && email.isEmpty()) {
            showError("Email is required for Basic authentication (Confluence Cloud)")
            return false
        }
        if (restController.isNotEmpty() && !FULLY_QUALIFIED_CLASS.matches(restController)) {
            showError(
                "CXBox REST controller must be a fully qualified class name, " +
                    "for example com.example.app.controller.MyRestController"
            )
            return false
        }

        settings.baseUrl = url
        settings.email = email
        settings.spaceKey = spaceKey
        settings.parentPageId = parentPageIdField.text.trim()
        settings.authMode = authMode
        cxboxSettings.restControllerClass = restController
        if (token.isNotEmpty()) {
            settings.apiToken = token
            apiTokenField.text = ""
        }
        return true
    }

    private fun saveAndTestConnection() {
        if (!applyFieldsToSettings()) return

        testButton.isEnabled = false
        testButton.text = "Testing..."
        statusLabel.text = "Проверка соединения..."
        statusLabel.foreground = JBColor.GRAY

        try {
            if (confluenceService.testConnection()) {
                statusLabel.text = "✓ Connection successful!"
                statusLabel.foreground = JBColor.GREEN
            } else {
                statusLabel.text = "✗ Connection failed. Check credentials and space key."
                statusLabel.foreground = JBColor.RED
            }
        } finally {
            testButton.isEnabled = true
            testButton.text = "Test Connection"
        }
    }

    override fun doOKAction() {
        if (!applyFieldsToSettings()) return

        if (confluenceService.testConnection()) {
            statusLabel.text = "✓ Connection successful!"
            statusLabel.foreground = JBColor.GREEN
            super.doOKAction()
            onConfigured()
        } else {
            showError("Не удалось подключиться к Confluence: проверьте URL, токен и space key")
        }
    }

    private fun showError(message: String) {
        statusLabel.text = message
        statusLabel.foreground = JBColor.RED
        Messages.showWarningDialog(project, message, "Confluence Configuration")
    }

    private companion object {
        /** Полное имя класса: `com.example.app.controller.MyRestController`. */
        val FULLY_QUALIFIED_CLASS = Regex("""^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$""")
    }
}
