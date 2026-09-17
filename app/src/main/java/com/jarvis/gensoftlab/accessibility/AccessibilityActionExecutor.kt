package com.jarvis.gensoftlab.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.gensoftlab.tools.ToolResult
import com.google.gson.Gson

object AccessibilityActionExecutor {

    fun click(node: AccessibilityNodeInfo, actionId: String?): ToolResult {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        
        return if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            ToolResult(success = true, tool = "click_node", actionId = actionId, message = "Click action dispatched.")
        } else {
            ToolResult(success = false, tool = "click_node", actionId = actionId, error = "EXECUTION_FAILED", message = "Node is not clickable or action failed.")
        }
    }

    fun setText(node: AccessibilityNodeInfo, text: String, actionId: String?): ToolResult {
        if (!node.isEditable) {
            return ToolResult(success = false, tool = "set_text", actionId = actionId, error = "INVALID_ARGUMENT", message = "Node is not editable.")
        }
        
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            ToolResult(success = true, tool = "set_text", actionId = actionId, message = "Text set successfully.")
        } else {
            ToolResult(success = false, tool = "set_text", actionId = actionId, error = "EXECUTION_FAILED", message = "Failed to set text.")
        }
    }

    fun scroll(node: AccessibilityNodeInfo, forward: Boolean, actionId: String?): ToolResult {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isScrollable) {
            target = target.parent
        }

        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        
        return if (target?.performAction(action) == true) {
            ToolResult(success = true, tool = "scroll", actionId = actionId, message = "Scroll dispatched.")
        } else {
            ToolResult(success = false, tool = "scroll", actionId = actionId, error = "EXECUTION_FAILED", message = "No scrollable container found.")
        }
    }
}
