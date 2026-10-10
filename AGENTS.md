# cxbox-plugin-atlassian

Плагин IntelliJ Platform (Kotlin + Gradle), который формирует документацию в Atlassian
Confluence по meta-файлам проектов на фреймворке **CXBox**.

Это НЕ официальный плагин CXBox: у CXBox есть собственный IntelliJ-плагин
(автодополнение, инспекции, навигация, генерация кода). Здесь — экспорт документации
в Confluence. Не путать назначение.

## Предметная область: что такое CXBox

CXBox — платформа быстрой разработки enterprise-приложений на Spring Boot.
UI (Cxbox-UI) работает по фиксированному контракту и целиком описывается JSON meta-файлами,
поэтому типовой интерфейс создаётся предоставлением этих файлов.

Meta-файлы (по соглашению в `src/main/resources/meta/`; плагин сканирует каталог
`meta` внутри resources-root модуля):
- **screen** (`*.screen.json`) — навигация: меню, порядок, `primaryViewName`/`primaryViews`
- **view** (`*.view.json`) — layout: набор виджетов, `position`, `gridWidth`, `template`
- **widget** (`*.widget.json`) — UI-компонент: `type`, `bc`, `fields`, `options`,
  `actionGroups.include`, `showCondition`

Бэкенд-сторона того же контракта:
- **BC** (business component) — идентификатор бизнес-компонента; ключ связи widget ↔ backend
- **Entity / DTO** — сущность и DTO; поля DTO маппятся на `fields` виджета
- **Service** — обычно `VersionAwareResponseService`; generic-параметр указывает на DTO
- **MetaBuilder** — построение meta на бэкенде (`buildIndependentMeta`, `buildRowDependentMeta`)
- **Controller** — точка входа; содержит enum бизнес-компонентов (см. env ниже)
- **Actions** — действия со scope BC / RECORD / MASS; на бэкенде метод `getActions`,
  в meta — `actionGroups.include`, `preAction`, `autoSaveBefore`
- **DrillDown** — переходы между виджетами/экранами (поле `drillDown`)

Документация: https://doc.cxbox.org/
Полезные разделы: Widget types, Field types, Widget property (Show condition),
Navigation (Screen/View), Repository elements (Entity/DTO/Service/MetaBuilder),
Features → Actions и DrillDown, Advanced customization (Validation, Filtration).

Версии: cxbox v1.0.0 … v3.0.1, плагин CXBox 1.7.x … 2.0.4. У проекта-потребителя
версия может отличаться — учитывай при работе с meta-контрактом.

## Конвейер: meta → страницы Confluence

1. `ui/panels/ModuleSelectionPanel.kt` — выбор модуля
2. `ui/panels/ScanMetaFilesPanel.kt` — поиск `meta/**/*.json` в resources
3. `model/MetaFileParser.kt` + `model/MetaModels.kt` — разбор screen/view/widget
4. `model/ServiceActionAnalysisService.kt` — PSI: BC → service impl → DTO → actions
5. `util/ConfluenceMarkupGenerator.kt` — иерархия страниц:
   MAIN → CATEGORY_{SCREENS,VIEWS,WIDGETS} → SCREEN_/VIEW_/WIDGET_<имя>
6. `util/CXBoxHrefUtils.kt` — ссылки на doc.cxbox.org по типу виджета/поля.
   При добавлении типа обновляй и парсер, и карту ссылок; шаблон URL —
   `https://doc.cxbox.org/widget/type/<name>/<name>/`
7. `services/ConfluenceService.kt` — REST Confluence: CQL-поиск страниц, create/update,
   контроль версий, счётчики created/updated/skipped/failed
8. `settings/ConfluenceSettings.kt` — параметры Confluence (`PersistentStateComponent`)
   + токен в PasswordSafe
9. `settings/CxboxSettings.kt` — параметры проекта-потребителя CXBox

## Команды

| Задача | Команда | Время |
|---|---|---|
| Компиляция | `./gradlew compileKotlin` | ~15 с |
| Тесты | `./gradlew test` | ~30 с, JDK 21 toolchain |
| Проверка плагина | `./gradlew verifyPlugin` | минуты, качает IDE |
| Песочница | `./gradlew runIde` | GUI, визуально агентом не проверяется |

Начинай с `compileKotlin`, затем `test`. Тестов на бизнес-логику нет
(`MyPluginTest` — шаблонный); новые — `BasePlatformTestCase`, данные в `src/test/testData`.

## Переменные окружения

Локальные значения — в `.env` (gitignored, образец `.env.example`). Задачи `runIde`
и `test` подхватывают файл автоматически. Окружение перекрывает сохранённые настройки.

| Переменная | Назначение |
|---|---|
| `CXBOX_CONFLUENCE_URL` | базовый URL Confluence |
| `CXBOX_CONFLUENCE_TOKEN` | PAT (bearer) или API-токен Cloud (basic) |
| `CXBOX_CONFLUENCE_SPACE_KEY` | ключ пространства для публикации |
| `CXBOX_CONFLUENCE_AUTH` | `bearer` (по умолчанию) или `basic` |
| `CXBOX_CONFLUENCE_EMAIL` | только для режима `basic` |
| `CXBOX_CONFLUENCE_PARENT_PAGE_ID` | необязательный ID родительской страницы |
| `CXBOX_REST_CONTROLLER_CLASS` | FQN класса-контроллера CXBox с enum бизнес-компонентов |

`CXBOX_REST_CONTROLLER_CLASS` обязателен для построения связи BC → service → DTO → actions.
Без него раздел действий в документации будет пустым. Пример:
`org.example.app.controller.MyRestController`.

## Окружение сборки

- JDK 21 через `jvmToolchain(21)`; Gradle 9.0.0 wrapper.
- В `gradle.properties` локальный прокси 127.0.0.1:10808 и отключённый configuration-cache.
  Это локальная настройка — НЕ коммить: в CI (ubuntu-latest) прокси нет, сборка упадёт.
  Перед коммитом: `git checkout gradle.properties`.

## Безопасность (критично)

- НИКОГДА не помещай в исходники токены, email, внутренние URL, space key, FQN классов
  конкретного заказчика. В этом репозитории такое уже приводило к утечке API-токена
  в публичную историю.
- Секреты — только `.env` или PasswordSafe через `ConfluenceSettings`.
- Не ослабляй `.gitignore`: `.env*`, `!.env.example`.
- Значения по умолчанию в UI — пустые плейсхолдеры, не реальные данные.
- Не пушить ветки `tainted-main`, `backup-before-rewrite` и тег `backup-tainted-init`:
  они содержат старую историю с утёкшим токеном.

## Конвенции платформы

- Сервисы — `@Service(Service.Level.PROJECT)`; состояние — `PersistentStateComponent` + `@State`.
- PSI/VFS-доступ — в `ReadAction`; длительные операции — `ProgressManager`, не в EDT.
- Потоки и слушатели освобождать через `Disposable` (в `ConfluencePanel.dispose()`
  сейчас никто не вызывает — известный долг).
- JSON — gson и безопасные extension-функции вместо `!!`.
- UI-строки — в `messages/MyBundle.properties`.

## Известные ограничения (согласовывать, а не менять молча)

- `ConfluenceMarkupGenerator` — `object` с изменяемым состоянием (`baseUrl`, `spaceKey`),
  конфигурируется в начале генерации.
- Поиск реализации сервиса (`findImplClass`) опирается на соглашение об имени `<Interface>Impl`
  и на отсутствие интерфейса как fallback — хрупко на нестандартных проектах.
- Ветки Dependabot из старой истории не мержить: вернут утёкшую историю.

## Definition of done

1. `./gradlew compileKotlin` — без ошибок и новых warning.
2. `./gradlew test` — зелёный.
3. Новый сервис/состояние зарегистрированы корректно.
4. В diff нет секретов и данных заказчика: `git diff | Select-String "token|password|email|crm"`.
5. Изменения meta-контракта отражены и в парсере, и в `CXBoxHrefUtils`.

## Не делать без явной просьбы

- Не менять `platformVersion` / `sinceBuild`.
- Не коммитить `gradle.properties` с прокси.
- Не переписывать `ConfluenceMarkupGenerator` на DI-архитектуру «заодно».
- Не трогать `.idea/`.
