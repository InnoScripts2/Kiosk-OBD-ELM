package com.selfservice.platform.data.diagnostics

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object MetadataJsonCodec {
    fun encode(metadata: Map<String, Any?>): String {
        if (metadata.isEmpty()) {
            return "{}"
        }
        val root = JSONObject()
        metadata.forEach { (key, value) ->
            root.put(key, toJsonValue(value))
        }
        return root.toString()
    }

    fun decode(json: String): Map<String, Any?> {
        if (json.isBlank()) {
            return emptyMap()
        }
        return try {
            fromJsonObject(JSONObject(json))
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    private fun toJsonValue(value: Any?): Any? =
        when (value) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, element) ->
                    if (key != null) {
                        put(key.toString(), toJsonValue(element))
                    }
                }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { element -> put(toJsonValue(element)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { element -> put(toJsonValue(element)) }
            }
            is Enum<*> -> value.name
            else -> value.toString()
        }

    private fun fromJsonObject(value: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = fromJsonValue(value.get(key))
        }
        return result
    }

    private fun fromJsonValue(value: Any?): Any? =
        when (value) {
            JSONObject.NULL -> null
            is JSONObject -> fromJsonObject(value)
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    add(fromJsonValue(value.get(index)))
                }
            }
            else -> value
        }
}
