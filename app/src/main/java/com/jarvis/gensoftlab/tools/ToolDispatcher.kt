package com.jarvis.gensoftlab.tools

import com.google.gson.Gson

object ToolDispatcher {
    private val whitelist = setOf(
        "tap", "swipe", "set_text", "clear_text", "get_ui_tree", 
        "capture_screen", "launch_app", "get_current_app", 
        "search_contacts", "call_phone", "set_alarm", "set_timer", 
        "get_battery", "get_location"
    )

    fun dispatch(toolName: String, arguments: Map<String, Any?>, actionId: String?): String {
        if (toolName !in whitelist) {
            val res = ToolResult(
                success = false,
                tool = toolName,
                actionId = actionId,
                error = "UNSUPPORTED",
                message = "Tool is not registered."
            )
            return Gson().toJson(res)
        }
        
        val res = ToolResult(
            success = true,
            tool = toolName,
            actionId = actionId,
            message = "Tool dispatch validated successfully (Phase 0 Foundation placeholder)."
        )
        return Gson().toJson(res)
    }
}
