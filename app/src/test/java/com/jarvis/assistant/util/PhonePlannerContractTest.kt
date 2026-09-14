package com.jarvis.assistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePlannerContractTest {

    @Test
    fun `rejects invalid scroll direction`() {
        val result = PhonePlannerContract.validateFunctionCall(
            name = "scroll",
            arguments = mapOf("direction" to "sideways"),
            confidence = 0.95
        )

        assertFalse(result.valid)
    }

    @Test
    fun `rejects low confidence action`() {
        val result = PhonePlannerContract.validateFunctionCall(
            name = "click_text",
            arguments = mapOf("text" to "Settings"),
            confidence = 0.4
        )

        assertFalse(result.valid)
    }

    @Test
    fun `accepts a valid single next action`() {
        val result = PhonePlannerContract.validateFunctionCall(
            name = "click_text",
            arguments = mapOf("text" to "Search"),
            confidence = 0.92
        )

        assertTrue(result.valid)
        assertEquals("click_text", result.decision?.action)
    }

    @Test
    fun `parses completed planner decision`() {
        val result = PhonePlannerContract.parseDecision("{\"status\":\"COMPLETED\"}")

        assertTrue(result.valid)
        assertEquals(PlannerStatus.COMPLETED, result.decision?.status)
    }

    @Test
    fun `validates brightness range`() {
        val valid = PhonePlannerContract.validateFunctionCall(
            "set_brightness",
            mapOf("percent" to 50),
            confidence = 0.95
        )
        val invalid = PhonePlannerContract.validateFunctionCall(
            "set_brightness",
            mapOf("percent" to 120),
            confidence = 0.95
        )

        assertTrue(valid.valid)
        assertFalse(invalid.valid)
    }

    @Test
    fun `rejects malformed planner decision`() {
        val result = PhonePlannerContract.parseDecision("not-json")

        assertFalse(result.valid)
    }
}
