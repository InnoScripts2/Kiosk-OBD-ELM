package com.selfservice.kiosk.mdm

import java.util.ArrayDeque

/**
 * Bounded in-memory buffer that keeps commands received from MDM while the
 * runtime infrastructure (e.g. Supabase writer / manager) is still starting.
 */
internal class MdmCommandQueue(
    private val maxSize: Int
) {

    init {
        require(maxSize >= 0) { "maxSize must be >= 0" }
    }

    private val lock = Any()
    private val queue = ArrayDeque<MdmCommand>()

    fun enqueue(command: MdmCommand): Boolean {
        if (maxSize == 0) return false
        synchronized(lock) {
            if (queue.size >= maxSize) {
                queue.removeFirst()
            }
            queue.addLast(command)
        }
        return true
    }

    fun drain(consumer: (MdmCommand) -> Unit): Int {
        val drained = mutableListOf<MdmCommand>()
        synchronized(lock) {
            if (queue.isEmpty()) return 0
            drained.addAll(queue)
            queue.clear()
        }
        drained.forEach(consumer)
        return drained.size
    }

    fun size(): Int = synchronized(lock) { queue.size }

    fun snapshot(maxEntries: Int = Int.MAX_VALUE): List<MdmCommand> {
        if (maxEntries <= 0) return emptyList()
        return synchronized(lock) {
            if (queue.isEmpty()) return emptyList()
            val limit = minOf(maxEntries, queue.size)
            val copy = ArrayList<MdmCommand>(limit)
            val iterator = queue.iterator()
            repeat(limit) {
                if (iterator.hasNext()) {
                    copy.add(iterator.next())
                }
            }
            copy
        }
    }
}
