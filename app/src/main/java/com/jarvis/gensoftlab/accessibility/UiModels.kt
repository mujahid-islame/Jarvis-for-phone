package com.jarvis.gensoftlab.accessibility

import android.graphics.Rect

enum class SemanticRole {
    BUTTON,
    TEXT_FIELD,
    SEARCH_FIELD,
    LINK,
    IMAGE,
    VIDEO,
    LIST,
    TAB,
    MENU,
    CHECKBOX,
    SWITCH,
    SLIDER,
    DIALOG,
    SCROLL_CONTAINER,
    UNKNOWN
}

data class ScreenNode(
    val text: String?,
    val description: String?,
    val className: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val checked: Boolean,
    val availableActions: List<Int>,
    val role: SemanticRole = inferRole(className, text, description, editable, clickable, scrollable)
) {
    companion object {
        private fun inferRole(
            className: String?,
            text: String?,
            description: String?,
            editable: Boolean,
            clickable: Boolean,
            scrollable: Boolean
        ): SemanticRole {
            val value = "${className.orEmpty()} ${text.orEmpty()} ${description.orEmpty()}".lowercase()
            return when {
                editable && (value.contains("search") || value.contains("সার্চ")) -> SemanticRole.SEARCH_FIELD
                editable -> SemanticRole.TEXT_FIELD
                scrollable -> SemanticRole.SCROLL_CONTAINER
                value.contains("switch") || value.contains("toggle") -> SemanticRole.SWITCH
                value.contains("checkbox") -> SemanticRole.CHECKBOX
                value.contains("slider") -> SemanticRole.SLIDER
                value.contains("video") || value.contains("ভিডিও") -> SemanticRole.VIDEO
                value.contains("image") || value.contains("ছবি") -> SemanticRole.IMAGE
                value.contains("tab") -> SemanticRole.TAB
                value.contains("menu") -> SemanticRole.MENU
                value.contains("link") || value.contains("http") -> SemanticRole.LINK
                clickable || value.contains("button") -> SemanticRole.BUTTON
                else -> SemanticRole.UNKNOWN
            }
        }
    }
}

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
                append(node.className.orEmpty())
                append(':')
                append(node.bounds.flattenToString())
                append(':')
                append(node.focused)
                append(':')
                append(node.enabled)
            }
        }
    }
}
