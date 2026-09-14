package com.jarvis.assistant.util

import java.util.Locale

enum class RequestRoute {
    CHAT,
    SIMPLE_TOOL,
    PHONE_TASK,
    CANCEL_TASK
}

data class RoutedPhoneTask(
    val route: RequestRoute,
    val goal: TaskGoal? = null
)

object PhoneTaskRouter {
    private const val PHONE_TASK_CONFIDENCE_THRESHOLD = 0.75

    fun route(text: String): RoutedPhoneTask {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase(Locale.getDefault())
        if (lower in setOf("stop", "cancel", "cancel this", "থামো", "বন্ধ করো", "কাজ বন্ধ করো")) {
            return RoutedPhoneTask(RequestRoute.CANCEL_TASK)
        }

        val command = AssistantCommandParser.parse(normalized)
        val phoneSignal = listOf(
            "open ", "launch ", "খোলো", "খুলে দাও", "চালু কর", "scroll", "স্ক্রল",
            "click", "ক্লিক", "type ", "লিখে দাও", "go back", "go home", "হোমে যাও",
            "settings", "সেটিংস", "youtube", "ইউটিউব", "whatsapp", "হোয়াটসঅ্যাপ",
            "facebook", "spotify", "calculator", "camera", "chrome", "play music", "pause music",
            "battery", "current app", "phone status", "status", "ফোন স্ট্যাটাস", "ব্যাটারি", "বর্তমান অ্যাপ"
        ).any { lower.contains(it) }

        if (!phoneSignal) return RoutedPhoneTask(RequestRoute.CHAT)
        if (command != null && !isComplex(normalized)) return RoutedPhoneTask(RequestRoute.SIMPLE_TOOL)

        val targetApp = when {
            lower.contains("youtube") || lower.contains("ইউটিউব") -> "YouTube"
            lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ") || lower.contains("হোয়াটসঅ্যাপ") -> "WhatsApp"
            lower.contains("chrome") || lower.contains("ক্রোম") -> "Chrome"
            lower.contains("facebook") || lower.contains("ফেসবুক") -> "Facebook"
            lower.contains("spotify") || lower.contains("স্পটিফাই") -> "Spotify"
            lower.contains("calculator") || lower.contains("ক্যালকুলেটর") || lower.contains("ক্যালকুলেট") -> "Calculator"
            lower.contains("camera") || lower.contains("ক্যামেরা") -> "Camera"
            lower.contains("settings") || lower.contains("সেটিংস") -> "Settings"
            else -> null
        }
        val constraints = buildList {
            if (lower.contains("search") || lower.contains("সার্চ")) add("Complete the requested search")
            if (lower.contains("first") || lower.contains("প্রথম")) add("Prefer the first relevant result")
            if (lower.contains("send") || lower.contains("পাঠাও")) add("Require confirmation before sending external content")
            if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("current app") || lower.contains("বর্তমান অ্যাপ") || lower.contains("phone status") || lower.contains("ফোন স্ট্যাটাস")) add("Read the actual device state before answering")
        }
        val criteria = buildList {
            if (targetApp != null) add("$targetApp is open")
            if (lower.contains("play") || lower.contains("চালাও")) add("A player or playable result is visible")
            if (lower.contains("search") || lower.contains("সার্চ")) add("Search results are visible")
            if (lower.contains("settings") || lower.contains("সেটিংস")) add("Requested settings page is visible")
            if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("current app") || lower.contains("বর্তমান অ্যাপ") || lower.contains("phone status") || lower.contains("ফোন স্ট্যাটাস")) add("The current device context is known")
        }
        return RoutedPhoneTask(
            route = RequestRoute.PHONE_TASK,
            goal = TaskGoal(
                objective = normalized,
                targetApp = targetApp,
                constraints = constraints,
                successCriteria = criteria
            )
        )
    }

    fun confidence(goal: TaskGoal): Double {
        var score = 0.75
        if (goal.objective.length >= 12) score += 0.10
        if (goal.targetApp != null) score += 0.05
        if (goal.successCriteria.isNotEmpty()) score += 0.05
        return score.coerceAtMost(0.99)
    }

    fun isConfident(goal: TaskGoal): Boolean = confidence(goal) >= PHONE_TASK_CONFIDENCE_THRESHOLD

    private fun isComplex(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return listOf(" and ", " then ", "তারপর", "এবং", "খুলে", "search করে", "চালিয়ে", "পাঠাও",
            "battery", "current app", "phone status", "settings", "সেটিংস", "facebook", "spotify", "calculator", "camera")
            .any { lower.contains(it) }
    }
}
