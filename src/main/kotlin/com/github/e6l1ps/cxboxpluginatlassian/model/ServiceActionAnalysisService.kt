package com.github.e6l1ps.cxboxpluginatlassian.model


import com.github.e6l1ps.cxboxpluginatlassian.settings.CxboxSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil

class ServiceActionAnalysisService(private val project: Project) {

    fun buildBcToServiceImpl(): Map<String, PsiClass> =
        ReadAction.compute<Map<String, PsiClass>, RuntimeException> {
            buildBcToServiceImplInternal()
        }

    fun buildBcToActionsMap(controllerBcMap: Map<String, PsiClass>): Map<String, List<ButtonInfo>> =
        ReadAction.compute<Map<String, List<ButtonInfo>>, RuntimeException> {
            buildBcToActionsMapInternal(controllerBcMap)
        }

    fun buildBcToServiceImplInternal(): Map<String, PsiClass> {
        val result = mutableMapOf<String, PsiClass>()
        val controllerEnum = findControllerEnum() ?: return result

        controllerEnum.fields
            .filterIsInstance<PsiEnumConstant>()
            .forEach { enumConst ->
                val bcName = enumConst.name
                val serviceClass = extractServiceClassFromEnum(enumConst) ?: return@forEach
                val implClass =
                    if (serviceClass.isInterface) findImplClass(serviceClass) ?: serviceClass else serviceClass
                result.put(bcName, implClass)
            }

        return result
    }

    private fun findImplClass(interfaceClass: PsiClass): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        val name = interfaceClass.name ?: return null
        val byImplName = facade.findClass("${interfaceClass.qualifiedName}Impl", GlobalSearchScope.allScope(project))
        if (byImplName != null) return byImplName

        // Если не нашли по имени — ищем классы, которые его реализуют
        val allClasses = PsiShortNamesCache.getInstance(project)
            .getClassesByName("${name}Impl", GlobalSearchScope.allScope(project))
        if (allClasses.isNotEmpty()) return allClasses.first()

        // Альтернатива: ищем все классы, реализующие интерфейс
        val candidates = PsiShortNamesCache.getInstance(project)
            .getAllClassNames()
            .flatMap { PsiShortNamesCache.getInstance(project).getClassesByName(it, GlobalSearchScope.projectScope(project)).toList() }
            .filter { it.interfaces.contains(interfaceClass) }

        return candidates.firstOrNull()
    }

    private fun extractServiceClassFromEnum(enumConst: PsiEnumConstant): PsiClass? {
        val argList = enumConst.argumentList ?: return null
        val classArg = argList.expressions.filterIsInstance<PsiClassObjectAccessExpression>().lastOrNull() ?: return null
        val classType = classArg.operand.type as? PsiClassType ?: return null
        return classType.resolve()
    }
    /**
     * Класс-контроллер с enum бизнес-компонентов. FQN задаётся настройкой проекта
     * или переменной окружения [CxboxSettings.ENV_REST_CONTROLLER_CLASS]: раньше
     * значение было захардкожено под конкретного заказчика.
     */
    private fun findControllerEnum(): PsiClass? {
        val className = project.service<CxboxSettings>().restControllerClass
        if (className.isEmpty()) {
            thisLogger().warn(
                "BC-контроллер не задан: укажите ${CxboxSettings.ENV_REST_CONTROLLER_CLASS} " +
                    "в .env (или в настройках плагина), иначе связь BC -> service -> actions пуста"
            )
            return null
        }

        return JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.allScope(project))
            ?: run {
                thisLogger().warn("Класс BC-контроллера '$className' не найден в проекте")
                null
            }
    }


    fun buildBcToActionsMapInternal(controllerBcMap: Map<String, PsiClass>): Map<String, List<ButtonInfo>> {
        val bcToActions = mutableMapOf<String, List<ButtonInfo>>()

        // 1. Берем все уникальные сервисы
        val uniqueServices = controllerBcMap.values.toSet()

        // 2. Строим Service -> Actions
        val serviceActionsMap = mutableMapOf<PsiClass, List<ButtonInfo>>()
        uniqueServices.forEach { serviceClass ->
            val getActionsMethod = serviceClass.findMethodsByName("getActions", false).firstOrNull() ?: return@forEach
            val actions = parseActions(getActionsMethod)
            if (actions.isNotEmpty()) {
                serviceActionsMap[serviceClass] = actions
            }
        }

        // 3. Строим BC -> Actions через контроллерную мапу
        controllerBcMap.forEach { (bcName, serviceClass) ->
            val actions = serviceActionsMap[serviceClass] ?: emptyList()
            bcToActions[bcName] = actions
        }

        return bcToActions
    }

    /**
     * Имена методов `ActionsBuilder`, объявляющих действие.
     * - `action` — универсальная форма `.action(key, text)`;
     * - `create`/`save`/`cancelCreate`/`associate`/`delete` — лямбда-формы
     *   `create { it -> ... }`; имя действия у них задаёт фреймворк (`ActionType`);
     * - `add` — `.add(key, responseServiceAction)`.
     *
     * `addGroup` и `addAll` намеренно не разбираются: это группировка и перенос
     * чужих действий, а не объявление кнопки.
     */
    private val actionMethodNames = setOf(
        "action", "create", "save", "cancelCreate", "associate", "delete", "add"
    )

    /**
     * Лямбда-формы, у которых имя действия совпадает с типом `ActionType`.
     * Для них имя и текст берутся из [BaseAction], а не из аргументов вызова.
     */
    private val baseActionMethodNames = setOf(
        "create", "save", "cancelCreate", "associate", "delete"
    )

    /** Методы, у которых `ActionsBuilder` сам вызывает `withoutAutoSaveBefore()`. */
    private val autoSaveDisabledMethods = setOf("create", "cancelCreate", "delete")

    private fun parseActions(getActions: PsiMethod): List<ButtonInfo> {
        val body = getActions.body ?: return emptyList()
        val calls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)
        val actions = mutableListOf<ButtonInfo>()

        for (call in calls) {
            val name = call.methodExpression.referenceName ?: continue

            if (name in actionMethodNames) {
                val actionName = extractActionKey(call, name) ?: continue
                val buttonText = extractActionText(call, name) ?: ""
                val scope = extractScope(call, name)
                val availableMethod = extractAvailableMethod(call)
                val invokerMethod = extractInvokerMethod(call)
                val preAction = extractPreAction(call)
                val autoSaveBefore = extractAutoSaveBefore(call, name)
                val iconCode = extractIconCode(call)
                val iconOnly = extractIconOnly(call)
                val iconDisabled = findChainedCall(call, "withoutIcon") != null

                actions.add(
                    ButtonInfo(
                        actionName,
                        buttonText,
                        scope,
                        availableMethod,
                        invokerMethod,
                        preAction,
                        autoSaveBefore,
                        iconCode,
                        iconOnly,
                        iconDisabled
                    )
                )
            }
        }

        return actions
    }

    /**
     * Явный `scope(...)` имеет приоритет. Если его нет, берётся scope, который
     * проставляет сам фреймворк: `create()` и `associate()` объявлены как
     * `scope(ActionScope.BC)` (см. `ActionsBuilder`), остальные — `RECORD`.
     */
    private fun extractScope(call: PsiMethodCallExpression, methodName: String): ActionScope {
        val scopeCall = findChainedCall(call, "scope")
        val scopeString = scopeCall
            ?.argumentList
            ?.expressions
            ?.firstOrNull()
            ?.lastChild
            ?.text
            ?.removeSurrounding("\"")

        if (scopeString != null) {
            return try {
                ActionScope.valueOf(scopeString)
            } catch (_: IllegalArgumentException) {
                ActionScope.RECORD
            }
        }

        return when (methodName) {
            "create", "associate" -> ActionScope.BC
            else -> ActionScope.RECORD
        }
    }

    private fun findChainedCall(start: PsiMethodCallExpression, name: String): PsiMethodCallExpression? {
        // 1️⃣ Проверяем сам вызов
        if (start.methodExpression.referenceName == name) return start

        // 2️⃣ Ищем вложенные вызовы (внутри лямбды)
        val innerCalls = PsiTreeUtil.findChildrenOfType(start, PsiMethodCallExpression::class.java)
        innerCalls.firstOrNull { it.methodExpression.referenceName == name }?.let { return it }

        // 3️⃣ Идём вверх по цепочке (через parent qualifier)
        var parent = start.parent
        while (parent != null) {
            if (parent is PsiMethodCallExpression && parent.methodExpression.referenceName == name) {
                return parent
            }
            parent = parent.parent
        }

        return null
    }

    private fun extractAvailableMethod(call: PsiMethodCallExpression): String? {
        val availableCall = findChainedCall(call, "available") ?: return null
        val ref = availableCall.argumentList.expressions.firstOrNull() as? PsiMethodReferenceExpression ?: return null
        val target = ref.resolve() as? PsiMethod ?: return null
        return target.body?.text?.trim()
    }

    private fun extractInvokerMethod(call: PsiMethodCallExpression): String? {
        val invokerCall = findChainedCall(call, "invoker") ?: return null
        val ref = invokerCall.argumentList.expressions.firstOrNull() as? PsiMethodReferenceExpression ?: return null
        val target = ref.resolve() as? PsiMethod ?: return null
        return target.body?.text?.trim()
    }

    private fun extractPreAction(call: PsiMethodCallExpression): String? {
        val preActionCall = findChainedCall(call, "withPreAction") ?: return null

        val arg = preActionCall.argumentList.expressions.firstOrNull() ?: return null

        // Если это вызов метода
        if (arg is PsiMethodCallExpression) {
            val method = arg.resolveMethod() ?: return null
            return method.body?.text
        }

        // Если это ссылка на метод
        if (arg is PsiMethodReferenceExpression) {
            val method = arg.resolve() as? PsiMethod ?: return null
            return method.body?.text
        }

        return null
    }

    /**
     * Явный `withoutAutoSaveBefore()` выключает автосохранение. Кроме того, у
     * `create()`, `cancelCreate()` и `delete()` фреймворк вызывает его сам
     * (см. `ActionsBuilder`), поэтому для них по умолчанию `false`.
     */
    private fun extractAutoSaveBefore(call: PsiMethodCallExpression, methodName: String): Boolean {
        val withoutAutoSaveCall = findChainedCall(call, "withoutAutoSaveBefore")
        if (withoutAutoSaveCall != null) {
            return false
        }
        return methodName !in autoSaveDisabledMethods
    }

    /**
     * Иконка из `withIcon(ActionIcon.EDIT, true)` — первый аргумент.
     * Берётся имя константы (`EDIT`): значения enum `ActionIcon` в исходниках
     * проекта могут быть недоступны, а имя совпадает с тем, что выбирает разработчик.
     * Если первым аргументом передана строка, возвращается её содержимое.
     */
    private fun extractIconCode(call: PsiMethodCallExpression): String? {
        val iconCall = findChainedCall(call, "withIcon") ?: return null
        val arg = iconCall.argumentList.expressions.firstOrNull() ?: return null

        return when (arg) {
            is PsiReferenceExpression -> arg.referenceName
            is PsiLiteralExpression -> arg.value as? String
            else -> arg.text.trim().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Второй аргумент `withIcon(icon, true)` — «только иконка»: на кнопке нет текста.
     * Если второго аргумента нет, считается `false` (иконка вместе с текстом).
     */
    private fun extractIconOnly(call: PsiMethodCallExpression): Boolean {
        val iconCall = findChainedCall(call, "withIcon") ?: return false
        val secondArg = iconCall.argumentList.expressions.getOrNull(1) ?: return false

        return when (secondArg) {
            is PsiLiteralExpression -> secondArg.value as? Boolean ?: false
            else -> secondArg.text.equals("true", ignoreCase = true)
        }
    }


    /**
     * Имя действия. Для `action(key, ...)` и `add(key, ...)` — первый аргумент:
     * литерал либо константа (`Button.EDIT_CHECK_RESULT`). Для лямбда-форм
     * (`create { ... }`) своего имени нет — действие задаёт фреймворк,
     * поэтому берётся тип из `ActionType`: он же попадает в `actionGroups.include`.
     */
    private fun extractActionKey(call: PsiMethodCallExpression, methodName: String): String? {
        if (methodName in baseActionMethodNames) {
            return methodName
        }
        val args = call.argumentList.expressions
        return when (val first = args.firstOrNull()) {
            is PsiLiteralExpression -> first.value as? String
            is PsiReferenceExpression -> first.referenceName
            else -> null
        }
    }

    private fun extractActionText(call: PsiMethodCallExpression, methodName: String): String? {
        // Пытаемся найти .text(...) в цепочке вызовов
        var current: PsiMethodCallExpression? = call
        while (current != null) {
            if (current.methodExpression.referenceName == "text") {
                val arg = current.argumentList.expressions.firstOrNull()
                return resolveExpressionValue(arg)
            }
            current = current.parent as? PsiMethodCallExpression
        }

        // Лямбда-формы create { ... } / delete { ... }: текст вручную не задаётся,
        // используется локализованный текст по умолчанию
        if (methodName in baseActionMethodNames) {
            return BaseAction.byType(methodName)?.defaultText
        }

        // Иначе — второй аргумент в .action(key, text) или .add(key, responseServiceAction)
        val args = call.argumentList.expressions
        if (args.size >= 2) {
            return resolveExpressionValue(args[1])
        }

        return null
    }

    /** 🔍 Разворачивает выражение до реального текста ("Button.CHECK_STATUS" -> "Проверить статус") */
    private fun resolveExpressionValue(expr: PsiExpression?): String? {
        when (expr) {
            is PsiLiteralExpression -> return expr.value as? String
            is PsiReferenceExpression -> {
                val resolved = expr.resolve()
                if (resolved is PsiField) {
                    // Если поле, например Button.CHECK_STATUS
                    val initializer = resolved.initializer
                    return when (initializer) {
                        is PsiLiteralExpression -> initializer.value as? String
                        else -> initializer?.text
                    }
                }
                return expr.referenceName
            }
        }
        return null
    }
}
