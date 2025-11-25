package com.github.e6l1ps.cxboxpluginatlassian.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.net.URLEncoder

@Service(Service.Level.PROJECT)
class ConfluenceService(project: Project) {

    private var baseUrl: String = ""
    private var email: String = ""  // Для Bearer token используем email
    private var apiToken: String = ""
    private var spaceKey: String = ""
    private var parentPageId: String? = null

    fun configure(url: String, email: String, apiToken: String, spaceKey: String, parentPageId: String? = null) {
        this.baseUrl = url.removeSuffix("/")
        this.email = email
        this.apiToken = apiToken
        this.spaceKey = spaceKey
        this.parentPageId = parentPageId
    }

    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank() &&
                email.isNotBlank() &&
                apiToken.isNotBlank() &&
                spaceKey.isNotBlank()
    }

    fun testConnection(): Boolean {
        return try {
            // Тестируем аутентификацию через запрос текущего пользователя
            val response = makeGetRequest("/rest/api/user/current")
            if (response != null && response.contains("\"known\"")) {
                thisLogger().info("Confluence connection successful")
                true
            } else {
                thisLogger().warn("Confluence connection test failed - invalid response")
                false
            }
        } catch (e: Exception) {
            thisLogger().warn("Confluence connection test failed", e)
            false
        }
    }

    // Остальные методы остаются без изменений...

    private fun makeGetRequest(endpoint: String): String? {
        return try {
            val url = "$baseUrl$endpoint"

            thisLogger().info("Making GET request to: $url")

            HttpRequests.request(url)
                .tuner { conn ->
                    // Bearer Token аутентификация для Confluence Cloud
                    conn.setRequestProperty("Authorization", "Bearer $apiToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Content-Type", "application/json")
                    // Для Cloud Confluence может потребоваться email в заголовках
                    conn.setRequestProperty("X-Atlassian-Token", "no-check")
                }
                .readString()
        } catch (e: Exception) {
            thisLogger().warn("GET request failed: ${e.message}")
            null
        }
    }

    private fun makeDeleteRequest(endpoint: String): String? {
        return try {
            val url = "$baseUrl$endpoint"

            thisLogger().info("Making DELETE request to: $url")

            HttpRequests.delete(url)
                .tuner { conn ->
                    conn.setRequestProperty("Authorization", "Bearer $apiToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Atlassian-Token", "no-check")
                }
                .connect { request ->
                    val response = request.readString()
                    thisLogger().debug("Response: $response")
                    response
                }
        } catch (e: Exception) {
            e.stackTrace
            thisLogger().warn("DELETE request failed: ${e.message}")
            null
        }
    }

    private fun makePostRequest(endpoint: String, body: String): String? {
        return try {
            val url = "$baseUrl$endpoint"

            thisLogger().info("Making POST request to: $url")
            thisLogger().debug("Request body: $body")

            HttpRequests.post(url, "application/json")
                .tuner { conn ->
                    conn.setRequestProperty("Authorization", "Bearer $apiToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Atlassian-Token", "no-check")
                }
                .connect { request ->
                    request.write(body)
                    val response = request.readString()
                    thisLogger().debug("Response: $response")
                    response
                }
        } catch (e: Exception) {
            e.stackTrace
            thisLogger().warn("POST request failed: ${e.message}")


            null
        }
    }

    private fun makePutRequest(endpoint: String, body: String): String? {
        return try {
            val url = "$baseUrl$endpoint"

            thisLogger().info("Making PUT request to: $url")

            HttpRequests.put(url, "application/json")
                .tuner { conn ->
                    conn.setRequestProperty("Authorization", "Bearer $apiToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Atlassian-Token", "no-check")
                }
                .connect { request ->
                    request.write(body)
                    request.readString()
                }
        } catch (e: Exception) {
            thisLogger().warn("PUT request failed: ${e.message}")
            null
        }
    }


    fun createOrUpdatePage(title: String, content: String, parentPageId: String? = null): String? {
        return try {
            // Сначала ищем существующую страницу
            val existingPageId = findPage(title)

            if (existingPageId != null) {
                thisLogger().info("Updating existing page: $title (ID: $existingPageId)")
                if (updatePage(existingPageId, title, content)) {
                    existingPageId // Возвращаем ID обновленной страницы
                } else {
                    null
                }
            } else {
                thisLogger().info("Creating new page: $title (Parent: $parentPageId)")
                createPage(title, content, parentPageId) // Возвращает String? (ID страницы)
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to create/update page: ${e.message}")
            null
        }
    }

    private fun createPage(title: String, content: String, parentPageId: String?): String? {
        val pageJson = buildCreatePageJson(title, content, parentPageId)
        thisLogger().debug("Create page JSON: $pageJson")

        val response = makePostRequest("/rest/api/content", pageJson) ?: return null

        return try {
            val json = JsonParser.parseString(response).asJsonObject
            if (json.has("id")) {
                val pageId = json.get("id").asString
                thisLogger().info("Page created successfully: $pageId")
                pageId // Возвращаем ID созданной страницы
            } else {
                thisLogger().warn("No page ID in response")
                null
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to parse create page response: ${e.message}")
            null
        }
    }

    private fun findPage(title: String): String? {
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val response = makeGetRequest("/rest/api/content?spaceKey=$spaceKey&title=$encodedTitle") ?: return null

        // Парсим JSON ответ чтобы найти ID страницы
        return try {
            val startIndex = response.indexOf("\"id\":\"") + 6
            if (startIndex >= 6) {
                val endIndex = response.indexOf("\"", startIndex)
                response.substring(startIndex, endIndex)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findPagesByTitlePrefix(prefix: String): List<String> {
        return try {//"SCREEN_"
            // Используем CQL для поиска по префиксу
            val cqlQuery = "type = page and space = \"$spaceKey\" and title ~ \"$prefix*\""
            val encodedCql = URLEncoder.encode(cqlQuery)
            val response = makeGetRequest("/rest/api/search?cql=$encodedCql&start=0&limit=500")
//makeGetRequest("/rest/api/search?cql=${URLEncoder.encode("type = page and space = \"$spaceKey\" and title ~ \"SCREEN_*\"")}")
            if (response != null) {
                parsePageIdsFromSearch(response)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to find pages by prefix '$prefix': ${e.message}")
            emptyList()
        }
    }

    private fun deleteByIds(ids: List<String>) {
        ids.forEach { id ->
            makeDeleteRequest("/rest/api/content/$id")
        }
    }

    private fun parsePageIdsFromSearch(jsonResponse: String): List<String> {
        return try {
            val json = JsonParser.parseString(jsonResponse).asJsonObject
            val results = json.getAsJsonArray("results")
            val pageIds = mutableListOf<String>()

            for (element in results) {
                pageIds.add(element.asJsonObject.get("content").asJsonObject.get("id").asString)
            }

            thisLogger().info("Found ${pageIds.size} pages")
            pageIds
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse page IDs: ${e.message}")
            emptyList()
        }
    }



    private fun updatePage(pageId: String, title: String, content: String): Boolean {
        // Получаем текущую версию страницы
        val currentPage = makeGetRequest("/rest/api/content/$pageId") ?: return false
        val currentVersion = parseVersionFromPage(currentPage)

        val updateJson = buildUpdatePageJson(title, content, currentVersion + 1)
        val response = makePutRequest("/rest/api/content/$pageId", updateJson)
        return response != null
    }


    private fun buildCreatePageJson(title: String, htmlContent: String, parentPageId: String?): String {
        val cleanedContent = htmlContent.trimIndent()

        val pageData = mutableMapOf<String, Any>(
            "type" to "page",
            "title" to title,
            "space" to mapOf("key" to "nrbcrm"),
            "body" to mapOf(
                "storage" to mapOf(
                    "value" to cleanedContent,
                    "representation" to "storage"
                )
            )
        )

        if (!parentPageId.isNullOrEmpty()) {
            pageData["ancestors"] = listOf(mapOf("id" to parentPageId))
        }

        return Gson().toJson(pageData)
    }

    private fun buildUpdatePageJson(title: String, content: String, version: Int): String {
        return """
        {
            "version": {"number": $version},
            "title": "${escapeJson(title)}",
            "type": "page",
            "body": {
                "storage": {
                    "value": "${escapeJson(content)}",
                    "representation": "storage"
                }
            }
        }
        """.trimIndent()
    }

    private fun parseVersionFromPage(pageJson: String): Int {
        return try {
            val versionPattern = "\"number\":(\\d+)".toRegex()
            versionPattern.find(pageJson)?.groupValues?.get(1)?.toInt() ?: 1
        } catch (e: Exception) {
            1
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}