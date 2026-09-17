package com.jarvis.gensoftlab.accessibility

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.jarvis.gensoftlab.tools.ToolResult
import kotlinx.coroutines.CompletableDeferred

object GestureController {

    suspend fun dispatchGesture(
        service: JarvisAccessibilityService,
        path: Path,
        duration: Long,
        tool: String,
        actionId: String?
    ): ToolResult {
        val deferred = CompletableDeferred<ToolResult>()
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        val success = service.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(ToolResult(success = true, tool = tool, actionId = actionId, message = "Gesture completed."))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(ToolResult(success = false, tool = tool, actionId = actionId, error = "GESTURE_FAILED", message = "Gesture cancelled."))
            }
        }, null)
        
        if (!success) {
            return ToolResult(success = false, tool = tool, actionId = actionId, error = "EXECUTION_FAILED", message = "Failed to dispatch gesture.")
        }
        
        return deferred.await()
    }

    suspend fun tap(service: JarvisAccessibilityService, x: Float, y: Float, actionId: String?): ToolResult {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(service, path, 100, "tap", actionId)
    }

    suspend fun swipe(service: JarvisAccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, actionId: String?): ToolResult {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatchGesture(service, path, duration, "swipe", actionId)
    }

    suspend fun doubleTap(service: JarvisAccessibilityService, x: Float, y: Float, actionId: String?): ToolResult {
        val first = tap(service, x, y, actionId)
        if (!first.success) return first.copy(tool = "double_tap")
        kotlinx.coroutines.delay(80L)
        return tap(service, x, y, actionId).copy(tool = "double_tap")
    }

    suspend fun longPress(service: JarvisAccessibilityService, x: Float, y: Float, duration: Long, actionId: String?): ToolResult {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(service, path, duration.coerceIn(500L, 3000L), "long_press", actionId)
    }

    suspend fun drag(service: JarvisAccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, actionId: String?): ToolResult {
        return swipe(service, x1, y1, x2, y2, duration.coerceIn(150L, 3000L), actionId).copy(tool = "drag")
    }
}
