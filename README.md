# cxbox-plugin-atlassian

![Build](https://github.com/E6L1PS/cxbox-plugin-atlassian/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [pluginGroup](./gradle.properties) and [pluginName](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml) and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.
- [ ] Configure the [CODECOV_TOKEN](https://docs.codecov.com/docs/quick-start) secret for automated test coverage reports on PRs

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections. 
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "cxbox-plugin-atlassian"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/E6L1PS/cxbox-plugin-atlassian/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## Локальная разработка

Секреты в репозиторий не коммитятся. Для локальной разработки и тестов скопируйте
образец и заполните значения:

```bash
cp .env.example .env
```

`.env` есть в `.gitignore`. Задачи `./gradlew runIde` (включая `runIdeForUiTests`)
и `./gradlew test` читают этот файл и автоматически пробрасывают переменные в
запускаемый процесс IDE или тестов.

| Переменная | Обяз. | Назначение |
|---|---|---|
| `CXBOX_CONFLUENCE_URL` | да | Базовый URL Confluence, без завершающего слэша |
| `CXBOX_CONFLUENCE_TOKEN` | да | Personal Access Token (bearer) или API-токен Cloud (basic) |
| `CXBOX_CONFLUENCE_SPACE_KEY` | да | Ключ пространства, в которое публикуется документация |
| `CXBOX_CONFLUENCE_AUTH` | нет | `bearer` (по умолчанию) или `basic` |
| `CXBOX_CONFLUENCE_EMAIL` | нет | Только для режима `basic`; в `bearer` игнорируется |
| `CXBOX_CONFLUENCE_PARENT_PAGE_ID` | нет | ID родительской страницы |
| `CXBOX_REST_CONTROLLER_CLASS` | да | FQN класса-контроллера CXBox с enum бизнес-компонентов (BC) |

`CXBOX_REST_CONTROLLER_CLASS` задаёт точку входа для построения связи
BC → service → DTO → actions: плагин берёт enum бизнес-компонентов из этого класса.
Раньше значение было жёстко зашито в коде под конкретный проект — теперь задаётся
переменной окружения или настройкой проекта. Без него раздел действий в документации
остаётся пустым.

Способ аутентификации задаётся явно и по умолчанию — **Bearer**:

- `bearer` → `Authorization: Bearer <token>` — Personal Access Token Confluence
  Server/Data Center;
- `basic` → `Authorization: Basic base64(email:token)` — API-токен Confluence Cloud;
  требует заполненного email.

Тот же выбор доступен в диалоге настроек плагина (поле `Authentication`); значение
из окружения имеет приоритет.

Переменные окружения **перекрывают** значения, сохранённые в настройках проекта
(`.idea/cxbox-atlassian.xml`), поэтому удобно гонять плагин против тестового
пространства, не меняя основную конфигурацию. Токен, введённый вручную в диалоге
настроек, хранится в PasswordSafe — системном keychain или KeePass — и в файлы
проекта не попадает.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
