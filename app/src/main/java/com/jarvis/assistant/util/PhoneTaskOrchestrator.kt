package com.jarvis.assistant.util

import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PhoneTaskOrchestrator(
    private val maxRetries: Int = 2
) {
    suspend fun execute(action: PhoneAction): ActionResult = withContext(Dispatchers.Main) {
        if (!JarvisAccessibilityService.isEnabled()) {
            return@withContext ActionResult(
                    success = false,
                    action = action.name(),
                message = "Phone Control Accessibility permission enabled নয়।",
                verification = VerificationStatus.FAILED,
                retryable = false
            )
        }

        var lastResult = failure(action.name(), "Action সম্পন্ন করা যায়নি।", retryable = true)
        repeat(maxRetries.coerceAtLeast(0) + 1) {
            if (lastResult.success) return@withContext lastResult
            lastResult = executeOnce(action)
            if (!lastResult.success && lastResult.retryable) delay(120L)
        }
        lastResult
    }

    private suspend fun executeOnce(action: PhoneAction): ActionResult {
        return when (action) {
            is PhoneAction.ClickText -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.clickText(action.text) ||
                    JarvisAccessibilityService.clickDescription(action.text)
                    verifyChanged(action.name(), action.text, executed, before)
            }
            is PhoneAction.ClickDescription -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.clickDescription(action.description)
                    verifyChanged(action.name(), action.description, executed, before)
            }
            is PhoneAction.Tap -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.tap(action.x, action.y)
                    verifyChanged(action.name(), "coordinate tap", executed, before)
            }
            is PhoneAction.LongPress -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.longPress(action.x, action.y, action.durationMs)
                    verifyChanged(action.name(), "long press", executed, before)
            }
            is PhoneAction.Swipe -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.swipe(
                    action.startX,
                    action.startY,
                    action.endX,
                    action.endY,
                    action.durationMs
                )
                    verifyChanged(action.name(), "swipe", executed, before)
            }
            is PhoneAction.Scroll -> {
                val before = fingerprint()
                val executed = JarvisAccessibilityService.scroll(action.direction == PhoneAction.Direction.DOWN)
                if (!executed) failure(action.name(), "Scroll action করা যায়নি।", retryable = true)
                else verifyFingerprint(action.name(), before, "Scroll সফল হয়েছে।")
            }
            is PhoneAction.TypeText -> {
                val executed = JarvisAccessibilityService.typeText(action.text, action.pressEnter)
                if (!executed) failure(action.name(), "কোনো focused editable field পাওয়া যায়নি।", retryable = false)
                else {
                    delay(120L)
                    val visible = JarvisAccessibilityService.getScreenContext()?.nodes
                        ?.any { node -> node.text?.contains(action.text) == true } == true
                    if (visible) verified(action.name(), "Text input সফল হয়েছে।")
                    else failure(action.name(), "Text input যাচাই করা যায়নি।", retryable = false)
                }
            }
                PhoneAction.Back -> global(action.name(), JarvisAccessibilityService.back())
                PhoneAction.Home -> global(action.name(), JarvisAccessibilityService.home())
                PhoneAction.Recents -> global(action.name(), JarvisAccessibilityService.recents())
                PhoneAction.Notifications -> global(action.name(), JarvisAccessibilityService.notifications())
                PhoneAction.QuickSettings -> global(action.name(), JarvisAccessibilityService.quickSettings())
            is PhoneAction.Wait -> {
                delay(action.milliseconds.coerceIn(0L, 5000L))
                    ActionResult(true, action.name(), "Wait সম্পন্ন হয়েছে।", VerificationStatus.VERIFIED)
            }
        }
    }

    private suspend fun verifyChanged(
        actionName: String,
        target: String,
        executed: Boolean,
        before: String?
    ): ActionResult {
        if (!executed) return failure(actionName, "$target খুঁজে পাওয়া বা action করা যায়নি।", retryable = true)
        delay(180L)
        return verifyFingerprint(actionName, before, "$target action সম্পন্ন হয়েছে।")
    }

    private fun verifyFingerprint(actionName: String, before: String?, successMessage: String): ActionResult {
        val after = fingerprint()
        return if (before == null || after == null || before != after) {
            verified(actionName, successMessage)
        } else {
            failure(actionName, "Action হয়েছে, কিন্তু screen পরিবর্তন যাচাই করা যায়নি।", retryable = true)
        }
    }

    private fun fingerprint(): String? = JarvisAccessibilityService.getScreenContext()?.fingerprint()

    private fun global(actionName: String, executed: Boolean): ActionResult {
        return if (executed) verified(actionName, "$actionName সফল হয়েছে।")
        else failure(actionName, "$actionName করা যায়নি।", retryable = true)
    }

    private fun verified(action: String, message: String) = ActionResult(
        success = true,
        action = action,
        message = message,
        verification = VerificationStatus.VERIFIED
    )

    private fun failure(action: String, message: String, retryable: Boolean) = ActionResult(
        success = false,
        action = action,
        message = message,
        verification = VerificationStatus.FAILED,
        retryable = retryable
    )

    private fun PhoneAction.name(): String = when (this) {
        is PhoneAction.ClickText -> "click_text"
        is PhoneAction.ClickDescription -> "click_description"
        is PhoneAction.Tap -> "tap"
        is PhoneAction.LongPress -> "long_press"
        is PhoneAction.Swipe -> "swipe"
        is PhoneAction.Scroll -> "scroll"
        is PhoneAction.TypeText -> "type_text"
        PhoneAction.Back -> "back"
        PhoneAction.Home -> "home"
        PhoneAction.Recents -> "recents"
        PhoneAction.Notifications -> "notifications"
        PhoneAction.QuickSettings -> "quick_settings"
        is PhoneAction.Wait -> "wait"
    }
}
