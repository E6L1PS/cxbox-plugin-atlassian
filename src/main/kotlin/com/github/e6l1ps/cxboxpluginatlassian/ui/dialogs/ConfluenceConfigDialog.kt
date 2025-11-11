package com.github.e6l1ps.cxboxpluginatlassian.ui.dialogs

import com.github.e6l1ps.cxboxpluginatlassian.services.ConfluenceService
import com.github.e6l1ps.cxboxpluginatlassian.util.ConfluenceMarkupGenerator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ConfluenceConfigDialog(
    private val project: Project,
    private val onConfigured: () -> Unit
) : DialogWrapper(project) {

    private val confluenceService = project.service<ConfluenceService>()
    private lateinit var urlField: JBTextField
    private lateinit var emailField: JBTextField  // Изменили с username на email
    private lateinit var apiTokenField: JBTextField
    private lateinit var spaceKeyField: JBTextField
    private lateinit var parentPageIdField: JBTextField
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

            // URL
            gbc.gridx = 0
            gbc.gridy = 0
            add(JBLabel("Confluence URL:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            urlField = JBTextField().apply {
                text = "https://kb-liga.phoenixit.ru"
                toolTipText = "Base URL of your Confluence Cloud instance"
            }
            add(urlField, gbc)

            // Email (вместо Username)
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.weightx = 0.0
            add(JBLabel("Email:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            emailField = JBTextField().apply {
                text = "dpesternikov@phoenixit.ru"
                toolTipText = "Your email for Confluence Cloud"
            }
            add(emailField, gbc)

            // API Token
            gbc.gridx = 0
            gbc.gridy = 2
            gbc.weightx = 0.0
            add(JBLabel("API Token:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            apiTokenField = JBTextField().apply {
                text = "NTAxMjk4ODg3NjgzOoYVPX2eqUbYxQZ/bJZXg4oYQQYV"
                toolTipText = "Confluence API token (Bearer token)"
            }
            add(apiTokenField, gbc)

            // Space Key
            gbc.gridx = 0
            gbc.gridy = 3
            gbc.weightx = 0.0
            add(JBLabel("Space Key:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            spaceKeyField = JBTextField().apply {
                text = "nrbcrm"
                toolTipText = "Confluence space key (e.g., 'DEV', 'QA')"
            }
            add(spaceKeyField, gbc)

            // Parent Page ID
            gbc.gridx = 0
            gbc.gridy = 4
            gbc.weightx = 0.0
            add(JBLabel("Parent Page ID (optional):"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            parentPageIdField = JBTextField().apply {
                text = "312501480"
                toolTipText = "Optional: ID of parent page for new documentation"
            }
            add(parentPageIdField, gbc)

            // Status Label
            gbc.gridx = 0
            gbc.gridy = 5
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            statusLabel = JBLabel("Please configure Confluence connection").apply {
                foreground = JBColor.GRAY
            }
            add(statusLabel, gbc)

            // Test Connection Button
            gbc.gridx = 1
            gbc.gridy = 6
            gbc.gridwidth = 1
            gbc.anchor = GridBagConstraints.EAST
            testButton = JButton("Test Connection").apply {
                addActionListener {
                    testConnection()
                }
            }
            add(testButton, gbc)
        }
    }

    private fun testConnection() {
        val url = urlField.text.trim()
        val email = emailField.text.trim()
        val apiToken = apiTokenField.text.trim()
        val spaceKey = spaceKeyField.text.trim()
        val parentPageId = parentPageIdField.text.trim().takeIf { it.isNotBlank() }

        if (url.isBlank() || email.isBlank() || apiToken.isBlank() || spaceKey.isBlank()) {
            statusLabel.text = "Please fill all required fields"
            statusLabel.foreground = JBColor.RED
            return
        }

        // Сохраняем конфигурацию
        confluenceService.configure(url, email, apiToken, spaceKey, parentPageId)
        ConfluenceMarkupGenerator.configure(url, spaceKey)

        // Тестируем соединение
        testButton.isEnabled = false
        testButton.text = "Testing..."

        try {
            val success = confluenceService.testConnection()

            if (success) {
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
        if (confluenceService.isConfigured() && confluenceService.testConnection()) {
            super.doOKAction()
            onConfigured()
        } else {
            Messages.showWarningDialog(
                project,
                "Please test connection first and ensure it's successful",
                "Configuration Not Complete"
            )
        }
    }
}