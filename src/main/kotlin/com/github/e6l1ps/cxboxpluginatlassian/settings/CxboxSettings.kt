package com.github.e6l1ps.cxboxpluginatlassian.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Настройки проекта-потребителя CXBox (в отличие от [ConfluenceSettings],
 * которые описывают подключение к Confluence).
 *
 * Несекретные значения хранятся в `.idea/cxbox-atlassian.xml`.
 * Переменные окружения перекрывают сохранённые значения — удобно для локальной
 * разработки и тестов (см. `.env.example`).
 */
@State(
    name = "CxboxProjectSettings",
    storages = [Storage("cxbox-atlassian.xml")],
)
@Service(Service.Level.PROJECT)
class CxboxSettings : PersistentStateComponent<CxboxSettings.State> {

    data class State(
        var restControllerClass: String = "",
    )

    private var myState = State()

    private val environment: Map<String, String> = System.getenv()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * FQN класса-контроллера CXBox, в котором объявлен enum бизнес-компонентов.
     * По нему строится связь BC → service → DTO → actions.
     *
     * Раньше это значение было жёстко зашито в коде под конкретный проект;
     * теперь задаётся через [ENV_REST_CONTROLLER_CLASS] или настройку проекта.
     */
    var restControllerClass: String
        get() = env(ENV_REST_CONTROLLER_CLASS).ifEmpty { myState.restControllerClass }
        set(value) {
            myState.restControllerClass = value.trim()
        }

    /** true, если значение пришло из переменных окружения. */
    fun isUsingEnvironment(): Boolean = env(ENV_REST_CONTROLLER_CLASS).isNotEmpty()

    private fun env(name: String): String = environment[name]?.trim().orEmpty()

    companion object {
        /**
         * FQN класса-контроллера с enum бизнес-компонентов,
         * например `org.example.app.controller.MyRestController`.
         */
        const val ENV_REST_CONTROLLER_CLASS = "CXBOX_REST_CONTROLLER_CLASS"
    }
}
