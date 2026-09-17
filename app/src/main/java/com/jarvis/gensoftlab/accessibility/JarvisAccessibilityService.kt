package com.jarvis.gensoftlab.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Filter and process meaningful events if needed for wait_for_ui_change
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        AccessibilityBridge.unregister()
        super.onDestroy()
    }

    private fun getUiSnapshot(): UiSnapshot? {
        val root = rootInActiveWindow ?: return null
        val elements = mutableListOf<UiElement>()
        collectNodes(root, elements, 0)
        return UiSnapshot(root.packageName?.toString(), elements)
    }

    private fun collectNodes(node: android.view.accessibility.AccessibilityNodeInfo, output: MutableList<UiElement>, depth: Int) {
        if (depth > 16 || output.size >= 120) return
        val text = node.text?.toString()?.trim()?.takeUnless { it.isBlank() }
        val description = node.contentDescription?.toString()?.trim()?.takeUnless { it.isBlank() }
        if (text != null || description != null || node.isClickable || node.isEditable || node.isScrollable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            output += UiElement(
                text = text?.take(160),
                contentDescription = description?.take(160),
                className = node.className?.toString()?.take(120),
                bounds = bounds,
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focused = node.isFocused,
                enabled = node.isEnabled,
                selected = node.isSelected,
                checked = node.isChecked,
                availableActions = node.actionList.map { it.id }.take(12)
            )
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectNodes(it, output, depth + 1) }
        }
    }

    companion object {
        fun isEnabled(): Boolean = AccessibilityBridge.isAvailable()
        
        fun getUiSnapshot(): UiSnapshot? = (AccessibilityBridge.getService() as? JarvisAccessibilityService)?.getUiSnapshot()

        fun typeText(text: String, pressEnter: Boolean): Boolean = 
            (AccessibilityBridge.getService() as? JarvisAccessibilityService)?.let { service ->
                val root = service.rootInActiveWindow ?: return@let false
                val focused = findNode(root) { it.isFocused && it.isEditable }
                    ?: findNode(root) { it.isEditable }
                    ?: return@let false
                val arguments = android.os.Bundle().apply {
                    putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } ?: false

        private fun findNode(
            node: android.view.accessibility.AccessibilityNodeInfo,
            predicate: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean
        ): android.view.accessibility.AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                findNode(child, predicate)?.let { return it }
            }
            return null
        }

        fun clickText(target: String): Boolean = (AccessibilityBridge.getService() as? JarvisAccessibilityService)?.let { service ->
             // Placeholder for now, will call NodeFinder later
             false
        } ?: false

        fun clickDescription(target: String): Boolean = false
        fun tap(x: Float, y: Float): Boolean = false
        fun longPress(x: Float, y: Float, durationMs: Long): Boolean = false
        fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = false
        fun scroll(forward: Boolean): Boolean = scroll(forward, null).success

        fun scroll(forward: Boolean, actionId: String?): com.jarvis.gensoftlab.tools.ToolResult =
            (AccessibilityBridge.getService() as? JarvisAccessibilityService)?.let { service ->
                val root = service.rootInActiveWindow
                    ?: return@let com.jarvis.gensoftlab.tools.ToolResult(false, "scroll", actionId, "NOT_READY")
                val requestedAction = if (forward) {
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val scrollable = findNode(root) {
                    it.isScrollable && it.actionList.any { action -> action.id == requestedAction }
                } ?: findNode(root) { it.isScrollable }
                    ?: return@let com.jarvis.gensoftlab.tools.ToolResult(false, "scroll", actionId, "ELEMENT_NOT_FOUND")
                AccessibilityActionExecutor.scroll(scrollable, forward, actionId)
            } ?: com.jarvis.gensoftlab.tools.ToolResult(false, "scroll", actionId, "NOT_READY")
        fun back(): Boolean = AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        fun home(): Boolean = AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        fun recents(): Boolean = AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        fun notifications(): Boolean = AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        fun quickSettings(): Boolean = AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }
}
