package com.github.e6l1ps.cxboxpluginatlassian.services

import com.github.e6l1ps.cxboxpluginatlassian.settings.ConfluenceAuthMode
import com.github.e6l1ps.cxboxpluginatlassian.settings.ConfluenceSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.net.URLConnection
import java.net.URLEncoder
import java.util.Base64

@Service(Service.Level.PROJECT)
class ConfluenceService(private val project: Project) {

    private val settings: ConfluenceSettings
        get() = project.service<ConfluenceSettings>()

    private val baseUrl: String
        get() = settings.baseUrl

    private val spaceKey: String
        get() = settings.spaceKey

    // Потокобезопасные счетчики
    private val createdCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val updatedCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val skippedCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val failedCount = java.util.concurrent.atomic.AtomicInteger(0)

    // Геттеры для UI
    fun getCreatedCount(): Int = createdCount.get()
    fun getUpdatedCount(): Int = updatedCount.get()
    fun getSkippedCount(): Int = skippedCount.get()
    fun getFailedCount(): Int = failedCount.get()

    fun getTotalProcessedCount(): Int = createdCount.get() + updatedCount.get() + skippedCount.get() + failedCount.get()

    // Сброс счетчиков
    fun resetCounters() {
        createdCount.set(0)
        updatedCount.set(0)
        skippedCount.set(0)
        failedCount.set(0)
    }

    /** Конфигурация берётся из [ConfluenceSettings], поэтому переживает перезапуск IDE. */
    fun isConfigured(): Boolean = settings.isConfigured()

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



    /**
     * Находит ID страниц по списку заголовков через CQL-запрос title IN (...)
     * ВАЖНО: CQL чувствителен к регистру – заголовки должны совпадать абсолютно точно.
     * @return Map, где ключ – оригинальный заголовок, значение – ID страницы или null (если не найдена).
     */
    fun resolvePageIdsByTitles(titles: List<String>): Map<String, String?> {
        if (titles.isEmpty()) return emptyMap()

        // Экранируем специальные символы в заголовках для CQL: кавычки и бэкслеши.
        // Каждый заголовок оборачивается в двойные кавычки.
        val escapedTitles = titles.map { title ->
            val escaped = title
                .replace("\\", "\\\\")   // Экранируем обратный слэш
                .replace("\"", "\\\"")   // Экранируем кавычки
            "\"$escaped\""
        }

        val cql = "type=page AND space=$spaceKey AND title IN (${escapedTitles.joinToString(", ")})"
        val encodedCql = URLEncoder.encode(cql, "UTF-8")
        // Ограничиваем количество результатов количеством переданных заголовков (макс. 500)
        val limit = titles.size.coerceAtMost(500)
        val response = makeGetRequest("/rest/api/search?cql=$encodedCql&limit=$limit") ?: return titles.associateWith { null }

        return try {
            val json = JsonParser.parseString(response).asJsonObject
            val results = json.getAsJsonArray("results") ?: return titles.associateWith { null }

            // Собираем найденные пары title -> id
            val foundMap = mutableMapOf<String, String>()
            for (element in results) {
                val item = element.asJsonObject
                val content = item.getAsJsonObject("content")
                val id = content.get("id").asString
                val foundTitle = content.get("title").asString
                foundMap[foundTitle] = id
            }

            // Сопоставляем с оригинальным списком: точное совпадение регистра
            titles.associateWith { title -> foundMap[title] }
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse search response for title IN query", e)
            titles.associateWith { null }
        }
    }

    /**
     * Находит URL страниц по списку заголовков через CQL-запрос title IN (...)
     * ВАЖНО: CQL чувствителен к регистру – заголовки должны совпадать абсолютно точно.
     * @param titles список заголовков для поиска
     * @return Map: ключ – оригинальный заголовок, значение – полный URL страницы или null (если не найдена)
     */
    fun resolvePageUrlsByTitles(titles: List<String>): Map<String, String?> {
        if (titles.isEmpty()) return emptyMap()

        // Экранируем специальные символы в заголовках для CQL: кавычки и обратный слэш
        val escapedTitles = titles.map { title ->
            val escaped = title
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            "\"$escaped\""
        }

        val cql = "type=page AND space=$spaceKey AND title IN (${escapedTitles.joinToString(", ")})"
        val encodedCql = URLEncoder.encode(cql, "UTF-8")
        val limit = titles.size.coerceAtMost(500)

        val response = makeGetRequest("/rest/api/search?cql=$encodedCql&limit=$limit")
            ?: return titles.associateWith { null }

        return try {
            val json = JsonParser.parseString(response).asJsonObject
            val results = json.getAsJsonArray("results") ?: return titles.associateWith { null }

            // Собираем пары title -> url
            val foundMap = mutableMapOf<String, String>()
            for (element in results) {
                val item = element.asJsonObject
                val content = item.getAsJsonObject("content")
                val urlPart = item.get("url").asString
                val foundTitle = content.get("title").asString
                // Формируем прямую ссылку на страницу
                val url = "$baseUrl$urlPart"
                foundMap[foundTitle] = url
            }

            // Сопоставляем с оригинальными заголовками (точное совпадение регистра)
            titles.associateWith { title -> foundMap[title] }
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse search response for title IN query", e)
            titles.associateWith { null }
        }
    }

    /**
     * Проставляет заголовки авторизации из сохранённых настроек.
     *
     * По умолчанию — Bearer-токен (`Authorization: Bearer <token>`), как было
     * исторически: это Personal Access Token Confluence Server/Data Center.
     * Basic-аутентификация (`email:apiToken`, Confluence Cloud) включается только
     * явным выбором режима в настройках или через `CXBOX_CONFLUENCE_AUTH=basic`.
     */
    private fun applyAuth(conn: URLConnection) {
        val authorization = when (settings.authMode) {
            ConfluenceAuthMode.BEARER -> "Bearer ${settings.apiToken}"

            ConfluenceAuthMode.BASIC -> {
                val raw = "${settings.email}:${settings.apiToken}"
                "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
            }
        }

        conn.setRequestProperty("Authorization", authorization)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Atlassian-Token", "no-check")
    }

    private fun makeGetRequest(endpoint: String): String? {
        return try {
            val url = "$baseUrl$endpoint"

            thisLogger().info("Making GET request to: $url")

            HttpRequests.request(url)
                .tuner { conn -> applyAuth(conn) }
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
                .tuner { conn -> applyAuth(conn) }
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
                .tuner { conn -> applyAuth(conn) }
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
                .tuner { conn -> applyAuth(conn) }
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
        val pageInfo = findPageWithContent(title)

        if (pageInfo != null) {
            thisLogger().info("Found existing page: $title (ID: ${pageInfo.id})")
            val oldContent = pageInfo.content

            if (oldContent != null && normalizeContent(oldContent) == normalizeContent(content)) {
                thisLogger().info("Content unchanged for page '$title', skipping update")
                skippedCount.incrementAndGet()
                return pageInfo.id
            }

            thisLogger().info("Content changed, updating page: $title")
            return if (updatePage(pageInfo, content)) {
                updatedCount.incrementAndGet()
                pageInfo.id
            } else {
                failedCount.incrementAndGet()
                null
            }
        }

        thisLogger().info("Creating new page: $title")
        val result = createPage(title, content, parentPageId)
        if (result == null) {
            failedCount.incrementAndGet()
        }
        return result
    }

    private fun normalizeContent(html: String): String {
        return html
            .replace(Regex("<tbody>|</tbody>"), "")  // Убираем tbody
            .replace("width: 100.0%", "width: 100%") // Нормализуем проценты
            .replace(Regex(">\\s+<"), "><")           // Убираем пробелы между тегами
            .replace(Regex("\\s+"), " ")              // Схлопываем пробелы
            .replace(Regex("Сгенерировано автоматически \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
                "Сгенерировано автоматически")   // Игнорируем дату
            .replace(Regex("<(strong|b|em|i|u|span)>\\s*</\\1>"), "")
            .replace(Regex("<(hr|br|img|input|meta|link)\\s+/>"), "<$1/>")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&quot;", "\"")
            .replace("&mdash;", "—")
            .replace("&sup2;", "²")
            .replace("&#39;", "'")
            .replace(Regex("\\s+ac:schema-version=\"[^\"]*\""), "")
            .replace(Regex("\\s+ac:macro-id=\"[^\"]*\""), "")
            .trim()
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
                createdCount.incrementAndGet()  // Увеличиваем счетчик созданных
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

    private data class PageInfo(val id: String, val version: Int, val title: String, val content: String?)

    private fun findPageWithContent(title: String): PageInfo? {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val response = makeGetRequest(
            "/rest/api/content?spaceKey=$spaceKey&title=$encodedTitle&expand=body.storage,version"
        ) ?: return null

        return try {
            val json = JsonParser.parseString(response).asJsonObject
            val results = json.getAsJsonArray("results")
            if (results.size() == 0) return null

            if (results.size() > 1) thisLogger().warn("Found more than 1 page in title $title")

            val page = results[0].asJsonObject
            val id = page.get("id").asString
            val body = page.getAsJsonObject("body")
            val storage = body?.getAsJsonObject("storage")
            val content = storage?.get("value")?.asString
            val versionObj = page.getAsJsonObject("version")
            val version = versionObj?.get("number")?.asInt ?: 1

            PageInfo(id, version, title, content)
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse page info", e)
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



    private fun updatePage(pageInfo: PageInfo,  content: String): Boolean {
        val nextVersion = pageInfo.version + 1
        val updateJson = buildUpdatePageJson(pageInfo.title, content, nextVersion)
        val response = makePutRequest("/rest/api/content/${pageInfo.id}", updateJson)
        return response != null
    }


    private fun buildCreatePageJson(title: String, htmlContent: String, parentPageId: String?): String {
        val cleanedContent = htmlContent.trimIndent()

        val pageData = mutableMapOf<String, Any>(
            "type" to "page",
            "title" to title,
            "space" to mapOf("key" to spaceKey),
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