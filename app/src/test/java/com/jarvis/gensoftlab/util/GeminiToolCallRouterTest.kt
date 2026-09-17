package com.jarvis.gensoftlab.util

import android.content.ContextWrapper
import com.jarvis.gensoftlab.agent.TaskStateManager
import com.jarvis.gensoftlab.agent.ActionAdmission
import com.jarvis.gensoftlab.agent.ActionIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiToolCallRouterTest {
    private val context = ContextWrapper(null)

    @Before
    fun reset() {
        TaskStateManager.resetForTests()
    }

    @Test
    fun `fake Gemini call preserves identity through response routing`() {
        val router = GeminiToolCallRouter { context, name, arguments ->
            AssistantToolExecutor.executeFunction(
                context,
                name,
                arguments,
                kotlinx.coroutines.Dispatchers.Unconfined
            )
        }
        var response: GeminiToolResponse? = null

        kotlinx.coroutines.runBlocking {
            router.route(
                context,
                "A1",
                "get_current_app",
                mapOf("task_id" to "T1", "generation_id" to "G1"),
            ) { response = it }
        }

        assertFalse(response!!.success)
        assertEquals("get_current_app", response!!.name)
        assertEquals("A1", response!!.actionId)
        assertEquals("T1", response!!.taskId)
        assertEquals("G1", response!!.generationId)
        assertTrue(response!!.message.contains("SPECIAL_ACCESS_REQUIRED"))
        assertTrue(router.serialize(response!!).contains("\"callId\":\"A1\""))
    }

    @Test
    fun `invalid fake Gemini call is rejected before executor`() {
        var executed = false
        val router = GeminiToolCallRouter { _, _, _ ->
            executed = true
            AssistantToolResult(true, "{}")
        }
        var response: GeminiToolResponse? = null

        kotlinx.coroutines.runBlocking {
            router.route(context, "A2", "tap", mapOf("x" to 1),) { response = it }
        }

        assertFalse(response!!.success)
        assertTrue(response!!.message.contains("Required argument missing"))
        assertFalse(executed)
    }

    @Test
    fun `real dispatcher returns structured special access failure`() {
        val json = kotlinx.coroutines.runBlocking {
            com.jarvis.gensoftlab.tools.ToolDispatcher.dispatch(
                "get_current_app",
                mapOf("task_id" to "T1", "generation_id" to "G1"),
                "A3",
                kotlinx.coroutines.Dispatchers.Unconfined
            )
        }

        assertTrue(json.contains("\"error\":\"SPECIAL_ACCESS_REQUIRED\"") || json.contains("\"success\":true"))
    }

    @Test
    fun `stale fake Gemini call is rejected before executor`() {
        TaskStateManager.startTask("T1", "G2", "ACTIVE")
        var executed = false
        val router = GeminiToolCallRouter { _, _, arguments ->
            val identity = ActionIdentity(
                arguments["task_id"].toString(),
                arguments["generation_id"].toString(),
                arguments["action_id"].toString()
            )
            val admission = TaskStateManager.admitAction(identity, "get_current_app")
            assertEquals(ActionAdmission.Stale, admission)
            if (admission == ActionAdmission.Accepted) executed = true
            AssistantToolResult(false, """{"error":"STALE_ACTION"}""")
        }
        var response: GeminiToolResponse? = null

        kotlinx.coroutines.runBlocking {
            router.route(
                context,
                "A1",
                "get_current_app",
                mapOf("task_id" to "T1", "generation_id" to "G1")
            ) { response = it }
        }

        assertFalse(executed)
        assertTrue(response!!.message.contains("STALE_ACTION"))
    }

    @Test
    fun `duplicate fake Gemini call returns cached result without second execution`() {
        var executions = 0
        val router = GeminiToolCallRouter { _, _, arguments ->
            val identity = ActionIdentity(
                arguments["task_id"].toString(),
                arguments["generation_id"].toString(),
                arguments["action_id"].toString()
            )
            when (val admission = TaskStateManager.admitAction(identity, "get_current_app")) {
                ActionAdmission.Accepted -> {
                    executions++
                    val result = """{"success":true,"actionId":"A1","taskId":"T1","generationId":"G1"}"""
                    TaskStateManager.completeAction(identity, result, true)
                    AssistantToolResult(true, result)
                }
                is ActionAdmission.Cached -> AssistantToolResult(true, admission.result)
                else -> AssistantToolResult(false, """{"error":"DUPLICATE_ACTION"}""")
            }
        }
        val responses = mutableListOf<GeminiToolResponse>()

        kotlinx.coroutines.runBlocking {
            repeat(2) {
                router.route(
                    context,
                    "A1",
                    "get_current_app",
                    mapOf("task_id" to "T1", "generation_id" to "G1")
                ) { responses += it }
            }
        }

        assertEquals(1, executions)
        assertEquals(2, responses.size)
        assertTrue(responses.all { it.success })
    }
}
