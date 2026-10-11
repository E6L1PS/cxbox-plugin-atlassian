package com.github.e6l1ps.cxboxpluginatlassian.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile

class MetaFileParser {

    fun parseFiles(files: List<VirtualFile>): MetaFilesResult {
        val screens = mutableListOf<ScreenFile>()
        val views = mutableListOf<ViewFile>()
        val widgets = mutableListOf<WidgetFile>()

        files.forEach { file ->
            try {
                when {
                    file.name.endsWith(".screen.json") -> parseScreen(file)?.let { screens.add(it) }
                    file.name.endsWith(".view.json") -> parseView(file)?.let { views.add(it) }
                    file.name.endsWith(".widget.json") -> parseWidget(file)?.let { widgets.add(it) }
                }
            } catch (e: Exception) {
                thisLogger().warn("Failed to parse ${file.name}: ${e.message}")
            }
        }

        return MetaFilesResult(screens, views, widgets)
    }

    private fun parseScreen(file: VirtualFile): ScreenFile? {
        val content = String(file.contentsToByteArray())
        val json = JsonParser.parseString(content).asJsonObject

        return ScreenFile(
            fileName = file.name,
            name = json.optString("name")!!,
            title = json.optString("title")!!,
            icon = json.optString("icon"),
            order = json.optInt("order"),
            primaryViewName = json.optString("primaryViewName"),
            primaryViews = json.optJsonArray("primaryViews")?.mapToStringList() ?: emptyList(),
            navigation = parseNavigation(json.optJsonObject("navigation"))
        )
    }

    private fun parseView(file: VirtualFile): ViewFile? {
        val content = String(file.contentsToByteArray())
        val json = JsonParser.parseString(content).asJsonObject

        return ViewFile(
            fileName = file.name,
            name = json.optString("name")!!,
            title = json.optString("title") ?: "-",
            template = json.optString("template"),
            url = json.optString("url"),
            widgets = parseViewWidgets(json.optJsonArray("widgets")),
            rolesAllowed = json.optJsonArray("rolesAllowed")?.mapToStringList() ?: emptyList()
        )
    }

    private fun parseWidget(file: VirtualFile): WidgetFile? {
        val content = String(file.contentsToByteArray())
        val json = JsonParser.parseString(content).asJsonObject

        return WidgetFile(
            fileName = file.name,
            name = json.optString("name")!!,
            title = json.optString("title") ?: "-",
            type = json.optString("type")!!,
            bc = json.optString("bc") ?: "-",
            fields = parseWidgetFields(json.optJsonArray("fields")),
            options = parseOptions(json.optJsonObject("options")),
            actionGroups = parseActionGroups(json.optJsonObject("options")),
            showCondition = parseShowCondition(json.optJsonObject("showCondition"))
        )
    }

    private fun parseShowCondition(json: JsonObject?): ShowCondition? {
        if (json == null) return null

        return try {
            ShowCondition(
                bcName = json.optString("bcName") ?: return null,
                key = json.optString("key") ?: return null,
                sequence = json.optInt("sequence") ?: 1,
                params = parseConditionParams(json.optJsonObject("params")) ?: return null
            )
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse showCondition: ${e.message}")
            null
        }
    }

    private fun parseConditionParams(json: JsonObject?): ConditionParams? {
        if (json == null) return null

        return try {
            ConditionParams(
                fieldKey = json.optString("fieldKey") ?: return null,
                value = json.get("value")?.let {
                    when {
                        it.isJsonPrimitive -> {
                            val primitive = it.asJsonPrimitive
                            when {
                                primitive.isBoolean -> primitive.asBoolean
                                primitive.isNumber -> primitive.asNumber
                                primitive.isString -> primitive.asString
                                else -> null
                            }
                        }
                        else -> null
                    }
                } ?: return null
            )
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse condition params: ${e.message}")
            null
        }
    }

    private fun parseNavigation(json: JsonObject?): Navigation? {
        if (json == null) return null
        return Navigation(
            menu = json.optJsonArray("menu")?.mapToMenuItemList() ?: emptyList()
        )
    }

    private fun parseMenuItem(json: JsonObject): MenuItem {
        // Проверяем, есть ли viewName (простой пункт меню)
        val viewName = json.optString("viewName")

        return if (viewName != null) {
            // Простой пункт меню с viewName
            MenuItem(
                viewName = viewName,
                child = emptyList()
            )
        } else {
            // Сложный пункт меню с title и child
            MenuItem(
                title = json.optString("title") ?: "",
                child = json.optJsonArray("child")?.mapToMenuItemChildList() ?: emptyList()
            )
        }
    }


    private fun parseMenuItemChild(json: JsonObject): MenuItemChild {
        val viewName = json.optString("viewName")

        return if (viewName != null) {
            MenuItemChild.ViewItem(viewName)
        } else {
            MenuItemChild.SubMenu(
                title = json.optString("title") ?: "",
                child = json.optJsonArray("child")?.mapToMenuItemChildList() ?: emptyList()
            )
        }
    }
    private fun parseViewWidgets(jsonArray: JsonArray?): List<ViewWidget> {
        return jsonArray?.mapToViewWidgetList() ?: emptyList()
    }

    private fun parseWidgetFields(jsonArray: JsonArray?): List<WidgetField> {
        return jsonArray?.mapToWidgetFieldList() ?: emptyList()
    }

    private fun parseOptions(json: JsonObject?): Map<String, Any>? {
        if (json == null) return null

        val options = mutableMapOf<String, Any>()
        json.entrySet().forEach { (key, value) ->
            when {
                value.isJsonPrimitive -> {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isBoolean -> options[key] = primitive.asBoolean
                        primitive.isNumber -> options[key] = primitive.asNumber
                        primitive.isString -> options[key] = primitive.asString
                        else -> options[key] = primitive.toString()
                    }
                }

                else -> {
                    // Для массивов и объектов сохраняем как отформатированную JSON строку
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    options[key] = gson.toJson(value)
                }
            }
        }
        return if (options.isEmpty()) null else options
    }

    private fun parseActionGroups(options: JsonObject?): ActionGroups? {
        if (options == null) return null

        val actionGroupsJson = options.optJsonObject("actionGroups")
        if (actionGroupsJson == null || !actionGroupsJson.has("include")) return null

        val includeArray = actionGroupsJson.optJsonArray("include")
        if (includeArray == null || includeArray.size() == 0) return null

        return ActionGroups(
            include = includeArray.mapToStringList()
        )
    }

    // Extension functions для безопасного маппинга
    private fun JsonArray?.mapToStringList(): List<String> {
        return this?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()
    }

    private fun JsonArray?.mapToMenuItemList(): List<MenuItem> {
        return this?.map {
            val obj = it.asJsonObject
            parseMenuItem(obj)
        } ?: emptyList()
    }

    private fun JsonArray?.mapToMenuItemChildList(): List<MenuItemChild> {
        return this?.map {
            val obj = it.asJsonObject
            parseMenuItemChild(obj)
        } ?: emptyList()
    }

    private fun JsonArray?.mapToViewWidgetList(): List<ViewWidget> {
        return this?.mapNotNull { element ->
            val obj = element as? JsonObject
            val name = obj?.optString("widgetName")
            val position = obj?.optInt("position")
            val gridWidth = obj?.optInt("gridWidth")
            if (obj == null || name == null || position == null || gridWidth == null) {
                // Пропускаем только повреждённую запись, а не весь файл view
                thisLogger().warn("Пропущен элемент widgets: нет widgetName/position/gridWidth в $element")
                return@mapNotNull null
            }
            ViewWidget(
                widgetName = name,
                descriptionTitle = obj.optString("descriptionTitle"),
                position = position,
                gridWidth = gridWidth
            )
        } ?: emptyList()
    }

    private fun JsonArray?.mapToWidgetFieldList(): List<WidgetField> {
        return this?.mapNotNull { element ->
            val obj = element as? JsonObject
            val fieldKey = obj?.optString("key")
            val fieldType = obj?.optString("type")
            if (obj == null || fieldKey == null || fieldType == null) {
                // Пропускаем только повреждённое поле, а не весь файл widget
                thisLogger().warn("Пропущено поле виджета: нет key/type в $element")
                return@mapNotNull null
            }
            WidgetField(
                key = fieldKey,
                type = fieldType,
                title = obj.optString("label") ?: obj.optString("title") ?: "",
                drillDown = obj.optBoolean("drillDown") ?: false,
                width = obj.optInt("width"),
                mode = obj.optString("mode"),
                bgColorKey = obj.optString("bgColorKey"),
                hintKey = obj.optString("hintKey"),
                options = parseOptions(obj)
            )
        } ?: emptyList()
    }

    // Вспомогательные extension functions для безопасного доступа к JSON.
    // Префикс opt — намеренный: у gson есть члены JsonObject.getAsJsonArray(String) и
    // getAsJsonObject(String), а член всегда побеждает extension с той же сигнатурой.
    // Одноимённые extension-функции были мёртвым кодом: вызов уходил в gson, который
    // делает простой cast и падает с ClassCastException, если значение другого типа.
    private fun JsonObject.optString(key: String): String? {
        return if (has(key) && get(key).isJsonPrimitive) get(key).asString else null
    }

    private fun JsonObject.optInt(key: String): Int? {
        return if (has(key) && get(key).isJsonPrimitive && get(key).asJsonPrimitive.isNumber)
            get(key).asInt
        else null
    }

    private fun JsonObject.optBoolean(key: String): Boolean? {
        return if (has(key) && get(key).isJsonPrimitive && get(key).asJsonPrimitive.isBoolean)
            get(key).asBoolean
        else null
    }

    private fun JsonObject.optJsonArray(key: String): JsonArray? {
        return if (has(key) && get(key).isJsonArray) get(key).asJsonArray else null
    }

    private fun JsonObject.optJsonObject(key: String): JsonObject? {
        return if (has(key) && get(key).isJsonObject) get(key).asJsonObject else null
    }
}

data class MetaFilesResult(
    val screens: List<ScreenFile>,
    val views: List<ViewFile>,
    val widgets: List<WidgetFile>
) {
    fun getTitles(): List<String> = screens.map { it.name } + views.map { it.name } + widgets.map { it.name }
}