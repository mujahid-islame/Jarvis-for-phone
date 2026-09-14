package com.jarvis.assistant.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Structured goal and one-step planner contract. Raw model reasoning is never stored. */
data class TaskGoal(
    val objective: String,
    val targetApp: String? = null,
    val constraints: List<String> = emptyList(),
    val successCriteria: List<String> = emptyList()
)

enum class PlannerStatus {
    CONTINUE,
    COMPLETED,
    NEED_USER_INPUT,
    FAILED
}

data class PlannerDecision(
    val status: PlannerStatus,
    val action: String? = null,
    val target: String? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val expectedOutcome: String? = null,
    val confidence: Double = 0.0,
    val requiresConfirmation: Boolean = false,
    val question: String? = null
)

data class PlannerValidation(
    val valid: Boolean,
    val message: String,
    val decision: PlannerDecision? = null
)

object PhonePlannerContract {
    private const val MIN_CONFIDENCE = 0.70
    private val allowedActions = setOf(
        "open_app",
        "list_apps",
        "click_text",
        "click_description",
        "click_element",
        "type_text",
        "scroll",
        "wait_for_element",
        "back",
        "home",
        "recents",
        "notifications",
        "quick_settings",
        "youtube_search",
        "web_search",
        "open_browser_search",
        "search_in_app",
        "open_settings",
        "set_brightness"
    )

    private val requiredArguments = mapOf(
        "open_app" to setOf("app_name"),
        "click_text" to setOf("text"),
        "click_description" to setOf("description"),
        "type_text" to setOf("text"),
        "scroll" to setOf("direction"),
        "wait_for_element" to setOf("text"),
        "open_browser_search" to setOf("query"),
        "search_in_app" to setOf("query"),
        "set_brightness" to setOf("percent")
    )

    fun validateFunctionCall(
        name: String,
        arguments: Map<String, Any?>,
        confidence: Double = 1.0
    ): PlannerValidation {
        if (name !in allowedActions) return invalid("এই action অনুমোদিত নয়।")
        if (confidence !in 0.0..1.0) return invalid("Planner confidence অবৈধ।")
        if (confidence < MIN_CONFIDENCE && name !in setOf("get_screen_context", "get_current_app")) {
            return invalid("Planner confidence কম; আগে screen আবার observe করতে হবে।")
        }

        val required = requiredArguments[name].orEmpty()
        val missing = required.filter { arguments[it].asNonBlankString().isNullOrBlank() }
        if (missing.isNotEmpty()) return invalid("Required argument missing: ${missing.joinToString()}")

        if (name == "scroll") {
            val direction = arguments["direction"].asNonBlankString()?.lowercase()
            if (direction !in setOf("up", "down")) return invalid("Scroll direction শুধু up বা down হতে পারে।")
        }
        if (name == "type_text" && arguments["text"].asNonBlankString().isNullOrBlank()) {
            return invalid("Type করার text খালি হতে পারে না।")
        }
        if (name == "set_brightness") {
            val percent = arguments["percent"].asDoubleOrNull()
                ?: return invalid("Brightness percent অবৈধ।")
            if (percent !in 0.0..100.0) return invalid("Brightness 0 থেকে 100 percent-এর মধ্যে হতে হবে।")
        }
        return PlannerValidation(true, "Valid planner action", PlannerDecision(
            status = PlannerStatus.CONTINUE,
            action = name,
            target = arguments["text"].asNonBlankString(),
            arguments = arguments,
            confidence = confidence
        ))
    }

    fun parseDecision(json: String): PlannerValidation {
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val status = root.get("status")?.asString?.uppercase()?.let {
                PlannerStatus.entries.firstOrNull { item -> item.name == it }
            } ?: return invalid("Planner status missing বা invalid।")
            if (status == PlannerStatus.COMPLETED) {
                return PlannerValidation(true, "Task completed decision", PlannerDecision(status))
            }
            if (status == PlannerStatus.NEED_USER_INPUT) {
                val question = root.get("question")?.asString?.trim()
                return if (question.isNullOrBlank()) invalid("Clarification question missing।")
                else PlannerValidation(true, "User clarification required", PlannerDecision(status, question = question))
            }
            val action = root.get("action")?.asString?.trim().orEmpty()
            val arguments = root.getAsJsonObject("arguments")?.toAnyMap().orEmpty()
            val confidence = root.get("confidence")?.asDouble ?: 0.0
            val validation = validateFunctionCall(action, arguments, confidence)
            if (!validation.valid) validation
            else validation.copy(decision = validation.decision?.copy(
                status = status,
                expectedOutcome = root.get("expectedOutcome")?.asString?.trim()
            ))
        }.getOrElse { invalid("Planner output parse করা যায়নি।") }
    }

    fun isAllowedAction(name: String): Boolean = name in allowedActions

    private fun invalid(message: String) = PlannerValidation(false, message)

    private fun Any?.asNonBlankString(): String? = this?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun Any?.asDoubleOrNull(): Double? = when (this) {
        is Number -> toDouble()
        else -> this?.toString()?.toDoubleOrNull()
    }

    private fun JsonObject.toAnyMap(): Map<String, Any?> = entrySet().associate { entry ->
        entry.key to Gson().fromJson<Any?>(entry.value, Any::class.java)
    }
}
