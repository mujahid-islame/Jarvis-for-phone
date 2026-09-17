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

enum class ActionLifecycle {
    NEW, IN_FLIGHT, COMPLETED, FAILED, CANCELLED, STALE
}

data class ActionIdentity(
    val taskId: String,
    val generationId: String,
    val actionId: String
)

data class ActionRecord(
    val identity: ActionIdentity,
    val tool: String,
    val state: ActionLifecycle,
    val result: String? = null
)

sealed class ActionAdmission {
    data object Accepted : ActionAdmission()
    data class Cached(val result: String) : ActionAdmission()
    data object DuplicateInFlight : ActionAdmission()
    data object Stale : ActionAdmission()
}

object TaskStateManager {
    private var activeContext: TaskStateContext? = null
    private val actions = LinkedHashMap<ActionIdentity, ActionRecord>()
    private const val MAX_ACTION_RECORDS = 128

    @Synchronized
    fun startTask(taskId: String, generationId: String, actionId: String) {
        actions.clear()
        activeContext = TaskStateContext(
            taskId = taskId,
            generationId = generationId,
            actionId = actionId,
            status = TaskStatus.PLANNING
        )
    }

    @Synchronized
    fun admitAction(identity: ActionIdentity, tool: String): ActionAdmission {
        val current = activeContext
        if (current == null) {
            activeContext = TaskStateContext(
                identity.taskId,
                identity.generationId,
                identity.actionId,
                TaskStatus.RUNNING
            )
        } else if (
            current.taskId != identity.taskId ||
            current.generationId != identity.generationId ||
            current.cancelled ||
            current.status in setOf(TaskStatus.CANCELLED, TaskStatus.COMPLETED, TaskStatus.FAILED)
        ) {
            return ActionAdmission.Stale
        }

        val existing = actions[identity]
        if (existing != null) {
            return when (existing.state) {
                ActionLifecycle.IN_FLIGHT -> ActionAdmission.DuplicateInFlight
                ActionLifecycle.COMPLETED, ActionLifecycle.FAILED -> existing.result
                    ?.let(ActionAdmission::Cached)
                    ?: ActionAdmission.DuplicateInFlight
                ActionLifecycle.CANCELLED, ActionLifecycle.STALE -> ActionAdmission.Stale
                ActionLifecycle.NEW -> ActionAdmission.DuplicateInFlight
            }
        }

        actions[identity] = ActionRecord(identity, tool, ActionLifecycle.IN_FLIGHT)
        trimRecords()
        return ActionAdmission.Accepted
    }

    @Synchronized
    fun completeAction(identity: ActionIdentity, result: String, success: Boolean): Boolean {
        val record = actions[identity] ?: return false
        if (record.state != ActionLifecycle.IN_FLIGHT) return false
        actions[identity] = record.copy(
            state = if (success) ActionLifecycle.COMPLETED else ActionLifecycle.FAILED,
            result = result
        )
        return true
    }

    @Synchronized
    fun cancelTask(taskId: String, generationId: String): Boolean {
        val current = activeContext ?: return false
        if (current.taskId != taskId || current.generationId != generationId) return false
        activeContext = current.copy(status = TaskStatus.CANCELLED, cancelled = true, updatedAt = System.currentTimeMillis())
        actions.entries
            .filter { it.key.taskId == taskId && it.key.generationId == generationId && it.value.state == ActionLifecycle.IN_FLIGHT }
            .forEach { (key, record) -> actions[key] = record.copy(state = ActionLifecycle.CANCELLED) }
        return true
    }

    @Synchronized
    fun resetForTests() {
        activeContext = null
        actions.clear()
    }

    @Synchronized
    fun updateStatus(nextStatus: TaskStatus): Boolean {
        val current = activeContext ?: return false
        val allowed = when (current.status) {
            TaskStatus.IDLE -> setOf(TaskStatus.PLANNING, TaskStatus.CANCELLED)
            TaskStatus.PLANNING -> setOf(TaskStatus.RUNNING, TaskStatus.WAITING, TaskStatus.CANCELLED, TaskStatus.FAILED)
            TaskStatus.RUNNING -> setOf(TaskStatus.WAITING, TaskStatus.VERIFYING, TaskStatus.RECOVERING, TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.FAILED)
            TaskStatus.WAITING -> setOf(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED)
            TaskStatus.VERIFYING -> setOf(TaskStatus.RUNNING, TaskStatus.RECOVERING, TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.FAILED)
            TaskStatus.RECOVERING -> setOf(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED)
            TaskStatus.PAUSED -> setOf(TaskStatus.RUNNING, TaskStatus.CANCELLED)
            TaskStatus.CANCELLED, TaskStatus.COMPLETED, TaskStatus.FAILED -> emptySet()
        }
        if (nextStatus !in allowed) return false

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

    @Synchronized
    fun actionState(identity: ActionIdentity): ActionLifecycle? = actions[identity]?.state

    private fun trimRecords() {
        while (actions.size > MAX_ACTION_RECORDS) {
            val first = actions.entries.firstOrNull() ?: return
            if (first.value.state == ActionLifecycle.IN_FLIGHT) return
            actions.remove(first.key)
        }
    }
}
