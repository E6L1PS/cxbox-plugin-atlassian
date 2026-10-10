package com.github.e6l1ps.cxboxpluginatlassian.model


sealed class MetaFile {
    abstract val fileName: String
    abstract val name: String
    abstract val title: String
}

data class ScreenFile(
    override val fileName: String,
    override val name: String,
    override val title: String,
    val icon: String?,
    val order: Int?,
    val primaryViewName: String?,
    val primaryViews: List<String>,
    val navigation: Navigation?
) : MetaFile()

data class ViewFile(
    override val fileName: String,
    override val name: String,
    override val title: String,
    val template: String?,
    val url: String?,
    val widgets: List<ViewWidget>,
    val rolesAllowed: List<String>
) : MetaFile()

data class WidgetFile(
    override val fileName: String,
    override val name: String,
    override val title: String,
    val type: String,
    val bc: String,
    val fields: List<WidgetField>,
    val options: Map<String, Any>?,
    val actionGroups: ActionGroups? = null,
    val showCondition: ShowCondition? = null
) : MetaFile()

data class ShowCondition(
    val bcName: String,
    val key: String,
    val sequence: Int,
    val params: ConditionParams
)

data class ConditionParams(
    val fieldKey: String,
    val value: Any
)

data class ActionGroups(
    val include: List<String>
)

data class ButtonInfo(
    val actionName: String,
    val buttonText: String = "",
    val scope: ActionScope,
    val availableMethod: String? = null,
    val invokerMethod: String? = null,
    val preAction: String? = null,
    val autoSaveBefore: Boolean = true,
    val iconCode: String? = null
)

//TODO add description
enum class ActionScope(val description: String) {
    BC(""),
    RECORD(""),
    MASS("Массовое действие, применяемое ко всем выделенным строкам в сетке");
}

data class Navigation(
    val menu: List<MenuItem>
)

data class MenuItem(
    val title: String? = null,
    val viewName: String? = null,
    val child: List<MenuItemChild> = emptyList()
)

sealed class MenuItemChild {
    data class ViewItem(val viewName: String) : MenuItemChild()
    data class SubMenu(val title: String, val child: List<MenuItemChild>) : MenuItemChild()
}

data class ViewWidget(
    val widgetName: String,
    val descriptionTitle: String?,
    val position: Int,
    val gridWidth: Int
)

data class WidgetField(
    val key: String,
    val type: String,
    val title: String,
    val drillDown: Boolean = false,
    val width: Int? = null,
    val mode: String? = null,
    val bgColorKey: String? = null,
    val hintKey: String? = null,
    val options: Map<String, Any>? = null
)