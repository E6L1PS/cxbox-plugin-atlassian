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
            name = json.getAsString("name")!!,
            title = json.getAsString("title")!!,
            icon = json.getAsString("icon"),
            order = json.getAsInt("order"),
            primaryViewName = json.getAsString("primaryViewName"),
            primaryViews = json.getAsJsonArray("primaryViews")?.mapToStringList() ?: emptyList(),
            navigation = parseNavigation(json.getAsJsonObject("navigation"))
        )
    }

    private fun parseView(file: VirtualFile): ViewFile? {
        val content = String(file.contentsToByteArray())
        val json = JsonParser.parseString(content).asJsonObject

        return ViewFile(
            fileName = file.name,
            name = json.getAsString("name")!!,
            title = json.getAsString("title") ?: "-",
            template = json.getAsString("template"),
            url = json.getAsString("url"),
            widgets = parseViewWidgets(json.getAsJsonArray("widgets")),
            rolesAllowed = json.getAsJsonArray("rolesAllowed")?.mapToStringList() ?: emptyList()
        )
    }

    private fun parseWidget(file: VirtualFile): WidgetFile? {
        val content = String(file.contentsToByteArray())
        val json = JsonParser.parseString(content).asJsonObject

        return WidgetFile(
            fileName = file.name,
            name = json.getAsString("name")!!,
            title = json.getAsString("title") ?: "-",
            type = json.getAsString("type")!!,
            bc = json.getAsString("bc") ?: "-",
            fields = parseWidgetFields(json.getAsJsonArray("fields")),
            options = parseOptions(json.getAsJsonObject("options")),
            actionGroups = parseActionGroups(json.getAsJsonObject("options")),
            showCondition = parseShowCondition(json.getAsJsonObject("showCondition"))
        )
    }

    private fun parseShowCondition(json: JsonObject?): ShowCondition? {
        if (json == null) return null

        return try {
            ShowCondition(
                bcName = json.getAsString("bcName") ?: return null,
                key = json.getAsString("key") ?: return null,
                sequence = json.getAsInt("sequence") ?: 1,
                params = parseConditionParams(json.getAsJsonObject("params")) ?: return null
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
                fieldKey = json.getAsString("fieldKey") ?: return null,
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
            menu = json.getAsJsonArray("menu")?.mapToMenuItemList() ?: emptyList()
        )
    }

    private fun parseMenuItem(json: JsonObject): MenuItem {
        // Проверяем, есть ли viewName (простой пункт меню)
        val viewName = json.getAsString("viewName")

        return if (viewName != null) {
            // Простой пункт меню с viewName
            MenuItem(
                viewName = viewName,
                child = emptyList()
            )
        } else {
            // Сложный пункт меню с title и child
            MenuItem(
                title = json.getAsString("title") ?: "",
                child = json.getAsJsonArray("child")?.mapToMenuItemChildList() ?: emptyList()
            )
        }
    }


    private fun parseMenuItemChild(json: JsonObject): MenuItemChild {
        val viewName = json.getAsString("viewName")

        return if (viewName != null) {
            MenuItemChild.ViewItem(viewName)
        } else {
            MenuItemChild.SubMenu(
                title = json.getAsString("title") ?: "",
                child = json.getAsJsonArray("child")?.mapToMenuItemChildList() ?: emptyList()
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

        val actionGroupsJson = options.getAsJsonObject("actionGroups")
        if (actionGroupsJson == null || !actionGroupsJson.has("include")) return null

        val includeArray = actionGroupsJson.getAsJsonArray("include")
        if (includeArray == null || includeArray.size() == 0) return null

        return ActionGroups(
            include = includeArray.mapToStringList()
        )
    }

    // Extension functions для безопасного маппинга
    private fun JsonArray?.mapToStringList(): List<String> {
        return this?.map { it.asString } ?: emptyList()
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
        return this?.map {
            val obj = it.asJsonObject
            ViewWidget(
                widgetName = obj.getAsString("widgetName")!!,
                descriptionTitle = obj.getAsString("descriptionTitle"),
                position = obj.getAsInt("position")!!,
                gridWidth = obj.getAsInt("gridWidth")!!
            )
        } ?: emptyList()
    }

    private fun JsonArray?.mapToWidgetFieldList(): List<WidgetField> {
        return this?.map {
            val obj = it.asJsonObject
            WidgetField(
                key = obj.get("key").asString,
                type = obj.get("type").asString,
                title = obj.get("label")?.asString ?: obj.get("title")?.asString ?: "",
                drillDown = obj.get("drillDown")?.asBoolean ?: false,
                width = obj.get("width")?.asInt,
                mode = obj.get("mode")?.asString,
                bgColorKey = obj.get("bgColorKey")?.asString,
                hintKey = obj.get("hintKey")?.asString,
                options = parseOptions(obj)
            )
        } ?: emptyList()
    }

    // Вспомогательные extension functions для безопасного доступа к JSON
    private fun JsonObject.getAsString(key: String): String? {
        return if (has(key) && get(key).isJsonPrimitive) get(key).asString else null
    }

    private fun JsonObject.getAsInt(key: String): Int? {
        return if (has(key) && get(key).isJsonPrimitive && get(key).asJsonPrimitive.isNumber)
            get(key).asInt
        else null
    }

    private fun JsonObject.getAsBoolean(key: String): Boolean? {
        return if (has(key) && get(key).isJsonPrimitive && get(key).asJsonPrimitive.isBoolean)
            get(key).asBoolean
        else null
    }

    private fun JsonObject.getAsJsonArray(key: String): JsonArray? {
        return if (has(key) && get(key).isJsonArray) get(key).asJsonArray else null
    }

    private fun JsonObject.getAsJsonObject(key: String): JsonObject? {
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