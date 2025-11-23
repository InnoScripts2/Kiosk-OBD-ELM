package com.selfservice.kiosk.mdm

import android.util.Log
import java.util.ArrayList
import org.json.JSONArray
import org.json.JSONObject

internal object MdmCommandPayloadParser {

    fun parseJsonMap(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { raw.toJsonMap() }
            .getOrElse { error ->
                Log.w(TAG, "Failed to parse MDM payload", error)
                emptyMap()
            }
    }

    private fun String.toJsonMap(): Map<String, Any?> = JSONObject(this).toCompatMap()

    private fun JSONObject.toCompatMap(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>(length())
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = get(key).toCompatAny()
        }
        return map
    }

    private fun JSONArray.toCompatList(): List<Any?> {
        val list = ArrayList<Any?>(length())
        for (index in 0 until length()) {
            list += get(index).toCompatAny()
        }
        return list
    }

    private fun Any?.toCompatAny(): Any? = when (this) {
        JSONObject.NULL -> null
        is JSONObject -> this.toCompatMap()
        is JSONArray -> this.toCompatList()
        else -> this
    }
}

private const val TAG = "MdmCommandPayloadParser"
