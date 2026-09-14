package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenNode(
    val text: String?,
    val description: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean
)

data class ScreenContext(
    val packageName: String?,
    val nodes: List<ScreenNode>
) {
    fun fingerprint(): String {
        return buildString {
            append(packageName.orEmpty())
            nodes.take(80).forEach { node ->
                append('|')
                append(node.text.orEmpty())
                append(':')
                append(node.description.orEmpty())
                append(':')
                append(node.bounds.flattenToString())
                append(':')
                append(node.focused)
            }
        }
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun clickVisibleText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(target).any(::clickNodeOrAncestor)
    }

    private fun clickVisibleDescription(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) {
            it.contentDescription?.toString()?.contains(target, ignoreCase = true) == true
        }
        return node != null && clickNodeOrAncestor(node)
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var clickableNode: AccessibilityNodeInfo? = node
        while (clickableNode != null && !clickableNode.isClickable) {
            clickableNode = clickableNode.parent
        }
        return clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findNode(root) { it.isScrollable } ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    private fun typeText(text: String, pressEnter: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = findNode(root) { it.isFocused && it.isEditable }
            ?: findNode(root) { it.isEditable }
            ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val typed = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return typed
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(), null, null
        )
    }

    private fun longPress(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(500L, 5000L)))
                .build(), null, null
        )
    }

    private fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100L, 5000L)))
                .build(), null, null
        )
    }

    private fun getScreenContext(): ScreenContext? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<ScreenNode>()
        collectNodes(root, nodes, 0)
        return ScreenContext(root.packageName?.toString(), nodes)
    }

    private fun collectNodes(node: AccessibilityNodeInfo, output: MutableList<ScreenNode>, depth: Int) {
        if (depth > 16 || output.size >= 120) return
        val text = node.text?.toString()?.trim()?.takeUnless { it.isBlank() }
        val description = node.contentDescription?.toString()?.trim()?.takeUnless { it.isBlank() }
        if (text != null || description != null || node.isClickable || node.isEditable || node.isScrollable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            output += ScreenNode(
                text = text?.take(160),
                description = description?.take(160),
                bounds = bounds,
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focused = node.isFocused
            )
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectNodes(it, output, depth + 1) }
        }
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null
        fun getScreenContext(): ScreenContext? = instance?.getScreenContext()
        fun scroll(forward: Boolean): Boolean = instance?.scroll(forward) == true
        fun clickText(target: String): Boolean = instance?.clickVisibleText(target) == true
        fun clickDescription(target: String): Boolean = instance?.clickVisibleDescription(target) == true
        fun tap(x: Float, y: Float): Boolean = instance?.tap(x, y) == true
        fun longPress(x: Float, y: Float, durationMs: Long): Boolean =
            instance?.longPress(x, y, durationMs) == true
        fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean =
            instance?.swipe(startX, startY, endX, endY, durationMs) == true
        fun typeText(text: String, pressEnter: Boolean): Boolean =
            instance?.typeText(text, pressEnter) == true
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun recents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
        fun notifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun quickSettings(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true
    }
}
