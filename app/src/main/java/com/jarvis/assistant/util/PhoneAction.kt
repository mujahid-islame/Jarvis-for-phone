package com.jarvis.assistant.util

sealed class PhoneAction {
    data class ClickText(val text: String) : PhoneAction()
    data class ClickDescription(val description: String) : PhoneAction()
    data class Tap(val x: Float, val y: Float) : PhoneAction()
    data class LongPress(val x: Float, val y: Float, val durationMs: Long = 800L) : PhoneAction()
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long = 350L
    ) : PhoneAction()
    data class Scroll(val direction: Direction) : PhoneAction()
    data class TypeText(val text: String, val pressEnter: Boolean = false) : PhoneAction()
    data object Back : PhoneAction()
    data object Home : PhoneAction()
    data object Recents : PhoneAction()
    data object Notifications : PhoneAction()
    data object QuickSettings : PhoneAction()
    data class SetBrightness(val percent: Int) : PhoneAction()
    data class Wait(val milliseconds: Long) : PhoneAction()

    enum class Direction {
        UP,
        DOWN
    }
}

enum class VerificationStatus {
    VERIFIED,
    EXECUTED_UNVERIFIED,
    FAILED
}

data class ActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val verification: VerificationStatus,
    val retryable: Boolean = false,
    val confidence: Double = if (verification == VerificationStatus.VERIFIED) 1.0 else 0.0,
    val shouldReobserve: Boolean = !success
)
