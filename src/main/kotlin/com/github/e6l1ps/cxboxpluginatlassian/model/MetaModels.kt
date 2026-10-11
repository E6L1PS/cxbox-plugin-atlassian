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
    val iconCode: String? = null,
    /** `true`, когда объявлено `withIcon(icon, true)` — на кнопке только иконка, без текста. */
    val iconOnly: Boolean = false,
    /** `true`, когда объявлено `withoutIcon()` — иконка не выводится, в том числе базовая. */
    val iconDisabled: Boolean = false
)

/**
 * Scope действия CXBox (`ActionScope` из `org.cxbox.core.service.action`) — то, над чем
 * действие выполняется. На UI уходит в `ActionDTO.scope` в нижнем регистре
 * (`ActionDescription.toDto` → `getActionScope().toString().toLowerCase()`).
 *
 * [description] — краткий ответ на вопрос «Область видимости»: что именно охватывает
 * действие. Он подставляется в одноимённую колонку таблицы кнопок, поэтому в нём нет
 * деталей объявления — они остались в KDoc констант.
 */
enum class ActionScope(val description: String) {
    /**
     * Действие бизнес-компонента: без привязки к конкретной записи. Задаётся
     * `scope(ActionScope.BC)`; так объявлены `create` и `associate`.
     */
    BC("Весь бизнес-компонент, без привязки к записи"),

    /**
     * Действие над одной записью — режим по умолчанию
     * (`ActionDescriptionBuilder.actionScope = ActionScope.RECORD`).
     * При объявлении через `action(...)` доступность дополнительно ограничивается
     * проверкой `NOT_NULL_ID`, то есть действие доступно только при выбранной записи.
     */
    RECORD("Одна запись — выбранная в виджете"),

    /**
     * Массовое действие над набором записей. Выставляется автоматически при объявлении
     * через `massInvoker(...)` — тогда инвокер получает набор id — либо явным
     * `scope(ActionScope.MASS)`.
     *
     * В UI это bulk-режим таблицы (доступен в List при непустой таблице): выбор строк,
     * проверка выбранного, подтверждение, результат. Запрос уходит по id первой выбранной
     * записи, а id остальных — в её `massIds`.
     */
    MASS("Несколько записей — выбранные в таблице"),
}

/**
 * Базовые действия CXBox — enum `ActionType` из `org.cxbox.core.dto.rowmeta`
 * (фреймворк, не код проекта-потребителя). Такие действия добавляются фреймворком
 * автоматически: в `getActions()` сервиса их обычно нет, поэтому PSI-анализ их не видит,
 * а в meta-файле виджета они могут быть указаны в `options.actionGroups.include`.
 *
 * Поля повторяют `ActionType`:
 * - [text] — `uiMessage("action.<type>")`, то есть локализованный текст по умолчанию;
 * - [icon] — `CxboxActionIconSpecifier`;
 * - [availability] — `ActionAvailableChecker`, базовая проверка видимости кнопки.
 *
 * [reaction] — краткое пояснение, что делает кнопка (в `ActionType` описания нет,
 * действие выполняет front-end CXBox-UI). Помечено как пояснение, а не как выдержка из кода.
 *
 * [scope] — область видимости в том же смысле, что и [ActionScope.description]:
 * для базовых действий это BC-уровень, поэтому берётся описание [ActionScope.BC].
 */
enum class BaseAction(
    /** Значение `ActionType.type` — то, что попадает в `actionGroups.include` и в `ActionDTO.type`. */
    val type: String,
    /** Локализованный текст по умолчанию (`uiMessage("action.<type>")`). */
    val defaultText: String,
    val icon: String,
    val scope: ActionScope,
    val availability: ActionAvailability,
    val reaction: String
) {
    CREATE(
        "create",
        "Создать",
        "PLUS",
        ActionScope.BC,
        ActionAvailability.NOT_NULL_PARENT_ID,
        "Создаёт новую запись BC: сервер возвращает CxboxResponse с созданной записью, " +
            "которая открывается в виджете на редактирование (UI переходит в режим создания)."
    ),
    CANCEL_CREATE(
        "cancel-create",
        "Отменить создание",
        "CLOSE",
        ActionScope.BC,
        ActionAvailability.ALWAYS_TRUE,
        "Отменяет режим создания записи: несохранённые изменения сбрасываются, " +
            "виджет возвращается к ранее выбранной записи."
    ),
    SAVE(
        "save",
        "Сохранить",
        "SAVE",
        ActionScope.RECORD,
        ActionAvailability.NOT_NULL_ID,
        "Сохраняет изменения выбранной записи (инвокер сервиса `update`), " +
            "сервер возвращает обновлённую запись, виджет перечитывает данные."
    ),
    COPY(
        "copy",
        "Копировать",
        "COPY",
        ActionScope.RECORD,
        ActionAvailability.NOT_NULL_ID,
        "Создаёт копию выбранной записи по правилам сервиса " +
            "(`createWithSignedId`/аналог) и открывает её на редактирование."
    ),
    ASSOCIATE(
        "associate",
        "Добавить",
        "PLUS",
        ActionScope.BC,
        ActionAvailability.NOT_NULL_PARENT_ID,
        "Создаёт связь дочерней записи с родительской: сервер сохраняет ассоциацию " +
            "и возвращает обновлённый родительский DTO."
    ),
    DELETE(
        "delete",
        "Удалить",
        "DELETE",
        ActionScope.RECORD,
        ActionAvailability.NOT_NULL_ID,
        "Удаляет выбранную запись (инвокер сервиса `delete`), " +
            "список перечитывается, удалённая запись исчезает из виджета."
    );

    companion object {
        /** Действие по типу из `actionGroups.include`; `null`, если тип не базовый. */
        fun byType(type: String?): BaseAction? = entries.firstOrNull { it.type == type }
    }
}

/**
 * Базовая проверка доступности кнопки — `ActionAvailableChecker` из
 * `org.cxbox.core.service.action`, с которой объявлен `ActionType`.
 */
enum class ActionAvailability(val description: String) {
    /** `ALWAYS_TRUE`: кнопка доступна всегда (доступность может уточняться `available(...)` сервиса). */
    ALWAYS_TRUE("Кнопка доступна всегда"),

    /** `NOT_NULL_ID`: `id != null` — действие доступно только при выбранной записи. */
    NOT_NULL_ID("Доступна, только когда запись выбрана (`id != null`)"),

    /** `NOT_NULL_PARENT_ID`: `parentId != null` — действие доступно только внутри родительской записи. */
    NOT_NULL_PARENT_ID("Доступна, только когда открыт родитель (`parentId != null`)"),
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