package com.jarvis.gensoftlab.util

object JarvisToolRegistry {
    val declarations: List<Map<String, Any>> = listOf(
        declaration("open_app", "Open an installed Android application by visible name", mapOf(
            "app_name" to property("string", "Application label")
        ), listOf("app_name")),
        declaration("list_apps", "List launchable installed applications", emptyMap(), emptyList()),
        declaration("get_current_app", "Get the foreground application and compact visible UI context", emptyMap(), emptyList()),
        declaration("get_current_package", "Get the foreground package name", emptyMap(), emptyList()),
        declaration("get_screen_context", "Inspect visible accessibility UI nodes", emptyMap(), emptyList()),
        declaration("get_ui_tree", "Capture the complete visible accessibility UI tree", emptyMap(), emptyList()),
        declaration("get_screen_state", "Get a compact screen state summary", emptyMap(), emptyList()),
        declaration("find_text", "Find visible nodes by text or content description", mapOf("text" to property("string", "Target text")), listOf("text")),
        declaration("find_view", "Find visible nodes by view id", mapOf("view_id" to property("string", "View id")), listOf("view_id")),
        declaration("find_clickable", "Find visible clickable nodes", emptyMap(), emptyList()),
        declaration("find_editable", "Find visible editable nodes", emptyMap(), emptyList()),
        declaration("get_focused_element", "Get the currently focused visible element", emptyMap(), emptyList()),
        declaration("wait_for_element", "Wait for visible text or content description", mapOf(
            "text" to property("string", "Visible target text"),
        ), listOf("text")),
        declaration("click_text", "Click a visible text or content description", mapOf(
            "text" to property("string", "Visible target text")
        ), listOf("text")),
        declaration("click_node", "Click a visible node by exact text", mapOf("text" to property("string", "Visible target text")), listOf("text")),
        declaration("scroll", "Scroll the active screen", mapOf(
            "direction" to property("string", "up or down")
        ), listOf("direction")),
        declaration("type_text", "Type into the focused editable field", mapOf(
            "text" to property("string", "Text to enter"),
            "press_enter" to property("boolean", "Whether submission is requested")
        ), listOf("text")),
        declaration("set_text", "Set text in a visible editable field", mapOf(
            "target_text" to property("string", "Existing field text"),
            "text" to property("string", "Replacement text")
        ), listOf("target_text", "text")),
        declaration("focus_field", "Focus a visible editable field", mapOf(
            "target_text" to property("string", "Optional field text")
        ), emptyList()),
        declaration("tap", "Tap screen coordinates", mapOf("x" to property("number", "X coordinate"), "y" to property("number", "Y coordinate")), listOf("x", "y")),
        declaration("double_tap", "Double tap screen coordinates", mapOf("x" to property("number", "X coordinate"), "y" to property("number", "Y coordinate")), listOf("x", "y")),
        declaration("long_press", "Long press screen coordinates", mapOf("x" to property("number", "X coordinate"), "y" to property("number", "Y coordinate"), "duration_ms" to property("integer", "Duration")), listOf("x", "y")),
        declaration("swipe", "Swipe between screen coordinates", mapOf("x1" to property("number", "Start X"), "y1" to property("number", "Start Y"), "x2" to property("number", "End X"), "y2" to property("number", "End Y")), listOf("x1", "y1", "x2", "y2")),
        declaration("drag", "Drag between screen coordinates", mapOf("x1" to property("number", "Start X"), "y1" to property("number", "Start Y"), "x2" to property("number", "End X"), "y2" to property("number", "End Y")), listOf("x1", "y1", "x2", "y2")),
        declaration("wait_for_ui_change", "Wait until the visible accessibility UI changes", mapOf("timeout_ms" to property("integer", "Timeout")), emptyList()),
        declaration("press_back", "Navigate back through AccessibilityService", emptyMap(), emptyList()),
        declaration("press_home", "Navigate home through AccessibilityService", emptyMap(), emptyList()),
        declaration("open_recents", "Open recent apps through AccessibilityService", emptyMap(), emptyList()),
        declaration("back", "Navigate back", emptyMap(), emptyList()),
        declaration("home", "Navigate home", emptyMap(), emptyList()),
        declaration("recents", "Open recent apps", emptyMap(), emptyList()),
        declaration("notifications", "Open notifications", emptyMap(), emptyList()),
        declaration("quick_settings", "Open quick settings", emptyMap(), emptyList()),
        declaration("open_notifications", "Open Android notification panel", emptyMap(), emptyList()),
        declaration("open_quick_settings", "Open Android quick settings panel", emptyMap(), emptyList()),
        declaration("play_media", "Attempt a safe media play action when a supported media session is active", emptyMap(), emptyList()),
        declaration("pause_media", "Attempt a safe media pause action when a supported media session is active", emptyMap(), emptyList()),
        declaration("toggle_media", "Toggle between play and pause when the current media state is known", emptyMap(), emptyList()),
        declaration("next_track", "Request next media item when a supported media session is active", emptyMap(), emptyList()),
        declaration("previous_track", "Request previous media item when a supported media session is active", emptyMap(), emptyList()),
        declaration("clear_text", "Clear current editable field when supported by Android accessibility", mapOf(
            "text" to property("string", "Target field text or blank")
        ), listOf("text")),
        declaration("press_enter", "Submit the active input target when supported by Android accessibility", emptyMap(), emptyList()),
        declaration("youtube_search", "Search YouTube for a query", mapOf(
            "query" to property("string", "YouTube query")
        ), listOf("query")),
        declaration("web_search", "Search the web for a query", mapOf(
            "query" to property("string", "Search query")
        ), listOf("query")),
        declaration("open_browser_search", "Open the browser with a search query for a target term", mapOf(
            "query" to property("string", "Search query")
        ), listOf("query")),
        declaration("search_in_app", "Type a query into the current search field when one is visible", mapOf(
            "query" to property("string", "Search query")
        ), listOf("query")),
        declaration("open_settings", "Open an Android Settings page", mapOf(
            "page" to property("string", "settings, wifi, bluetooth, display, sound, battery, notifications")
        ), listOf("page")),
        declaration("set_brightness", "Set screen brightness percentage when Android write-settings access is granted", mapOf(
            "percent" to property("number", "Brightness from 0 to 100")
        ), listOf("percent")),
        declaration("call_phone", "Place or prepare a phone call", mapOf(
            "phone_number" to property("string", "Phone number to call"),
            "direct" to property("boolean", "True to call directly, false to open dialer")
        ), listOf("phone_number")),
        declaration("search_contacts", "Search for contacts by name or phone number", mapOf(
            "query" to property("string", "Search keyword (name or number)")
        ), listOf("query")),
        declaration("set_alarm", "Set a new system alarm", mapOf(
            "hour" to property("integer", "Hour of the day (0-23)"),
            "minute" to property("integer", "Minute of the hour (0-59)"),
            "message" to property("string", "Optional label or message for the alarm"),
            "skip_ui" to property("boolean", "Optional flag to create alarm without showing system UI")
        ), listOf("hour", "minute")),
        declaration("set_timer", "Set a new system timer countdown", mapOf(
            "seconds" to property("integer", "Duration in seconds (> 0)"),
            "message" to property("string", "Optional label or message for the timer")
        ), listOf("seconds"))
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