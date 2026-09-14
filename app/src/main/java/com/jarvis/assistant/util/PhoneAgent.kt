package com.jarvis.assistant.util

import android.content.Context
import com.jarvis.assistant.accessibility.ScreenContext
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class GoalVerificationStatus {
    COMPLETED,
    NOT_COMPLETED,
    AMBIGUOUS
}

data class GoalVerification(
    val status: GoalVerificationStatus,
    val message: String
)

data class TaskContext(
    val goal: TaskGoal,
    val currentScreenFingerprint: String? = null,
    val currentApp: String? = null,
    val lastDecision: PlannerDecision? = null,
    val lastActionResult: ActionResult? = null,
    val lastVerification: VerificationStatus? = null,
    val completedSteps: Int = 0,
    val plannerCalls: Int = 0,
    val recoveryAttempts: Int = 0,
    val noProgressCount: Int = 0,
    val lastActionKey: String? = null
)

fun interface PhonePlanner {
    suspend fun nextDecision(context: TaskContext, screen: ScreenContext?): PlannerValidation
}

object PhoneGoalVerifier {
    fun verify(goal: TaskGoal, screen: ScreenContext?, context: TaskContext): GoalVerification {
        if (screen == null) return GoalVerification(GoalVerificationStatus.NOT_COMPLETED, "Screen context পাওয়া যায়নি।")
        val packageName = screen.packageName.orEmpty().lowercase()
        val appSatisfied = goal.targetApp.isNullOrBlank() || packageName.contains(goal.targetApp.lowercase())
        val criteriaSatisfied = goal.successCriteria.isEmpty() || goal.successCriteria.all { criterion ->
            val normalized = criterion.lowercase()
            screen.nodes.any { node ->
                node.text.orEmpty().lowercase().contains(normalized) ||
                    node.description.orEmpty().lowercase().contains(normalized)
            }
        }
        return if (appSatisfied && criteriaSatisfied) {
            GoalVerification(GoalVerificationStatus.COMPLETED, "Goal-এর প্রয়োজনীয় screen evidence পাওয়া গেছে।")
        } else {
            GoalVerification(GoalVerificationStatus.NOT_COMPLETED, "Goal এখনো সম্পূর্ণ হওয়ার যথেষ্ট evidence নেই।")
        }
    }
}

class PhoneAgent(
    private val context: Context,
    private val maxPlannerCalls: Int = 20,
    private val maxRecoveryAttempts: Int = 3,
    private val taskTimeoutMs: Long = 60_000L
) {
    private val orchestrator = PhoneTaskOrchestrator(maxRetries = 2)

    suspend fun run(
        goal: TaskGoal,
        planner: PhonePlanner,
        onStateChanged: (PhoneTaskState) -> Unit = {}
    ): TaskResult = withContext(Dispatchers.Default) {
        withTimeoutOrNull(taskTimeoutMs.coerceAtLeast(1L)) {
            executeLoop(goal, planner, onStateChanged)
        } ?: TaskResult(
            success = false,
            completedSteps = 0,
            totalSteps = maxPlannerCalls,
            message = "Task-এর সময়সীমা শেষ হয়েছে।",
            failureReason = FailureReason.TIMEOUT,
            state = PhoneTaskState.FAILED
        )
    } ?: TaskResult(
        success = false,
        completedSteps = 0,
        totalSteps = maxPlannerCalls,
        message = "Task-এর সময়সীমা শেষ হয়েছে।",
        failureReason = FailureReason.TIMEOUT,
        state = PhoneTaskState.FAILED
    )

    private suspend fun executeLoop(
        goal: TaskGoal,
        planner: PhonePlanner,
        onStateChanged: (PhoneTaskState) -> Unit
    ): TaskResult {
        var taskContext = TaskContext(goal = goal)
        val actionHistory = mutableListOf<String>()
        var recoveryAttempts = 0

        repeat(maxPlannerCalls.coerceAtLeast(1)) { callIndex ->
            ensureActive()
            onStateChanged(PhoneTaskState.OBSERVING)
            val screen = JarvisAccessibilityService.getScreenContext()
            val verification = PhoneGoalVerifier.verify(goal, screen, taskContext)
            if (verification.status == GoalVerificationStatus.COMPLETED) {
                onStateChanged(PhoneTaskState.COMPLETED)
                return TaskResult(true, taskContext.completedSteps, callIndex, verification.message, state = PhoneTaskState.COMPLETED, plannerCalls = callIndex, recoveryAttempts = recoveryAttempts)
            }

            onStateChanged(PhoneTaskState.PLANNING)
            val decisionValidation = planner.nextDecision(
                taskContext.copy(
                    currentScreenFingerprint = screen?.fingerprint(),
                    currentApp = screen?.packageName
                ),
                screen
            )
            if (!decisionValidation.valid || decisionValidation.decision == null) {
                recoveryAttempts++
                onStateChanged(PhoneTaskState.RECOVERING)
                if (recoveryAttempts > maxRecoveryAttempts) {
                    return TaskResult(false, taskContext.completedSteps, callIndex + 1, decisionValidation.message, FailureReason.UNKNOWN_ERROR, PhoneTaskState.FAILED, callIndex + 1, recoveryAttempts)
                }
                taskContext = taskContext.copy(recoveryAttempts = recoveryAttempts)
                return@repeat
            }

            val decision = decisionValidation.decision
            when (decision.status) {
                PlannerStatus.COMPLETED -> return TaskResult(true, taskContext.completedSteps, callIndex + 1, "Task সম্পন্ন হয়েছে।", state = PhoneTaskState.COMPLETED, plannerCalls = callIndex + 1, recoveryAttempts = recoveryAttempts)
                PlannerStatus.NEED_USER_INPUT -> return TaskResult(false, taskContext.completedSteps, callIndex + 1, decision.question ?: "আরও তথ্য প্রয়োজন।", FailureReason.AMBIGUOUS_TARGET, PhoneTaskState.WAITING, callIndex + 1, recoveryAttempts)
                PlannerStatus.FAILED -> return TaskResult(false, taskContext.completedSteps, callIndex + 1, "Planner task চালাতে পারেনি।", FailureReason.UNKNOWN_ERROR, PhoneTaskState.FAILED, callIndex + 1, recoveryAttempts)
                PlannerStatus.CONTINUE -> Unit
            }

            val actionKey = "${screen?.fingerprint()}|${decision.action}|${decision.arguments}"
            if (actionHistory.count { it == actionKey } >= 2) {
                return TaskResult(false, taskContext.completedSteps, callIndex + 1, "একই action বারবার ব্যর্থ হওয়ায় task থামানো হয়েছে।", FailureReason.UNKNOWN_ERROR, PhoneTaskState.FAILED, callIndex + 1, recoveryAttempts)
            }
            actionHistory += actionKey
            onStateChanged(PhoneTaskState.EXECUTING)
            val actionResult = executeDecision(decision)
            onStateChanged(PhoneTaskState.VERIFYING)
            taskContext = taskContext.copy(
                currentScreenFingerprint = screen?.fingerprint(),
                currentApp = screen?.packageName,
                lastDecision = decision,
                lastActionResult = actionResult,
                lastVerification = actionResult.verification,
                completedSteps = taskContext.completedSteps + if (actionResult.success) 1 else 0,
                plannerCalls = callIndex + 1,
                recoveryAttempts = recoveryAttempts
            )
            if (!actionResult.success) {
                recoveryAttempts++
                taskContext = taskContext.copy(recoveryAttempts = recoveryAttempts)
                onStateChanged(PhoneTaskState.RECOVERING)
                if (recoveryAttempts > maxRecoveryAttempts) {
                    return TaskResult(false, taskContext.completedSteps, callIndex + 1, actionResult.message, FailureReason.VERIFICATION_FAILED, PhoneTaskState.FAILED, callIndex + 1, recoveryAttempts)
                }
            }
        }
        onStateChanged(PhoneTaskState.FAILED)
        return TaskResult(false, taskContext.completedSteps, maxPlannerCalls, "Planner call limit শেষ হয়েছে।", FailureReason.TIMEOUT, PhoneTaskState.FAILED, maxPlannerCalls, recoveryAttempts)
    }

    private suspend fun executeDecision(decision: PlannerDecision): ActionResult {
        val name = decision.action ?: return ActionResult(false, "planner", "Action missing।", VerificationStatus.FAILED)
        return when (name) {
            "click_text" -> orchestrator.execute(PhoneAction.ClickText(decision.arguments.string("text")))
            "click_description" -> orchestrator.execute(PhoneAction.ClickDescription(decision.arguments.string("description")))
            "scroll" -> orchestrator.execute(PhoneAction.Scroll(if (decision.arguments.string("direction").equals("down", true)) PhoneAction.Direction.DOWN else PhoneAction.Direction.UP))
            "type_text" -> orchestrator.execute(PhoneAction.TypeText(decision.arguments.string("text"), decision.arguments.boolean("press_enter")))
            "back" -> orchestrator.execute(PhoneAction.Back)
            "home" -> orchestrator.execute(PhoneAction.Home)
            "recents" -> orchestrator.execute(PhoneAction.Recents)
            "notifications" -> orchestrator.execute(PhoneAction.Notifications)
            "quick_settings" -> orchestrator.execute(PhoneAction.QuickSettings)
            "wait_for_element" -> orchestrator.waitForElement(decision.arguments.string("text"))
            else -> ActionResult(false, name, "এই planner action-এর execution adapter নেই।", VerificationStatus.FAILED)
        }
    }

    private suspend fun ensureActive() {
        currentCoroutineContext().ensureActive()
    }

    private fun Map<String, Any?>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()
    private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: false
}