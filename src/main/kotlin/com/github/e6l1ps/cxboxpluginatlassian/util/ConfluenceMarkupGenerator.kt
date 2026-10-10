package com.github.e6l1ps.cxboxpluginatlassian.util

import com.github.e6l1ps.cxboxpluginatlassian.model.*
import com.github.e6l1ps.cxboxpluginatlassian.settings.ConfluenceSettings
import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import java.net.URLEncoder

object ConfluenceMarkupGenerator {

    private var baseUrl: String = ""
    private var spaceKey: String = ""

    /** Подтягивает URL и space key из сохранённых настроек проекта. */
    fun configure(settings: ConfluenceSettings) {
        this.baseUrl = settings.baseUrl.removeSuffix("/")
        this.spaceKey = settings.spaceKey
    }

    fun generateHierarchicalDocumentation(
        result: MetaFilesResult,
        project: Project,
        combinedUrlsMap: MutableMap<String, String?>
    ): Map<String, Pair<String, String?>> {
        configure(project.service<ConfluenceSettings>())
        val pages = mutableMapOf<String, Pair<String, String?>>()

        // 1. Главная страница
        pages["MAIN"] = generateMainPage(result) to null

        // 2. Создаем родительские страницы для каждой категории
        pages["CATEGORY_SCREENS"] = generateScreensCategoryPage("Screens", result.screens) to "MAIN"
        pages["CATEGORY_VIEWS"] = generateViewsCategoryPage("Views", result.views) to "MAIN"
        pages["CATEGORY_WIDGETS"] = generateWidgetsCategoryPage("Widgets", result.widgets) to "MAIN"

        // 3. Страницы для каждого Screen (родитель = CATEGORY_SCREENS)
        result.screens.forEach { screen ->
            pages["SCREEN_${screen.name}"] = generateScreenPage(screen, result) to "CATEGORY_SCREENS"
        }

        // 4. Страницы для каждого View (родитель = CATEGORY_VIEWS)
        result.views.forEach { view ->
            pages["VIEW_${view.name}"] = generateViewPage(view, result) to "CATEGORY_VIEWS"
        }

        val serviceActionAnalysisService = ServiceActionAnalysisService(project)
        val bcToServiceImpl = serviceActionAnalysisService.buildBcToServiceImpl()
        val bcToFieldsLogic = bcToFieldsLogic(bcToServiceImpl)

        val bcToActionsMap = serviceActionAnalysisService.buildBcToActionsMap(bcToServiceImpl)
        // 5. Страницы для каждого Widget (родитель = CATEGORY_WIDGETS)
        result.widgets.forEach { widget ->
            pages["WIDGET_${widget.name}"] =
                generateWidgetPage(widget, bcToActionsMap, bcToFieldsLogic, result, combinedUrlsMap) to "CATEGORY_WIDGETS"
        }

        return pages
    }

    fun bcToFieldsLogic(bcToServiceImpl: Map<String, PsiClass>): Map<String, Pair<PsiClass, Map<String, String>>> {
        return bcToServiceImpl.mapNotNull { (bc, serviceClass) ->
            val dtoClass = findDtoClassForService(serviceClass)
            if (dtoClass != null) {
                val fieldMappings = parseDtoConstructorMapping(dtoClass)
                bc to (dtoClass to fieldMappings)
            } else {
                null
            }
        }.toMap()
    }

    // Вспомогательный метод для поиска DTO класса
    private fun findDtoClassForService(serviceClass: PsiClass): PsiClass? {
        return serviceClass.extendsList?.referenceElements
            ?.firstOrNull { ref ->
                ref.qualifiedName?.contains("VersionAwareResponseService") == true
            }
            ?.typeParameters
            ?.firstOrNull()
            ?.let { dtoType ->
                JavaPsiFacade.getInstance(serviceClass.project)
                    .findClass(dtoType.canonicalText, serviceClass.resolveScope)
            }
    }

    fun parseDtoConstructorMapping(dtoClass: PsiClass): Map<String, String> {
        val mappings = mutableMapOf<String, String>()

        // Ищем все конструкторы
        dtoClass.constructors.forEach { constructor ->
            constructor.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    // Проверяем, что это присваивание полю this
                    if (expression.lExpression is PsiReferenceExpression) {
                        val refExpr = expression.lExpression as PsiReferenceExpression
                        if (refExpr.qualifierExpression?.text == "this") {
                            val fieldName = refExpr.referenceName
                            val fieldValue = expression.rExpression?.text ?: ""
                            if (fieldName != null) {
                                mappings[fieldName] = fieldValue
                            }
                        }
                    }
                    super.visitAssignmentExpression(expression)
                }
            })
        }

        return mappings
    }

    private fun isThisFieldAssignment(expression: PsiAssignmentExpression): Boolean {
        return expression.lExpression is PsiReferenceExpression &&
                (expression.lExpression as PsiReferenceExpression).qualifierExpression?.text == "this"
    }

    private fun processMethodCall(methodCall: PsiMethodCallExpression, mappings: MutableMap<String, String>) {
        // Здесь можно добавить обработку специфических методов маппинга
        // Например, если используются builder-методы или другие подходы
    }

    private fun generateScreensCategoryPage(category: String, screens: List<ScreenFile>): String {
        val screensWithNavigation = screens.count { it.navigation != null }
        val totalViewsInNavigation = screens.sumOf { countViewsInScreen(it) }
        val screensWithPrimaryViews = screens.count { it.primaryViews.isNotEmpty() }

        return """
        <h1>🖥 $category</h1>
        <p><em>Категория экранов приложения</em></p>
        
        <h2>📊 Статистика Screens</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <td><strong>Всего Screens</strong></td>
                <td><strong>${screens.size}</strong></td>
            </tr>
            <tr>
                <td>Screens с навигацией</td>
                <td>$screensWithNavigation</td>
            </tr>
            <tr>
                <td>Screens с основными Views</td>
                <td>$screensWithPrimaryViews</td>
            </tr>
            <tr>
                <td>Всего Views в навигации</td>
                <td>$totalViewsInNavigation</td>
            </tr>
        </table>
        
        <h2>📋 Список Screens</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Название</th>
                <th>Имя</th>
                <th>Иконка</th>
                <th>Порядок</th>
                <th>Основное View</th>
                <th>Views в навигации</th>
                <th>Основные Views</th>
            </tr>
            ${
            screens.joinToString("") { screen ->
                val viewsCount = countViewsInScreen(screen)
                """
                <tr>
                    <td><strong>${escapeHtml(screen.title)}</strong></td>
                    <td>${getPageLink("Screen:", screen.name)}</td>
                    <td>${screen.icon ?: "-"}</td>
                    <td>${screen.order ?: "-"}</td>
                    <td>${screen.primaryViewName ?: "-"}</td>
                    <td>$viewsCount</td>
                    <td>${screen.primaryViews.size}</td>
                </tr>
                """
            }
        }
        </table>
        
        <h2>📈 Распределение по иконкам</h2>
        ${generateScreensIconsDistribution(screens)}
    """.trimIndent()
    }

    private fun generateViewsCategoryPage(category: String, views: List<ViewFile>): String {
        val viewsByTemplate = views.groupBy { it.template ?: "Не указан" }
        val totalWidgets = views.sumOf { it.widgets.size }
        val viewsWithRoles = views.count { it.rolesAllowed.isNotEmpty() }

        return """
        <h1>$category</h1>
        <p><em>Категория представлений приложения</em></p>
        
        <h2>📊 Статистика Views</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <td><strong>Всего Views</strong></td>
                <td><strong>${views.size}</strong></td>
            </tr>
            <tr>
                <td>Views с ролями</td>
                <td>$viewsWithRoles</td>
            </tr>
            <tr>
                <td>Всего виджетов</td>
                <td>$totalWidgets</td>
            </tr>
            <tr>
                <td>Среднее виджетов на View</td>
                <td>${"%.1f".format(totalWidgets.toDouble() / views.size)}</td>
            </tr>
        </table>
        
        <h2>📊 Распределение по шаблонам</h2>
        ${generateViewsTemplateDistribution(viewsByTemplate)}
        
        <h2>📋 Список Views</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Название</th>
                <th>Имя</th>
                <th>Шаблон</th>
                <th>Роли</th>
                <th>Виджетов</th>
                <th>URL</th>
            </tr>
            ${
            views.take(100).joinToString("") { view ->
                """
                <tr>
                    <td><strong>${escapeHtml(view.title)}</strong></td>
                    <td>${getPageLink("View:", view.name)}</td>
                    <td>${view.template ?: "-"}</td>
                    <td>${if (view.rolesAllowed.isNotEmpty()) view.rolesAllowed.joinToString(", ") else "-"}</td>
                    <td>${view.widgets.size}</td>
                    <td><code>${view.url ?: "-"}</code></td>
                </tr>
                """
            }
        }
        </table>
        ${if (views.size > 100) "<p><em>... и ещё ${views.size - 100} views</em></p>" else ""}
    """.trimIndent()
    }

    private fun generateWidgetsCategoryPage(category: String, widgets: List<WidgetFile>): String {
        val widgetsByType = widgets.groupBy { it.type }
        val totalFields = widgets.sumOf { it.fields.size }
        val uniqueBCs = widgets.map { it.bc }.distinct().size
        val widgetsWithActions = widgets.count { it.actionGroups?.include?.isNotEmpty() == true }

        return """
        <h1>$category</h1>
        <p><em>Категория виджетов приложения</em></p>
        
        <h2>📊 Статистика Widgets</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <td><strong>Всего Widgets</strong></td>
                <td><strong>${widgets.size}</strong></td>
            </tr>
            <tr>
                <td>Уникальных BC</td>
                <td>$uniqueBCs</td>
            </tr>
            <tr>
                <td>Всего полей</td>
                <td>$totalFields</td>
            </tr>
            <tr>
                <td>Widgets с действиями</td>
                <td>$widgetsWithActions</td>
            </tr>
            <tr>
                <td>Среднее полей на Widget</td>
                <td>${"%.1f".format(totalFields.toDouble() / widgets.size)}</td>
            </tr>
        </table>
        
        <h2>📊 Распределение по типам</h2>
        ${generateWidgetsTypeDistribution(widgetsByType)}
        
        <h2>📊 Топ BC по количеству виджетов</h2>
        ${generateTopBCsDistribution(widgets)}
        
        <h2>📋 Список Widgets</h2>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Название</th>
                <th>Имя</th>
                <th>Тип</th>
                <th>BC</th>
                <th>Поля</th>
                <th>Действия</th>
            </tr>
            ${
            widgets.take(100).joinToString("") { widget ->
                val actionsCount = widget.actionGroups?.include?.size ?: 0
                """
                <tr>
                    <td><strong>${escapeHtml(widget.title)}</strong></td>
                    <td>${getPageLink("Widget:", widget.name)}</td>
                    <td><code>${escapeHtml(widget.type)}</code></td>
                    <td><code>${escapeHtml(widget.bc)}</code></td>
                    <td>${widget.fields.size}</td>
                    <td>${if (actionsCount > 0) actionsCount else "-"}</td>
                </tr>
                """
            }
        }
        </table>
        ${if (widgets.size > 100) "<p><em>... и ещё ${widgets.size - 100} widgets</em></p>" else ""}
    """.trimIndent()
    }

    private fun generateEmptyCategoryPage(category: String): String {
        return """
        <h1>📁 $category</h1>
        <p><em>Эта категория пуста</em></p>
        <p>В данной категории не найдено элементов для отображения.</p>
    """.trimIndent()
    }

    private fun generateViewsTemplateDistribution(viewsByTemplate: Map<String, List<ViewFile>>): String {
        val totalViews = viewsByTemplate.values.sumOf { it.size }

        return """
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Шаблон</th>
                <th>Количество</th>
                <th>Процент</th>
            </tr>
            ${
            viewsByTemplate.entries.sortedByDescending { it.value.size }.joinToString("") { (template, views) ->
                val percentage = (views.size * 100.0 / totalViews).toInt()
                """
                <tr>
                    <td><code>${escapeHtml(template)}</code></td>
                    <td>${views.size}</td>
                    <td>$percentage%</td>
                </tr>
                """
            }
        }
        </table>
    """.trimIndent()
    }

    private fun generateWidgetsTypeDistribution(widgetsByType: Map<String, List<WidgetFile>>): String {
        val totalWidgets = widgetsByType.values.sumOf { it.size }

        return """
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Тип виджета</th>
                <th>Количество</th>
                <th>Процент</th>
            </tr>
            ${
            widgetsByType.entries.sortedByDescending { it.value.size }.joinToString("") { (type, widgets) ->
                val percentage = (widgets.size * 100.0 / totalWidgets).toInt()
                """
                <tr>
                    <td><code>${escapeHtml(type)}</code></td>
                    <td>${widgets.size}</td>
                    <td>$percentage%</td>
                </tr>
                """
            }
        }
        </table>
    """.trimIndent()
    }

    private fun generateTopBCsDistribution(widgets: List<WidgetFile>): String {
        val bcDistribution = widgets.groupBy { it.bc }
        val topBCs = bcDistribution.entries.sortedByDescending { it.value.size }.take(10)

        return """
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>BC</th>
                <th>Количество виджетов</th>
                <th>Процент</th>
            </tr>
            ${
            topBCs.joinToString("") { (bc, widgets) ->
                val percentage = (widgets.size * 100.0 / widgets.size).toInt()
                """
                <tr>
                    <td><code>${escapeHtml(bc)}</code></td>
                    <td>${widgets.size}</td>
                    <td>$percentage%</td>
                </tr>
                """
            }
        }
        </table>
        ${if (bcDistribution.size > 10) "<p><em>... и ещё ${bcDistribution.size - 10} BC</em></p>" else ""}
    """.trimIndent()
    }

    private fun generateScreensIconsDistribution(screens: List<ScreenFile>): String {
        val iconsDistribution = screens.groupBy { it.icon ?: "Не указана" }

        return """
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Иконка</th>
                <th>Количество</th>
                <th>Процент</th>
            </tr>
            ${
            iconsDistribution.entries.sortedByDescending { it.value.size }.joinToString("") { (icon, screens) ->
                val percentage = (screens.size * 100.0 / screens.size).toInt()
                """
                <tr>
                    <td><code>${if (icon == "Не указана") icon else "'$icon'"} </code></td>
                    <td>${screens.size}</td>
                    <td>$percentage%</td>
                </tr>
                """
            }
        }
        </table>
    """.trimIndent()
    }

    private fun generateCategoryPage(category: String, items: List<Any>): String {
        return """
            <h1>${category}</h1>
        """.trimIndent()
    }

    private fun generateMainPage(result: MetaFilesResult): String {
        val totalFields = result.widgets.sumOf { it.fields.size }
        val fieldTypes = result.widgets.flatMap { it.fields }.groupBy { it.type }
        val bcCount = result.widgets.map { it.bc }.distinct().size
        val viewsWithRoles = result.views.sumOf { it.rolesAllowed.size }

        return """
            <h1>🏗 CxBox Meta Documentation</h1>
            <p><em>Автоматически сгенерированная документация по структуре приложения</em></p>
            
            <h2>📊 Общая статистика</h2>
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <td><strong>Screens (Экраны)</strong></td>
                    <td><strong>${result.screens.size}</strong></td>
                </tr>
                <tr>
                    <td><strong>Views (Представления)</strong></td>
                    <td><strong>${result.views.size}</strong></td>
                </tr>
                <tr>
                    <td><strong>Widgets (Виджеты)</strong></td>
                    <td><strong>${result.widgets.size}</strong></td>
                </tr>
                <tr>
                    <td><strong>Всего полей</strong></td>
                    <td><strong>$totalFields</strong></td>
                </tr>
            </table>
            
            <h2>📈 Детальная статистика</h2>
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <td>Уникальных BC</td>
                    <td>$bcCount</td>
                </tr>
                <tr>
                    <td>Screens с навигацией</td>
                    <td>${result.screens.count { it.navigation != null }}</td>
                </tr>
                <tr>
                    <td>Views с ролями</td>
                    <td>$viewsWithRoles</td>
                </tr>
            </table>
            
            <h2>Распределение типов полей</h2>
            ${generateFieldTypesTable(fieldTypes, totalFields)}
            
            <h2>Screens (Экраны)</h2>
            ${generateScreensTable(result.screens)}
            
            <h2>Views (Представления)</h2>
            ${generateViewsTable(result.views)}
            
            <h2>Widgets (Виджеты)</h2>
            ${generateWidgetsOverviewTable(result.widgets)}
            
            <hr/>
            <p><em>Сгенерировано автоматически ${
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
        }</em></p>
        """.trimIndent()
    }

    private fun generateScreenPage(screen: ScreenFile, result: MetaFilesResult): String {
        val relatedViews = result.views.filter { view ->
            screen.navigation?.menu?.any { menu -> containsView(menu, view.name) } == true ||
                    screen.primaryViews.contains(view.name)
        }

        return """
            <div style="margin-bottom: 20.0px;">
              <span style="background-color: rgb(33,150,243);color: white;font-size: 0.65em;font-weight: bold;display: inline-block;margin-bottom: 6.0px;">SCREEN</span>
            </div>
            <h1>Основная информация</h1>
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <td><strong>Заголовок</strong></td>
                    <td><code>${escapeHtml(screen.title)}</code></td>
                </tr>
                ${
            screen.icon?.let {
                """
                <tr>
                    <td><strong>Иконка</strong></td>
                    <td><code>$it</code></td>
                </tr>
                """
            } ?: ""
        }
                ${
            screen.order?.let {
                """
                <tr>
                    <td><strong>Порядок</strong></td>
                    <td>$it</td>
                </tr>
                """
            } ?: ""
        }
                ${
            screen.primaryViewName?.let {
                """
                <tr>
                    <td><strong>Основное View</strong></td>
                    <td>${getPageLink("View:", it)}</td>
                </tr>
                """
            } ?: ""
        }
            </table>
            
            <h1>Навигация</h1>
            ${generateNavigationTree(screen.navigation)}
            
            <h2>📊 Связанные Views</h2>
            ${
            if (relatedViews.isNotEmpty()) {
                generateRelatedViewsTable(relatedViews)
            } else {
                "<p>Нет связанных views</p>"
            }
        }
            
            <h2>⭐ Основные Views</h2>
            <ul>
                ${
            screen.primaryViews.joinToString("") {
                "<li>${getPageLink("View:", it)}</li>"
            }
        }
            </ul>
        """.trimIndent()
    }

    private fun generateViewPage(view: ViewFile, result: MetaFilesResult): String {
        val relatedWidgets = result.widgets.filter { widget ->
            view.widgets.any { it.widgetName == widget.name }
        }

        return """
            <div style="margin-bottom: 20.0px;">
              <span style="background-color: rgb(156,39,176);color: white;font-size: 0.65em;font-weight: bold;display: inline-block;margin-bottom: 6.0px;">VIEW</span>
            </div>
            <h1>Основная информация</h1>
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <td><strong>Заголовок</strong></td>
                    <td><code>${escapeHtml(view.title)}</code></td>
                </tr>
                ${
            view.template?.let {
                """
                <tr>
                    <td><strong>Шаблон</strong></td>
                    <td><code>$it</code></td>
                </tr>
                """
            } ?: ""
        }
                ${
            view.url?.let {
                """
                <tr>
                    <td><strong>URL</strong></td>
                    <td><code>$it</code></td>
                </tr>
                """
            } ?: ""
        }
                <tr>
                    <td><strong>Роли</strong></td>
                    <td>${view.rolesAllowed.joinToString(", ")}</td>
                </tr>
            </table>
            
            <h1>Виджеты</h1>
            ${generateViewWidgetsTable(view.widgets, relatedWidgets)}
            
            <h2>📊 Статистика</h2>
            <table border="1" cellpadding="5" cellspacing="0">
                <tr>
                    <td>Всего виджетов</td>
                    <td><strong>${view.widgets.size}</strong></td>
                </tr>
                <tr>
                    <td>Уникальных позиций</td>
                    <td>${view.widgets.map { it.position }.distinct().size}</td>
                </tr>
                <tr>
                    <td>Связанные BC</td>
                    <td>${relatedWidgets.map { it.bc }.distinct().joinToString(", ")}</td>
                </tr>
            </table>
        """.trimIndent()
    }


    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ТАБЛИЦ ==========

    private fun generateFieldTypesTable(fieldTypes: Map<String, List<WidgetField>>, totalFields: Int): String {
        val sortedTypes = fieldTypes.entries.sortedByDescending { it.value.size }

        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Тип поля</th>
                    <th>Количество</th>
                    <th>Процент</th>
                </tr>
                ${
            sortedTypes.joinToString("") { (type, fields) ->
                val percentage = (fields.size * 100.0 / totalFields).toInt()
                """
                    <tr>
                        <td><code>${escapeHtml(type)}</code></td>
                        <td>${fields.size}</td>
                        <td>$percentage%</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun generateScreensTable(screens: List<ScreenFile>): String {
        if (screens.isEmpty()) return "<p>Нет screens</p>"

        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Название</th>
                    <th>Имя</th>
                    <th>Иконка</th>
                    <th>Порядок</th>
                    <th>Основное View</th>
                    <th>Кол-во Views</th>
                </tr>
                ${
            screens.joinToString("") { screen ->
                val viewsCount = countViewsInScreen(screen)
                """
                    <tr>
                        <td><strong>${escapeHtml(screen.title)}</strong></td>
                        <td><code>${escapeHtml(screen.name)}</code></td>
                        <td>${screen.icon ?: "-"}</td>
                        <td>${screen.order ?: "-"}</td>
                        <td>${screen.primaryViewName ?: "-"}</td>
                        <td>$viewsCount</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun generateViewsTable(views: List<ViewFile>): String {
        if (views.isEmpty()) return "<p>Нет views</p>"

        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Название</th>
                    <th>Имя</th>
                    <th>Шаблон</th>
                    <th>Роли</th>
                    <th>Кол-во виджетов</th>
                </tr>
                ${
            views.joinToString("") { view ->
                """
                    <tr>
                        <td><strong>${escapeHtml(view.title)}</strong></td>
                        <td><code>${escapeHtml(view.name)}</code></td>
                        <td>${view.template ?: "-"}</td>
                        <td>${view.rolesAllowed.joinToString(", ")}</td>
                        <td>${view.widgets.size}</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun generateWidgetsOverviewTable(widgets: List<WidgetFile>): String {
        if (widgets.isEmpty()) return "<p>Нет widgets</p>"

        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Название</th>
                    <th>Имя</th>
                    <th>Тип</th>
                    <th>BC</th>
                    <th>Кол-во полей</th>
                </tr>
                ${
            widgets.take(50).joinToString("") { widget -> // Ограничим для производительности
                """
                    <tr>
                        <td><strong>${escapeHtml(widget.title)}</strong></td>
                        <td><code>${escapeHtml(widget.name)}</code></td>
                        <td><code>${escapeHtml(widget.type)}</code></td>
                        <td><code>${escapeHtml(widget.bc)}</code></td>
                        <td>${widget.fields.size}</td>
                    </tr>
                    """
            }
        }
            </table>
            ${if (widgets.size > 50) "<p>... и ещё ${widgets.size - 50} виджетов</p>" else ""}
        """.trimIndent()
    }

    private fun generateRelatedViewsTable(views: List<ViewFile>): String {
        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>View</th>
                    <th>Шаблон</th>
                    <th>Роли</th>
                    <th>Виджетов</th>
                </tr>
                ${
            views.joinToString("") { view ->
                """
                    <tr>
                        <td><strong>${escapeHtml(view.title)}</strong></td>
                        <td>${view.template ?: "-"}</td>
                        <td>${view.rolesAllowed.joinToString(", ")}</td>
                        <td>${view.widgets.size}</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun generateViewWidgetsTable(viewWidgets: List<ViewWidget>, relatedWidgets: List<WidgetFile>): String {
        if (viewWidgets.isEmpty()) return "<p>Нет виджетов</p>"
        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Позиция</th>
                    <th>Ширина</th>
                    <th>Имя виджета</th>
                    <th>Тип</th>
                    <th>BC</th>
                    <th>Описание заголовка</th>
                    <th>Поля</th>
                </tr>
                ${
            viewWidgets.joinToString("") { vw ->
                val widget = relatedWidgets.find { it.name == vw.widgetName }
                val widgetPageLink = getPageLink("Widget:", vw.widgetName)
                """
                    <tr>
                        <td>${vw.position}</td>
                        <td>${vw.gridWidth}</td>
                        <td><code>${widgetPageLink}</code></td>
                        <td>${CXBoxHrefUtils.getWidgetTypeLink(widget?.type ?: "?")}</td>
                        <td>${widget?.bc ?: "?"}</td>
                        <td>${vw.descriptionTitle ?: "-"}</td>
                        <td>${widget?.fields?.size ?: "?"}</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun getPageLink(prefix: String, widgetName: String): String {
        val pageTitle = "$prefix $widgetName"

        return """<a href="$baseUrl/display/$spaceKey/${
            URLEncoder.encode(
                pageTitle,
                "UTF-8"
            )
        }"><code>$widgetName</code></a>"""
    }

    private fun generateFieldsTable(fields: List<WidgetField>, fieldMappings: Map<String, String>): String {
        if (fields.isEmpty()) return ""

        return """
            <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
                <tr>
                    <th>Ключ</th>
                    <th>Тип</th>
                    <th>Заголовок</th>
                    <th>DrillDown</th>
                    <th>Ширина</th>
                    <th>Режим</th>
                    <th>Логика маппинга</th>
                </tr>
                ${
            fields.joinToString("") { field ->
                """
                    <tr>
                        <td><code>${escapeHtml(field.key)}</code></td>
                        <td>${CXBoxHrefUtils.getFieldTypeLink(escapeHtml(field.type))}</td>
                        <td>${escapeHtml(field.title)}</td>
                        <td>${if (field.drillDown) "✅" else "❌"}</td>
                        <td>${field.width ?: "-"}</td>
                        <td>${field.mode ?: "-"}</td>
                        <td>${fieldMappings[field.key]?.let { "<code style=\"font-size: 12.0px;\">${escapeHtml(it)}</code>" } ?: ""}</td>
                    </tr>
                    """
            }
        }
            </table>
        """.trimIndent()
    }

    private fun generateNavigationTree(navigation: Navigation?): String {
        if (navigation == null) return "<p>Навигация не определена</p>"

        return buildString {
            appendLine(
                """
            <div style="border: 1.0px solid rgb(204,204,204);padding: 15.0px;background: rgb(250,250,250);font-family: &quot;Segoe UI&quot; , sans-serif;font-size: 14.0px;line-height: 1.4;">
            <div class="nav-tree">
            """.trimIndent()
            )

            navigation.menu.forEach { menuItem ->
                appendLine(generateMenuTree(menuItem))
            }

            appendLine("</div></div>")
        }
    }

    private fun generateMenuTree(menuItem: MenuItem): String {
        return buildString {
            appendLine("<ul>")
            appendLine("<li class=\"folder\">📁 ${escapeHtml(menuItem.title ?: menuItem.viewName ?: "not found")}</li>")

            if (menuItem.child.isNotEmpty()) {
                appendLine("<ul>")
                menuItem.child.forEach { child ->
                    when (child) {
                        is MenuItemChild.ViewItem -> {
                            appendLine("<li class=\"view\">${getPageLink("View:", escapeHtml(child.viewName))}</li>")
                        }

                        is MenuItemChild.SubMenu -> {
                            appendLine(generateSubMenuTree(child))
                        }
                    }
                }
                appendLine("</ul>")
            } else {
                menuItem.viewName?.let { viewName ->
                    appendLine("<ul>")
                    appendLine("<li class=\"view\">${getPageLink("View:", escapeHtml(viewName))}</li>")
                    appendLine("</ul>")
                }
            }

            appendLine("</ul>")
        }
    }

    private fun generateSubMenuTree(subMenu: MenuItemChild.SubMenu): String {
        return buildString {
            appendLine("<ul>")
            appendLine("<li class=\"folder\">📂 ${escapeHtml(subMenu.title)}</li>")
            if (subMenu.child.isNotEmpty()) {
                appendLine("<ul>")
                subMenu.child.forEach { child ->
                    when (child) {
                        is MenuItemChild.ViewItem -> {
                            appendLine("<li class=\"view\">${getPageLink("View:", escapeHtml(child.viewName))}</li>")
                        }

                        is MenuItemChild.SubMenu -> {
                            appendLine(generateSubMenuTree(child))
                        }
                    }
                }
                appendLine("</ul>")
            }
            appendLine("</ul>")
        }
    }


    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    // ... существующие вспомогательные методы для подсчета ...
    public fun countViewsInScreen(screen: ScreenFile): Int {
        return countViewsInNavigation(screen.navigation) + screen.primaryViews.size
    }

    private fun countViewsInNavigation(navigation: Navigation?): Int {
        if (navigation == null) return 0
        return navigation.menu.sumOf { menuItem -> countViewsInMenuItem(menuItem) }
    }

    private fun countViewsInMenuItem(menuItem: MenuItem): Int {
        return menuItem.child.sumOf { child ->
            when (child) {
                is MenuItemChild.ViewItem -> 1
                is MenuItemChild.SubMenu -> countViewsInSubMenu(child)
            }
        }
    }

    private fun countViewsInSubMenu(subMenu: MenuItemChild.SubMenu): Int {
        return subMenu.child.sumOf { child ->
            when (child) {
                is MenuItemChild.ViewItem -> 1
                is MenuItemChild.SubMenu -> countViewsInSubMenu(child)
            }
        }
    }

    private fun containsView(menu: MenuItem, viewName: String): Boolean {
        return menu.child.any { child ->
            when (child) {
                is MenuItemChild.ViewItem -> child.viewName == viewName
                is MenuItemChild.SubMenu -> containsViewInSubMenu(child, viewName)
            }
        }
    }

    private fun containsViewInSubMenu(subMenu: MenuItemChild.SubMenu, viewName: String): Boolean {
        return subMenu.child.any { child ->
            when (child) {
                is MenuItemChild.ViewItem -> child.viewName == viewName
                is MenuItemChild.SubMenu -> containsViewInSubMenu(child, viewName)
            }
        }
    }

    private fun generateWidgetPage(
        widget: WidgetFile,
        bcToActionsMap: Map<String, List<ButtonInfo>>,
        bcToFieldsLogic: Map<String, Pair<PsiClass, Map<String, String>>>,
        result: MetaFilesResult,
        combinedUrlsMap: MutableMap<String, String?>
    ): String {
        val buttonsInfo = if (widget.actionGroups != null) {
            bcToActionsMap.getOrDefault(widget.bc, emptyList())
        } else {
            emptyList()
        }

        val baseEntity = bcToFieldsLogic[widget.bc]?.first?.name ?: "not found"
        val fieldMappings = bcToFieldsLogic[widget.bc]?.second ?: emptyMap()

        // Находим все View, которые используют этот виджет
        val usedInViews = findViewsUsingWidget(widget.name, result)
        // Получаем URL, если он есть
        val pageUrl = combinedUrlsMap[widget.name]
        val urlLine = if (!pageUrl.isNullOrEmpty()) {"""
            <tr>
                <td><strong>Ссылка на найденную страницу</strong></td>
                <td>${"""<a href="${escapeHtml(pageUrl)}"><code>${widget.name}</code></a>"""}</td>
            </tr>
        """.trimIndent()
        } else {
            "" // не добавляем строку
        }
        return """
        <div style="margin-bottom: 20.0px;">
            <span style="background-color: rgb(76,175,80);color: white;font-size: 0.65em;font-weight: bold;display: inline-block;margin-bottom: 6.0px;">WIDGET</span>
        </div>
        <h1>Основная информация</h1>
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <td><strong>Заголовок</strong></td>
                <td><code>${escapeHtml(widget.title)}</code></td>
            </tr>
            <tr>
                <td><strong>Тип</strong></td>
                <td>${CXBoxHrefUtils.getWidgetTypeLink(escapeHtml(widget.type))}</td>
            </tr>
            <tr>
                <td><strong>BC</strong></td>
                <td><code>${escapeHtml(widget.bc)}</code></td>
            </tr>
            <tr>
                <td><strong>Базовая сущность</strong></td>
                <td><code>${escapeHtml(baseEntity)}</code></td>
            </tr>
            <tr>
                <td><strong>Условие отображения</strong></td>
                <td>${if (widget.showCondition != null) "✅ Настроено" else "❌ Отсутствует"}</td>
            </tr>
            $urlLine
        </table>
        
        ${
            widget.showCondition?.let {
                """
        <h1>Условие отображения</h1>
        ${analyzeShowConditionLogic(it, bcToFieldsLogic)}
        """
            } ?: ""
        }
    
        <h1>Поля (${widget.fields.size})</h1>
        ${generateFieldsTable(widget.fields, fieldMappings)}
        
        <h1>Кнопки (${widget.actionGroups?.include?.size ?: 0})</h1>
        ${generateButtonsTable(buttonsInfo, widget.actionGroups)}
        
        ${
            widget.options?.let {
                """
                <h1>Прочие настройки</h1>
                <ac:structured-macro ac:name="expand">
                    <ac:parameter ac:name="title">Показать настройки виджета</ac:parameter>
                    <ac:rich-text-body>
                        <ac:structured-macro ac:name="code">
                            <ac:parameter ac:name="language">json</ac:parameter>
                            <ac:plain-text-body><![CDATA[${JsonParser.parseString(it.toString())}]]></ac:plain-text-body>
                        </ac:structured-macro>
                    </ac:rich-text-body>
                </ac:structured-macro>
                """
            } ?: ""
        }
        
        ${
            if (usedInViews.isNotEmpty()) {
                """
            <h1>Использование на View</h1>
            ${generateWidgetUsageTable(usedInViews, widget.name)}
            """
            } else {
                """
        <h1>Использование на View</h1>
        <div style="background: rgb(255,243,205);padding: 10.0px;border-radius: 5.0px;border: 1.0px solid rgb(255,234,167);margin: 10.0px 0;">
            <p>⚠️ <strong>Виджет не используется ни на одном View</strong></p>
            <p>Это может означать, что виджет:</p>
            <ul>
                <li>Используется динамически</li>
                <li>Будет добавлен в будущем</li>
                <li>Является устаревшим</li>
            </ul>
        </div>
        """
            }
        }
    """.trimIndent()
    }

    /**
     * Генерирует простой список View, где используется виджет
     */
    private fun generateWidgetUsageTable(usedInViews: List<String>, widgetName: String): String {
        return """
    <div style="margin-bottom: 20.0px;">
        <p>Виджет <code>${escapeHtml(widgetName)}</code> используется на <strong>${usedInViews.size}</strong> View:</p>
        
        <ul>
            ${
            usedInViews.joinToString("") { viewName ->
                "<li>${getPageLink("View:", viewName)}</li>"
            }
        }
        </ul>
    </div>
    """.trimIndent()
    }

    /**
     * Находит все View, которые используют указанный виджет
     */
    private fun findViewsUsingWidget(widgetName: String, result: MetaFilesResult): List<String> {
        val usageInfo = mutableListOf<String>()

        result.views.forEach { view ->
            view.widgets.forEach { viewWidget ->
                if (viewWidget.widgetName == widgetName) {
                    usageInfo.add(view.name)
                }
            }
        }

        return usageInfo
    }

    private fun generateButtonsTable(buttonsInfo: List<ButtonInfo>, actionGroups: ActionGroups?): String {
        val actionNames = actionGroups?.include?.takeIf { it.isNotEmpty() } ?: return ""

        return """
        <table border="1" cellpadding="5" cellspacing="0" style="width: 100%;">
            <tr>
                <th>Кнопка</th>
                <th>Текст</th>
                <th>Область видимости</th>
                <th>Доступность</th>
                <th>Реакция</th>
                <th>Pre-action</th>
                <th>Auto-save</th>
            </tr>
            ${
            actionNames.joinToString("") { actionName ->
                val buttonInfo = buttonsInfo.find { it.actionName == actionName }
                """
                <tr>
                    <td><code>${escapeHtml(actionName)}</code></td>
                    <td>${buttonInfo?.buttonText ?: "?"}</td>
                    <td>${buttonInfo?.scope?.name ?: "?"}</td>
                    <td>
                        <ac:structured-macro ac:name="expand">
                            <ac:parameter ac:name="title">Показать доступность</ac:parameter>
                            <ac:rich-text-body>
                                <ac:structured-macro ac:name="code">
                                    <ac:parameter ac:name="language">java</ac:parameter>
                                    <ac:plain-text-body><![CDATA[${buttonInfo?.availableMethod ?: "-"}]]></ac:plain-text-body>
                                </ac:structured-macro>
                            </ac:rich-text-body>
                        </ac:structured-macro>
                    </td>
                    <td>
                        <ac:structured-macro ac:name="expand">
                            <ac:parameter ac:name="title">Показать реакцию</ac:parameter>
                            <ac:rich-text-body>
                                <ac:structured-macro ac:name="code">
                                    <ac:parameter ac:name="language">java</ac:parameter>
                                    <ac:plain-text-body><![CDATA[${buttonInfo?.invokerMethod ?: "-"}]]></ac:plain-text-body>
                                </ac:structured-macro>
                            </ac:rich-text-body>
                        </ac:structured-macro>
                    </td>
                    <td>
                        <ac:structured-macro ac:name="expand">
                            <ac:parameter ac:name="title">Показать preAction</ac:parameter>
                            <ac:rich-text-body>
                                <ac:structured-macro ac:name="code">
                                    <ac:parameter ac:name="language">java</ac:parameter>
                                    <ac:plain-text-body><![CDATA[${buttonInfo?.preAction ?: "-"}]]></ac:plain-text-body>
                                </ac:structured-macro>
                            </ac:rich-text-body>
                        </ac:structured-macro>
                    </td>
                    <td>${if (buttonInfo?.autoSaveBefore == true) "✅" else "❌"}</td>
                </tr>
                """
            }
        }
        </table>
    """.trimIndent()
    }

    private fun analyzeShowConditionLogic(
        showCondition: ShowCondition,
        bcToFieldsLogic: Map<String, Pair<PsiClass, Map<String, String>>>
    ): String {
        val (dtoClass, fieldMappings) = bcToFieldsLogic[showCondition.bcName]
            ?: return "Не найдена логика для BC: ${showCondition.bcName}"

        val fieldKey = showCondition.params.fieldKey
        val expectedValue = showCondition.params.value

        // Ищем логику для поля условия
        val fieldLogic = fieldMappings[fieldKey]

        return buildString {
            appendLine("<table border=\"1\" cellpadding=\"5\" cellspacing=\"0\" style=\"width: 100%;\">")
            appendLine("<tr><td><strong>Поле условия</strong></td><td><code>${escapeHtml(fieldKey)}</code></td></tr>")
            appendLine("<tr><td><strong>Ожидаемое значение</strong></td><td>${escapeHtml(expectedValue.toString()).let { if (it.isEmpty()) "" else "<code>$it</code>" }}</td></tr>")
            appendLine("<tr><td><strong>BC</strong></td><td><code>${escapeHtml(showCondition.bcName)}</code></td></tr>")
            appendLine("<tr><td><strong>Ключ условия</strong></td><td><code>${escapeHtml(showCondition.key)}</code></td></tr>")
            appendLine("<tr><td><strong>Последовательность</strong></td><td>${showCondition.sequence}</td></tr>")
            appendLine("</table>")

            if (fieldLogic != null) {
                appendLine("<h4>📋 Логика поля условия</h4>")
                appendLine("<div style=\"background: rgb(248,249,250);padding: 10.0px;border-radius: 5.0px;border: 1.0px solid rgb(233,236,239);\">")
                appendLine("<pre style=\"font-family: &quot;Courier New&quot; , monospace;font-size: 12.0px;\">")
                appendLine(escapeHtml(fieldLogic))
                appendLine("</pre>")
                appendLine("</div>")

                // Анализ логики
                appendLine("<h4>🔎 Интерпретация логики</h4>")
                appendLine("<ul>")

                when {
                    fieldLogic.contains("this.$fieldKey ==") || fieldLogic.contains("this.$fieldKey = ") -> {
                        appendLine("<li>✅ Поле <code>$fieldKey</code> устанавливается напрямую</li>")
                    }

                    fieldLogic.contains("set$fieldKey") -> {
                        appendLine("<li>✅ Используется сеттер для поля <code>$fieldKey</code></li>")
                    }

                    fieldLogic.contains("get$fieldKey") -> {
                        appendLine("<li>ℹ️ Поле <code>$fieldKey</code> используется в геттере</li>")
                    }

                    else -> {
                        appendLine("<li>⚠️ Логика установки поля <code>$fieldKey</code> требует дополнительного анализа</li>")
                    }
                }

                // Проверяем, соответствует ли логика ожидаемому значению
                when (expectedValue) {
                    true -> {
                        if (fieldLogic.contains("true") || fieldLogic.contains("Boolean.TRUE")) {
                            appendLine("<li>✅ Логика соответствует ожидаемому значению <code>true</code></li>")
                        } else {
                            appendLine("<li>⚠️ Логика может не соответствовать ожидаемому значению <code>true</code></li>")
                        }
                    }

                    false -> {
                        if (fieldLogic.contains("false") || fieldLogic.contains("Boolean.FALSE")) {
                            appendLine("<li>✅ Логика соответствует ожидаемому значению <code>false</code></li>")
                        } else {
                            appendLine("<li>⚠️ Логика может не соответствовать ожидаемому значению <code>false</code></li>")
                        }
                    }

                    else -> {
                        appendLine("<li>ℹ️ Ожидаемое значение: ${expectedValue.toString().let { if (it.isEmpty()) "" else "<code>$it</code>" }}</li>")
                    }
                }

                appendLine("</ul>")
            } else {
                appendLine("<div style=\"background: rgb(255,243,205);padding: 10.0px;border-radius: 5.0px;border: 1.0px solid rgb(255,234,167);\">")
                appendLine("<p>⚠️ <strong>Логика для поля условия не найдена</strong></p>")
                appendLine(
                    "<p>Поле <code>${escapeHtml(fieldKey)}</code> не найдено в маппингах DTO для BC <code>${
                        escapeHtml(
                            showCondition.bcName
                        )
                    }</code></p>"
                )
                appendLine("</div>")
            }
        }
    }

    fun findDraftLegalEntityParameterClass(psiClass: PsiClass): PsiClass? {
        return psiClass.constructors
            .flatMap { it.parameterList.parameters.toList() }
            .mapNotNull { parameter ->
                (parameter.type as? PsiClassType)?.resolve()
            }
            .firstOrNull()
    }


    fun findTableName(entityClass: PsiClass): String? {
        // Ищем аннотацию @Table
        val tableAnnotation = entityClass.annotations.find {
            it.qualifiedName == "javax.persistence.Table" ||
                    it.qualifiedName == "jakarta.persistence.Table"
        }

        if (tableAnnotation != null) {
            // Ищем атрибут name в аннотации @Table
            val nameAttribute = tableAnnotation.findAttributeValue("name")
            if (nameAttribute != null) {
                return nameAttribute.text.removeSurrounding("\"")
            }
        }

        // Если аннотации @Table нет, ищем @Entity и используем имя класса
        val entityAnnotation = entityClass.annotations.find {
            it.qualifiedName == "javax.persistence.Entity" ||
                    it.qualifiedName == "jakarta.persistence.Entity"
        }

        if (entityAnnotation != null) {
            // Если есть @Entity, но нет @Table, используем имя класса
            return convertClassNameToTableName(entityClass.name ?: "")
        }

        return null
    }

    private fun convertClassNameToTableName(className: String): String {
        // Простая конвертация CamelCase to SNAKE_CASE
        return className.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    }

}