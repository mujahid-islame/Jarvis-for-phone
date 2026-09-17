package com.jarvis.gensoftlab.agent

enum class TaskStatus {
    IDLE,
    PLANNING,
    RUNNING,
    WAITING,
    VERIFYING,
    RECOVERING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED
}

data class TaskStateContext(
    val taskId: String,
    val generationId: String,
    val actionId: String,
    val status: TaskStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val currentTool: String? = null,
    val retryCount: Int = 0,
    val cancelled: Boolean = false,
    val paused: Boolean = false
)

object TaskStateManager {
    private var activeContext: TaskStateContext? = null

    @Synchronized
    fun startTask(taskId: String, generationId: String, actionId: String) {
        activeContext = TaskStateContext(
            taskId = taskId,
            generationId = generationId,
            actionId = actionId,
            status = TaskStatus.PLANNING
        )
    }

    @Synchronized
    fun updateStatus(nextStatus: TaskStatus): Boolean {
        val current = activeContext ?: return false
        
        // Enforce strict valid state lifecycle transitions
        if (current.status == TaskStatus.COMPLETED && nextStatus == TaskStatus.RUNNING) {
            return false // must be rejected unless new generation task is created
        }

        activeContext = current.copy(
            status = nextStatus,
            updatedAt = System.currentTimeMillis()
        )
        return true
    }

    @Synchronized
    fun validateGeneration(taskId: String, generationId: String, actionId: String): Boolean {
        val current = activeContext ?: return false
        return current.taskId == taskId && 
               current.generationId == generationId && 
               current.actionId == actionId
    }

    @Synchronized
    fun getActiveContext(): TaskStateContext? = activeContext
}
