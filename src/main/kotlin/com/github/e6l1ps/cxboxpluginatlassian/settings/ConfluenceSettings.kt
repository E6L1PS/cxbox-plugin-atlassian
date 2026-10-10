package com.github.e6l1ps.cxboxpluginatlassian.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Настройки подключения к Confluence для текущего проекта.
 *
 * Источники значений, по убыванию приоритета:
 *
 * 1. Переменные окружения `CXBOX_CONFLUENCE_*` — для локальной разработки и тестирования.
 *    Их удобно держать в файле `.env` в корне проекта (он в `.gitignore`, образец —
 *    `.env.example`): `./gradlew runIde` и `./gradlew test` подхватывают его автоматически.
 *    Окружение перекрывает сохранённые значения, поэтому можно гонять плагин против
 *    тестового пространства, не меняя настройки проекта.
 * 2. Персистентные настройки проекта: несекретные значения — в `.idea/cxbox-atlassian.xml`,
 *    API-токен — в PasswordSafe (системный keychain или KeePass в зависимости от настроек IDE).
 *    Токен в файл состояния не попадает никогда.
 */
@State(
    name = "CxboxConfluenceSettings",
    storages = [Storage("cxbox-atlassian.xml")],
)
@Service(Service.Level.PROJECT)
class ConfluenceSettings : PersistentStateComponent<ConfluenceSettings.State> {

    data class State(
        var baseUrl: String = "",
        var email: String = "",
        var spaceKey: String = "",
        var parentPageId: String = "",
        var authMode: String = ConfluenceAuthMode.BEARER.name,
    )

    private var myState = State()

    private val environment: Map<String, String> = System.getenv()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** Базовый URL Confluence без завершающего слэша, например `https://confluence.example.com`. */
    var baseUrl: String
        get() = env(ENV_BASE_URL).ifEmpty { myState.baseUrl }
        set(value) {
            myState.baseUrl = value.trim().removeSuffix("/")
        }

    /**
     * Email пользователя. Используется **только** в режиме [ConfluenceAuthMode.BASIC]
     * (`email:apiToken`), который нужен Confluence Cloud. В режиме по умолчанию
     * [ConfluenceAuthMode.BEARER] значение игнорируется.
     */
    var email: String
        get() = env(ENV_EMAIL).ifEmpty { myState.email }
        set(value) {
            myState.email = value.trim()
        }

    /**
     * Режим аутентификации.
     *
     * По умолчанию [ConfluenceAuthMode.BEARER] — историческое поведение плагина
     * (`Authorization: Bearer <token>`), оно работает с Personal Access Token
     * Confluence Server/Data Center.
     *
     * [ConfluenceAuthMode.BASIC] (`Basic base64(email:token)`) включается явно —
     * это способ аутентификации для Confluence Cloud.
     */
    var authMode: ConfluenceAuthMode
        get() = when (env(ENV_AUTH).lowercase()) {
            "basic", "cloud" -> ConfluenceAuthMode.BASIC
            "bearer", "pat" -> ConfluenceAuthMode.BEARER
            else -> ConfluenceAuthMode.entries
                .firstOrNull { it.name.equals(myState.authMode.trim(), ignoreCase = true) }
                ?: ConfluenceAuthMode.BEARER
        }
        set(value) {
            myState.authMode = value.name
        }

    var spaceKey: String
        get() = env(ENV_SPACE_KEY).ifEmpty { myState.spaceKey }
        set(value) {
            myState.spaceKey = value.trim()
        }

    /** ID родительской страницы; пустая строка означает «создавать в корне пространства». */
    var parentPageId: String
        get() = env(ENV_PARENT_PAGE_ID).ifEmpty { myState.parentPageId }
        set(value) {
            myState.parentPageId = value.trim()
        }

    var apiToken: String
        get() = env(ENV_API_TOKEN).ifEmpty {
            PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()
        }
        set(value) {
            val token = value.trim()
            PasswordSafe.instance.setPassword(credentialAttributes(), token.takeIf { it.isNotEmpty() })
        }

    fun isConfigured(): Boolean =
        baseUrl.isNotEmpty() && spaceKey.isNotEmpty() && apiToken.isNotEmpty()

    /** true, если хотя бы одно значение пришло из переменных окружения (локальная разработка). */
    fun isUsingEnvironment(): Boolean = ENV_NAMES.any { env(it).isNotEmpty() }

    private fun env(name: String): String = environment[name]?.trim().orEmpty()

    private fun credentialAttributes() = CredentialAttributes(SERVICE_NAME, USER_NAME)

    companion object {
        /** Базовый URL Confluence. */
        const val ENV_BASE_URL = "CXBOX_CONFLUENCE_URL"

        /** Email для Confluence Cloud; используется только в режиме `basic`. */
        const val ENV_EMAIL = "CXBOX_CONFLUENCE_EMAIL"

        /** Режим аутентификации: `bearer` (по умолчанию) или `basic`. */
        const val ENV_AUTH = "CXBOX_CONFLUENCE_AUTH"

        /** API-токен (Cloud) или Personal Access Token (Server/DC). */
        const val ENV_API_TOKEN = "CXBOX_CONFLUENCE_TOKEN"

        /** Ключ пространства, в которое публикуется документация. */
        const val ENV_SPACE_KEY = "CXBOX_CONFLUENCE_SPACE_KEY"

        /** Необязательный ID родительской страницы. */
        const val ENV_PARENT_PAGE_ID = "CXBOX_CONFLUENCE_PARENT_PAGE_ID"

        private const val SERVICE_NAME = "cxbox-plugin-atlassian"
        private const val USER_NAME = "confluence-api-token"

        private val ENV_NAMES = listOf(
            ENV_BASE_URL,
            ENV_EMAIL,
            ENV_AUTH,
            ENV_API_TOKEN,
            ENV_SPACE_KEY,
            ENV_PARENT_PAGE_ID,
        )
    }
}

/** Способ передачи токена в Confluence. */
enum class ConfluenceAuthMode {
    /** `Authorization: Bearer <token>` — Personal Access Token, Confluence Server/Data Center. */
    BEARER,

    /** `Authorization: Basic base64(email:token)` — API-токен Confluence Cloud. */
    BASIC,
}
