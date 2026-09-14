package com.jarvis.assistant.util

import android.graphics.Rect
import com.jarvis.assistant.accessibility.ScreenContext
import com.jarvis.assistant.accessibility.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneGoalVerifierTest {

    @Test
    fun `completes goal only when app and criteria are visible`() {
        val screen = ScreenContext(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                ScreenNode(
                    text = "AI Music 2026",
                    description = null,
                    className = "android.widget.TextView",
                    bounds = Rect(0, 0, 200, 100),
                    clickable = true,
                    editable = false,
                    scrollable = false,
                    focused = false,
                    enabled = true,
                    selected = false,
                    checked = false,
                    availableActions = emptyList()
                )
            )
        )
        val goal = TaskGoal(
            objective = "Play AI music",
            targetApp = "youtube",
            successCriteria = listOf("AI Music")
        )

        val result = PhoneGoalVerifier.verify(goal, screen, TaskContext(goal))

        assertEquals(GoalVerificationStatus.COMPLETED, result.status)
    }
}
