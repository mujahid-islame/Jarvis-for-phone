package com.jarvis.assistant.util

object JarvisToolRegistry {
    val declarations: List<Map<String, Any>> = listOf(
        declaration("open_app", "Open an installed Android application by visible name", mapOf(
            "app_name" to property("string", "Application label")
        ), listOf("app_name")),
        declaration("list_apps", "List launchable installed applications", emptyMap(), emptyList()),
        declaration("get_current_app", "Get the foreground application and compact visible UI context", emptyMap(), emptyList()),
        declaration("get_screen_context", "Inspect visible accessibility UI nodes", emptyMap(), emptyList()),
        declaration("click_text", "Click a visible text or content description", mapOf(
            "text" to property("string", "Visible target text")
        ), listOf("text")),
        declaration("scroll", "Scroll the active screen", mapOf(
            "direction" to property("string", "up or down")
        ), listOf("direction")),
        declaration("type_text", "Type into the focused editable field", mapOf(
            "text" to property("string", "Text to enter"),
            "press_enter" to property("boolean", "Whether submission is requested")
        ), listOf("text")),
        declaration("back", "Navigate back", emptyMap(), emptyList()),
        declaration("home", "Navigate home", emptyMap(), emptyList()),
        declaration("recents", "Open recent apps", emptyMap(), emptyList()),
        declaration("notifications", "Open notifications", emptyMap(), emptyList()),
        declaration("quick_settings", "Open quick settings", emptyMap(), emptyList()),
        declaration("youtube_search", "Search YouTube for a query", mapOf(
            "query" to property("string", "YouTube query")
        ), listOf("query")),
        declaration("web_search", "Search the web for a query", mapOf(
            "query" to property("string", "Search query")
        ), listOf("query")),
        declaration("open_settings", "Open an Android Settings page", mapOf(
            "page" to property("string", "settings, wifi, bluetooth, display, sound, battery, notifications")
        ), listOf("page"))
    )

    private fun property(type: String, description: String): Map<String, String> = mapOf(
        "type" to type.uppercase(),
        "description" to description
    )

    private fun declaration(
        name: String,
        description: String,
        properties: Map<String, Map<String, String>>,
        required: List<String>
    ): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "parameters" to mapOf(
            "type" to "OBJECT",
            "properties" to properties,
            "required" to required
        )
    )
}