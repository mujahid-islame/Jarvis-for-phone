package com.jarvis.gensoftlab.accessibility

data class UiSnapshot(
    val packageName: String? = null,
    val activityName: String? = null,
    val windowId: Int = -1,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val focusedElement: UiElement? = null,
    val elements: List<UiElement> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
