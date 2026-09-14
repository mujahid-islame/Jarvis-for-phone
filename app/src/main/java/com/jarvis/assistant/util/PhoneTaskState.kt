package com.jarvis.assistant.util

enum class PhoneTaskState {
    IDLE,
    UNDERSTANDING,
    OBSERVING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    RECOVERING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class FailureReason {
    TARGET_NOT_FOUND,
    APP_NOT_FOUND,
    ACCESSIBILITY_UNAVAILABLE,
    ACTION_FAILED,
    VERIFICATION_FAILED,
    TIMEOUT,
    CANCELLED,
    AMBIGUOUS_TARGET,
    SAFETY_BLOCKED,
    UNKNOWN_ERROR
}

data class TaskResult(
    val success: Boolean,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String,
    val failureReason: FailureReason? = null,
    val state: PhoneTaskState = if (success) PhoneTaskState.COMPLETED else PhoneTaskState.FAILED
)