package com.jarvis.gensoftlab.accessibility

import android.graphics.Rect

data class UiElement(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val bounds: Rect = Rect(),
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focusable: Boolean = false,
    val focused: Boolean = false,
    val editable: Boolean = false,
    val selected: Boolean = false,
    val checked: Boolean = false,
    val scrollable: Boolean = false,
    val packageName: String? = null
)
