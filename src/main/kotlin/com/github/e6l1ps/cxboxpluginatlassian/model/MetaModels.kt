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

/**
 * Scope действия CXBox (`ActionScope` из `org.cxbox.core.service.action`) — то, над чем
 * действие выполняется. На UI уходит в `ActionDTO.scope` в нижнем регистре
 * (`ActionDescription.toDto` → `getActionScope().toString().toLowerCase()`).
 */
enum class ActionScope(val description: String) {
    /** Действие бизнес-компонента: без привязки к конкретной записи. Задаётся `scope(ActionScope.BC)`; так объявлены `create` и `associate`. */
    BC("Действие бизнес-компонента: выполняется без привязки к конкретной записи. Так объявлены `create` и `associate`."),

    /**
     * Действие над одной записью — режим по умолчанию
     * (`ActionDescriptionBuilder.actionScope = ActionScope.RECORD`).
     * При объявлении через `action(...)` доступность дополнительно ограничивается
     * проверкой `NOT_NULL_ID`, то есть действие доступно только при выбранной записи.
     */
    RECORD("Действие над одной записью. Режим по умолчанию: доступно, только когда запись выбрана."),

    /**
     * Массовое действие над набором записей. Выставляется автоматически при объявлении
     * через `massInvoker(...)` — тогда инвокер получает набор id — либо явным
     * `scope(ActionScope.MASS)`.
     *
     * В UI это bulk-режим таблицы (доступен в List при непустой таблице): выбор строк,
     * проверка выбранного, подтверждение, результат. Запрос уходит по id первой выбранной
     * записи, а id остальных — в её `massIds`.
     */
    MASS(
        "Массовое действие над набором записей: в таблице включается режим выбора строк, " +
            "запрос уходит по id первой выбранной записи, а id остальных — в её `massIds`. " +
            "Выставляется через `massInvoker(...)` или явным `scope(ActionScope.MASS)`."
    ),
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