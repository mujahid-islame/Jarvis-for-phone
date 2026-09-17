package com.jarvis.gensoftlab.tools

import com.google.gson.Gson
import com.jarvis.gensoftlab.accessibility.AccessibilityBridge
import com.jarvis.gensoftlab.accessibility.AccessibilityNodeFinder
import com.jarvis.gensoftlab.accessibility.AccessibilityActionExecutor
import com.jarvis.gensoftlab.accessibility.GestureController
import com.jarvis.gensoftlab.agent.ActionAdmission
import com.jarvis.gensoftlab.agent.ActionIdentity
import com.jarvis.gensoftlab.agent.TaskStateManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

object ToolDispatcher {
    private val whitelist = setOf(
        "get_current_app", "get_current_package", "get_ui_tree", "get_screen_state",
        "find_text", "find_view", "find_clickable", "find_editable", "get_focused_element",
        "click_node", "set_text", "clear_text", "focus_field", "tap", "double_tap",
        "long_press", "swipe", "scroll", "drag", "press_back", "press_home",
        "open_recents", "wait_for_ui_change"
    )

    private val gson = Gson()

    suspend fun dispatch(
        toolName: String,
        arguments: Map<String, Any?>,
        actionId: String?,
        dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): String = withContext(dispatcher) {
        val effectiveActionId = actionId ?: arguments["action_id"]?.toString() ?: UUID.randomUUID().toString()
        val identity = ActionIdentity(
            taskId = arguments["task_id"]?.toString() ?: "tool-session",
            generationId = arguments["generation_id"]?.toString() ?: "generation-1",
            actionId = effectiveActionId
        )
        if (toolName !in whitelist) {
            return@withContext errorResult(toolName, effectiveActionId, "UNSUPPORTED", "Tool is not registered or supported in Phase 1.", identity)
        }

        if (!AccessibilityBridge.isAvailable()) {
            return@withContext errorResult(toolName, effectiveActionId, "SPECIAL_ACCESS_REQUIRED", "Accessibility Service must be enabled.", identity)
        }

        when (val admission = TaskStateManager.admitAction(identity, toolName)) {
            is ActionAdmission.Cached -> return@withContext admission.result
            ActionAdmission.DuplicateInFlight -> return@withContext errorResult(
                toolName, effectiveActionId, "DUPLICATE_ACTION", "The action is already in flight.", identity
            )
            ActionAdmission.Stale -> return@withContext errorResult(
                toolName, effectiveActionId, "STALE_ACTION", "The action belongs to an inactive task generation.", identity
            )
            ActionAdmission.Accepted -> Unit
        }

        val result = try {
            when (toolName) {
                "get_current_app", "get_current_package" -> getCurrentApp(toolName, effectiveActionId)
                "get_ui_tree" -> getUiTree(effectiveActionId)
                "get_screen_state" -> getScreenState(effectiveActionId)
                "find_text" -> findText(arguments, effectiveActionId)
                "find_view" -> findView(arguments, effectiveActionId)
                "find_clickable" -> findNodes("find_clickable", effectiveActionId) { it.isClickable }
                "find_editable" -> findNodes("find_editable", effectiveActionId) { it.isEditable }
                "get_focused_element" -> findNodes("get_focused_element", effectiveActionId) { it.isFocused }
                "press_back" -> performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "press_back", effectiveActionId)
                "press_home" -> performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "press_home", effectiveActionId)
                "open_recents" -> performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, "open_recents", effectiveActionId)
                "tap" -> tap(arguments, effectiveActionId)
                "double_tap" -> doubleTap(arguments, effectiveActionId)
                "long_press" -> longPress(arguments, effectiveActionId)
                "swipe" -> swipe(arguments, effectiveActionId)
                "drag" -> drag(arguments, effectiveActionId)
                "scroll" -> scroll(arguments, effectiveActionId)
                "set_text" -> setText(arguments, effectiveActionId, clear = false)
                "clear_text" -> setText(arguments, effectiveActionId, clear = true)
                "focus_field" -> focusField(arguments, effectiveActionId)
                "click_node" -> clickNode(arguments, effectiveActionId)
                "wait_for_ui_change" -> waitForUiChange(arguments, effectiveActionId)
                else -> ToolResult(success = false, tool = toolName, actionId = effectiveActionId, error = "UNSUPPORTED", message = "Not implemented.")
            }
        } catch (e: Exception) {
            ToolResult(success = false, tool = toolName, actionId = effectiveActionId, error = "EXECUTION_FAILED", message = e.message)
        }

        val enriched = result.copy(
            actionId = effectiveActionId,
            taskId = identity.taskId,
            generationId = identity.generationId
        )
        val json = gson.toJson(enriched)
        TaskStateManager.completeAction(identity, json, enriched.success)
        json
    }

    private fun getCurrentApp(tool: String, actionId: String?): ToolResult {
        val packageName = AccessibilityBridge.getCurrentPackage()
        return ToolResult(
            success = true,
            tool = tool,
            actionId = actionId,
            data = mapOf("packageName" to packageName)
        )
    }

    private fun findText(args: Map<String, Any?>, actionId: String?): ToolResult {
        val text = args["text"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return invalidArg("find_text", actionId, "text missing")
        return findNodes("find_text", actionId) { node ->
            node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
        }
    }

    private fun findView(args: Map<String, Any?>, actionId: String?): ToolResult {
        val id = args["view_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return invalidArg("find_view", actionId, "view_id missing")
        return findNodes("find_view", actionId) { it.viewIdResourceName?.contains(id, ignoreCase = true) == true }
    }

    private fun findNodes(tool: String, actionId: String?, predicate: (AccessibilityNodeInfo) -> Boolean): ToolResult {
        val root = AccessibilityBridge.getRootNode()
            ?: return ToolResult(false, tool, actionId, "NOT_READY", "Root node not available.")
        val nodes = AccessibilityNodeFinder.findNodes(root, predicate)
        val data = nodes.map { node ->
            val element = AccessibilityNodeFinder.createUiElement(node)
            node.recycle()
            element
        }
        return ToolResult(true, tool, actionId, data = data)
    }

    private fun getUiTree(actionId: String?): ToolResult {
        val root = AccessibilityBridge.getRootNode() ?: return ToolResult(success = false, tool = "get_ui_tree", actionId = actionId, error = "NOT_READY", message = "Root node not available.")
        val snapshot = AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400) // Placeholder dimensions
        return ToolResult(success = true, tool = "get_ui_tree", actionId = actionId, data = snapshot)
    }

    private fun getScreenState(actionId: String?): ToolResult {
        val root = AccessibilityBridge.getRootNode() ?: return ToolResult(success = false, tool = "get_screen_state", actionId = actionId, error = "NOT_READY", message = "Root node not available.")
        val snapshot = AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400)

        val summary = mapOf(
            "packageName" to snapshot.packageName,
            "focusedElement" to snapshot.focusedElement,
            "elementCount" to snapshot.nodes.size
        )
        return ToolResult(success = true, tool = "get_screen_state", actionId = actionId, data = summary)
    }

    private fun performGlobal(action: Int, tool: String, actionId: String?): ToolResult {
        val success = AccessibilityBridge.performGlobalAction(action)
        return ToolResult(success = success, tool = tool, actionId = actionId, message = if (success) "Action dispatched." else "Action failed.")
    }

    private suspend fun tap(args: Map<String, Any?>, actionId: String?): ToolResult {
        val point = coordinates(args, "tap", actionId) ?: return invalidArg("tap", actionId, "valid coordinates required")
        val service = AccessibilityBridge.getService() ?: return ToolResult(success = false, tool = "tap", actionId = actionId, error = "NOT_READY")
        return GestureController.tap(service, point.first, point.second, actionId)
    }

    private suspend fun doubleTap(args: Map<String, Any?>, actionId: String?): ToolResult {
        val point = coordinates(args, "double_tap", actionId) ?: return invalidArg("double_tap", actionId, "valid coordinates required")
        val service = AccessibilityBridge.getService() ?: return ToolResult(false, "double_tap", actionId, "NOT_READY")
        return GestureController.doubleTap(service, point.first, point.second, actionId)
    }

    private suspend fun longPress(args: Map<String, Any?>, actionId: String?): ToolResult {
        val point = coordinates(args, "long_press", actionId) ?: return invalidArg("long_press", actionId, "valid coordinates required")
        val service = AccessibilityBridge.getService() ?: return ToolResult(false, "long_press", actionId, "NOT_READY")
        val duration = args["duration_ms"]?.toString()?.toLongOrNull() ?: 800L
        return GestureController.longPress(service, point.first, point.second, duration, actionId)
    }

    private suspend fun swipe(args: Map<String, Any?>, actionId: String?): ToolResult {
        val values = floatCoordinates(args, "swipe", actionId) ?: return invalidArg("swipe", actionId, "valid coordinates required")
        val service = AccessibilityBridge.getService() ?: return ToolResult(false, "swipe", actionId, "NOT_READY")
        return GestureController.swipe(service, values[0], values[1], values[2], values[3], args["duration_ms"]?.toString()?.toLongOrNull() ?: 400L, actionId)
    }

    private suspend fun drag(args: Map<String, Any?>, actionId: String?): ToolResult {
        val values = floatCoordinates(args, "drag", actionId) ?: return invalidArg("drag", actionId, "valid coordinates required")
        val service = AccessibilityBridge.getService() ?: return ToolResult(false, "drag", actionId, "NOT_READY")
        return GestureController.drag(service, values[0], values[1], values[2], values[3], args["duration_ms"]?.toString()?.toLongOrNull() ?: 600L, actionId)
    }

    private suspend fun scroll(args: Map<String, Any?>, actionId: String?): ToolResult {
        val forward = when {
            args.containsKey("forward") -> args["forward"]?.toString()?.toBooleanStrictOrNull()
            args["direction"]?.toString()?.equals("down", ignoreCase = true) == true -> true
            args["direction"]?.toString()?.equals("up", ignoreCase = true) == true -> false
            else -> null
        } ?: return invalidArg("scroll", actionId, "direction must be up/down or forward must be true/false")

        val before = AccessibilityBridge.getRootNode()?.let { root ->
            AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400).fingerprint()
        } ?: return ToolResult(false, "scroll", actionId, "NOT_READY", "Root node not available.")
        val dispatched = com.jarvis.gensoftlab.accessibility.JarvisAccessibilityService.scroll(forward, actionId)
        if (!dispatched.success) return dispatched

        delay(250L)
        val after = AccessibilityBridge.getRootNode()?.let { root ->
            AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400).fingerprint()
        } ?: return ToolResult(false, "scroll", actionId, "NOT_READY", "Root node unavailable after scroll.")
        return if (before != after) {
            dispatched.copy(message = "Scroll dispatched and UI changed.", status = "ACTION_VERIFIED")
        } else {
            ToolResult(false, "scroll", actionId, "EXECUTION_FAILED", "Scroll action was dispatched but the UI did not change.", status = "ACTION_FAILED")
        }
    }

    private fun setText(args: Map<String, Any?>, actionId: String?, clear: Boolean): ToolResult {
        val tool = if (clear) "clear_text" else "set_text"
        val text = if (clear) "" else args["text"]?.toString() ?: return invalidArg(tool, actionId, "text missing")
        val targetText = args["target_text"]?.toString() ?: return invalidArg(tool, actionId, "target_text missing")

        val root = AccessibilityBridge.getRootNode() ?: return ToolResult(success = false, tool = "set_text", actionId = actionId, error = "NOT_READY")
        val nodes = AccessibilityNodeFinder.findByText(root, targetText, exact = true)
        if (nodes.isEmpty()) return ToolResult(false, tool, actionId, "ELEMENT_NOT_FOUND")
        if (nodes.size > 1) return ToolResult(false, tool, actionId, "AMBIGUOUS_TARGET")

        return AccessibilityActionExecutor.setText(nodes[0], text, actionId).copy(tool = tool)
    }

    private fun focusField(args: Map<String, Any?>, actionId: String?): ToolResult {
        val root = AccessibilityBridge.getRootNode() ?: return ToolResult(false, "focus_field", actionId, "NOT_READY")
        val target = args["target_text"]?.toString()?.takeIf { it.isNotBlank() }
        val nodes = AccessibilityNodeFinder.findNodes(root) { it.isEditable && (target == null || it.text?.toString()?.contains(target, true) == true) }
        if (nodes.isEmpty()) return ToolResult(false, "focus_field", actionId, "ELEMENT_NOT_FOUND")
        if (nodes.size > 1 && target != null) return ToolResult(false, "focus_field", actionId, "AMBIGUOUS_TARGET")
        val success = nodes.first().performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        nodes.forEach { it.recycle() }
        return ToolResult(success, "focus_field", actionId, if (!success) "EXECUTION_FAILED" else null)
    }

    private fun clickNode(args: Map<String, Any?>, actionId: String?): ToolResult {
        val text = args["text"]?.toString() ?: return invalidArg("click_node", actionId, "text missing")
        val root = AccessibilityBridge.getRootNode() ?: return ToolResult(success = false, tool = "click_node", actionId = actionId, error = "NOT_READY")
        val nodes = AccessibilityNodeFinder.findByText(root, text, exact = true)
        if (nodes.isEmpty()) return ToolResult(success = false, tool = "click_node", actionId = actionId, error = "ELEMENT_NOT_FOUND")
        if (nodes.size > 1) {
            nodes.forEach { it.recycle() }
            return ToolResult(false, "click_node", actionId, "AMBIGUOUS_TARGET")
        }

        return AccessibilityActionExecutor.click(nodes[0], actionId)
    }

    private suspend fun waitForUiChange(args: Map<String, Any?>, actionId: String?): ToolResult {
        val before = AccessibilityBridge.getRootNode()?.let { root -> AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400).fingerprint() }
            ?: return ToolResult(false, "wait_for_ui_change", actionId, "NOT_READY")
        val timeout = args["timeout_ms"]?.toString()?.toLongOrNull()?.coerceIn(100L, 10_000L) ?: 3_000L
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            delay(120L)
            val current = AccessibilityBridge.getRootNode()?.let { root -> AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400).fingerprint() }
            if (current != null && current != before) return ToolResult(true, "wait_for_ui_change", actionId, message = "UI changed.")
        }
        return ToolResult(false, "wait_for_ui_change", actionId, "TIMEOUT", "UI did not change before timeout.")
    }

    private fun coordinates(args: Map<String, Any?>, tool: String, actionId: String?): Pair<Float, Float>? {
        val x = args["x"]?.toString()?.toFloatOrNull()
        val y = args["y"]?.toString()?.toFloatOrNull()
        return if (x != null && y != null && x >= 0f && y >= 0f) x to y else null
    }

    private fun floatCoordinates(args: Map<String, Any?>, tool: String, actionId: String?): FloatArray? {
        val values = listOf("x1", "y1", "x2", "y2").map { args[it]?.toString()?.toFloatOrNull() }
        return if (values.all { it != null && it >= 0f }) FloatArray(4) { values[it]!! } else null
    }

    private fun errorResult(
        tool: String,
        actionId: String?,
        error: String,
        message: String,
        identity: ActionIdentity? = null
    ): String {
        return gson.toJson(
            ToolResult(
                success = false,
                tool = tool,
                actionId = actionId,
                taskId = identity?.taskId,
                generationId = identity?.generationId,
                error = error,
                message = message
            )
        )
    }

    private fun invalidArg(tool: String, actionId: String?, msg: String): ToolResult {
        return ToolResult(success = false, tool = tool, actionId = actionId, error = "INVALID_ARGUMENT", message = msg)
    }
}
