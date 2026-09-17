package com.jarvis.gensoftlab.util

import android.graphics.Rect
import com.jarvis.gensoftlab.accessibility.UiSnapshot
import com.jarvis.gensoftlab.accessibility.UiElement
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneGoalVerifierTest {

    @Test
    fun `completes goal only when app and criteria are visible`() {
        val uiSnapshot = UiSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                UiElement(
                    text = "AI Music 2026",
                    contentDescription = null,
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

        val result = PhoneGoalVerifier.verify(goal, uiSnapshot, TaskContext(goal))

        assertEquals(GoalVerificationStatus.COMPLETED, result.status)
    }
}
