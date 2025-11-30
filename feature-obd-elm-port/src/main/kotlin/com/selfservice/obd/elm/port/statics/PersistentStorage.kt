package com.selfservice.obd.elm.port.statics

import com.selfservice.obd.elm.port.models.PID

/**
 * In-memory persistent PID cache mirroring the original donor behaviour.
 */
internal object PersistentStorage {
    private val persistentPidStorage = mutableMapOf<String, PID>()

    fun addElement(element: PID?) {
        element?.let { pid ->
            persistentPidStorage[formKey(pid)] = pid
        }
    }

    fun removeElement(element: PID?) {
        element?.let { pid ->
            persistentPidStorage.remove(formKey(pid))
        }
    }

    fun getElement(element: PID?): PID? = element?.let { pid ->
        persistentPidStorage[formKey(pid)]
    }

    fun containsPid(element: PID): Boolean = element.isPersistent && persistentPidStorage.containsKey(formKey(element))

    fun clearAll() {
        persistentPidStorage.clear()
    }

    private fun formKey(pid: PID): String = pid.mode + pid.PID
}
