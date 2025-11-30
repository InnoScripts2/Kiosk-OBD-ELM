package com.selfservice.kiosk.mdm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdmCommandQueueTest {

    @Test
    fun `enqueues and drains in order`() {
        val queue = MdmCommandQueue(maxSize = 4)
        val commands = (1..3).map { index ->
            MdmCommand(id = "cmd-$index", type = "noop", payload = mapOf("value" to index))
        }
        commands.forEach { command ->
            assertTrue(queue.enqueue(command))
        }
        val drained = mutableListOf<MdmCommand>()
        val drainedCount = queue.drain { drained += it }
        assertEquals(commands, drained)
        assertEquals(commands.size, drainedCount)
        assertEquals(0, queue.size())
    }

    @Test
    fun `oldest commands are dropped when capacity exceeded`() {
        val queue = MdmCommandQueue(maxSize = 2)
        assertTrue(queue.enqueue(MdmCommand(id = "a", type = "noop")))
        assertTrue(queue.enqueue(MdmCommand(id = "b", type = "noop")))
        assertTrue(queue.enqueue(MdmCommand(id = "c", type = "noop")))

        val drained = mutableListOf<String>()
        queue.drain { drained += it.id }
        assertEquals(listOf("b", "c"), drained)
    }

    @Test
    fun `zero capacity refuses commands`() {
        val queue = MdmCommandQueue(maxSize = 0)
        assertFalse(queue.enqueue(MdmCommand(id = "ignored", type = "noop")))
        assertEquals(0, queue.drain { })
    }

    @Test
    fun `snapshot returns ordered preview without mutating queue`() {
        val queue = MdmCommandQueue(maxSize = 4)
        listOf("a", "b", "c").forEach { id ->
            assertTrue(queue.enqueue(MdmCommand(id = id, type = "noop")))
        }

        val preview = queue.snapshot(maxEntries = 2)
        assertEquals(listOf("a", "b"), preview.map { it.id })
        assertEquals(3, queue.size(), "snapshot should not drain entries")
    }

    @Test
    fun `snapshot with non-positive limit is empty`() {
        val queue = MdmCommandQueue(maxSize = 2)
        assertTrue(queue.enqueue(MdmCommand(id = "only", type = "noop")))

        assertEquals(emptyList(), queue.snapshot(maxEntries = 0))
        assertEquals(emptyList(), queue.snapshot(maxEntries = -1))
    }
}
