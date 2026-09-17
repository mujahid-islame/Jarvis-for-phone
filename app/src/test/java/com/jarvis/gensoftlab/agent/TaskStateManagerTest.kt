package com.jarvis.gensoftlab.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class TaskStateManagerTest {
    @Before
    fun reset() {
        TaskStateManager.resetForTests()
    }

    @Test
    fun `old generation is stale`() {
        TaskStateManager.startTask("T1", "G2", "A5")

        assertEquals(
            ActionAdmission.Stale,
            TaskStateManager.admitAction(ActionIdentity("T1", "G1", "A5"), "tap")
        )
    }

    @Test
    fun `duplicate in flight is rejected`() {
        val identity = ActionIdentity("T1", "G1", "A1")

        assertEquals(ActionAdmission.Accepted, TaskStateManager.admitAction(identity, "tap"))
        assertEquals(ActionAdmission.DuplicateInFlight, TaskStateManager.admitAction(identity, "tap"))
    }

    @Test
    fun `completed action returns cached result`() {
        val identity = ActionIdentity("T1", "G1", "A1")
        TaskStateManager.admitAction(identity, "tap")
        TaskStateManager.completeAction(identity, """{"success":true}""", true)

        assertEquals(
            ActionAdmission.Cached("""{"success":true}"""),
            TaskStateManager.admitAction(identity, "tap")
        )
    }

    @Test
    fun `cancelled task rejects action`() {
        val identity = ActionIdentity("T1", "G1", "A1")
        TaskStateManager.admitAction(identity, "tap")
        assertTrue(TaskStateManager.cancelTask("T1", "G1"))

        assertEquals(ActionAdmission.Stale, TaskStateManager.admitAction(identity, "tap"))
    }

    @Test
    fun `terminal state cannot return to running`() {
        TaskStateManager.startTask("T1", "G1", "A1")
        assertTrue(TaskStateManager.updateStatus(TaskStatus.RUNNING))
        assertTrue(TaskStateManager.updateStatus(TaskStatus.COMPLETED))

        assertTrue(!TaskStateManager.updateStatus(TaskStatus.RUNNING))
    }

    @Test
    fun `concurrent admission accepts exactly one action`() {
        val identity = ActionIdentity("T1", "G1", "A1")
        val admissions = ConcurrentLinkedQueue<ActionAdmission>()
        val threads = (1..2).map {
            Thread { admissions.add(TaskStateManager.admitAction(identity, "tap")) }.apply { start() }
        }
        threads.forEach(Thread::join)
        assertEquals(1, admissions.count { it == ActionAdmission.Accepted })
        assertEquals(ActionLifecycle.IN_FLIGHT, TaskStateManager.actionState(identity))
    }
}
