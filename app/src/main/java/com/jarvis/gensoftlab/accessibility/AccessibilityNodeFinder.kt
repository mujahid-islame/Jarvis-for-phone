package com.jarvis.gensoftlab.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeFinder {

    fun findNodes(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        traverse(root, found, predicate)
        return found
    }

    private fun traverse(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>, predicate: (AccessibilityNodeInfo) -> Boolean) {
        if (predicate(node)) {
            output.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverse(child, output, predicate)
        }
    }

    fun findByText(root: AccessibilityNodeInfo, text: String, exact: Boolean = false): List<AccessibilityNodeInfo> {
        return findNodes(root) { node ->
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (exact) {
                nodeText.equals(text, ignoreCase = true) || nodeDesc.equals(text, ignoreCase = true)
            } else {
                nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)
            }
        }
    }

    fun findByViewId(root: AccessibilityNodeInfo, viewId: String): List<AccessibilityNodeInfo> {
        return findNodes(root) { node ->
            node.viewIdResourceName?.contains(viewId, ignoreCase = true) == true
        }
    }

    fun createUiElement(node: AccessibilityNodeInfo): UiElement {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return UiElement(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString(),
            bounds = bounds,
            clickable = node.isClickable,
            editable = node.isEditable,
            scrollable = node.isScrollable,
            focused = node.isFocused,
            enabled = node.isEnabled,
            selected = node.isSelected,
            checked = node.isChecked,
            packageName = node.packageName?.toString(),
            viewId = node.viewIdResourceName,
            focusable = node.isFocusable
        )
    }

    fun captureSnapshot(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): UiSnapshot {
        val elements = mutableListOf<UiElement>()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        traverse(root, nodes) { true }
        
        var focusedElement: UiElement? = null
        for (node in nodes) {
            val element = createUiElement(node)
            elements.add(element)
            if (node.isFocused) {
                focusedElement = element
            }
            node.recycle()
        }

        return UiSnapshot(
            packageName = root.packageName?.toString(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            focusedElement = focusedElement,
            nodes = elements
        )
    }
}
