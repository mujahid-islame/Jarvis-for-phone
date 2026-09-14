package com.jarvis.assistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTaskRouterTest {

    @Test
    fun `routes normal conversation to chat`() {
        assertEquals(RequestRoute.CHAT, PhoneTaskRouter.route("তুমি কেমন আছ?").route)
    }

    @Test
    fun `routes simple app command to deterministic tool`() {
        assertEquals(RequestRoute.SIMPLE_TOOL, PhoneTaskRouter.route("YouTube খোলো").route)
    }

    @Test
    fun `creates structured goal for complex phone request`() {
        val routed = PhoneTaskRouter.route("YouTube খুলে AI music search করে প্রথম ভিডিও চালাও")

        assertEquals(RequestRoute.PHONE_TASK, routed.route)
        assertNotNull(routed.goal)
        assertEquals("YouTube", routed.goal?.targetApp)
        assertTrue(routed.goal?.successCriteria?.isNotEmpty() == true)
    }

    @Test
    fun `routes cancellation explicitly`() {
        assertEquals(RequestRoute.CANCEL_TASK, PhoneTaskRouter.route("থামো").route)
    }
}
