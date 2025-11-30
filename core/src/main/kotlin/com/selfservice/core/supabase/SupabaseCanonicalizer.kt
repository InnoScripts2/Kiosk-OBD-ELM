package com.selfservice.core.supabase

import org.json.JSONArray
import org.json.JSONObject

/** Utility functions for producing deterministic JSON encodings for Supabase payload hashing. */
object SupabaseCanonicalizer {

    fun canonicalize(value: Any?): String =
        when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is String -> JSONObject.quote(value)
            is Enum<*> -> JSONObject.quote(value.name)
            is JSONObject -> canonicalizeJSONObject(value)
            is JSONArray -> canonicalizeJSONArray(value)
            is Map<*, *> -> canonicalizeMap(value)
            is Iterable<*> -> canonicalizeIterable(value)
            is Array<*> -> canonicalizeIterable(value.asList())
            else -> JSONObject.quote(value.toString())
        }

    private fun canonicalizeMap(map: Map<*, *>): String {
        val entries = map.entries
            .filter { it.key != null }
            .sortedBy { it.key.toString() }
        return buildString {
            append('{')
            entries.forEachIndexed { index, entry ->
                append(JSONObject.quote(entry.key.toString()))
                append(':')
                append(canonicalize(entry.value))
                if (index < entries.lastIndex) {
                    append(',')
                }
            }
            append('}')
        }
    }

    private fun canonicalizeIterable(collection: Iterable<*>): String = buildString {
        append('[')
        val iterator = collection.iterator()
        var first = true
        while (iterator.hasNext()) {
            if (!first) {
                append(',')
            }
            first = false
            append(canonicalize(iterator.next()))
        }
        append(']')
    }

    private fun canonicalizeJSONObject(json: JSONObject): String {
        val keys = json.keys().asSequence().toList().sorted()
        return buildString {
            append('{')
            keys.forEachIndexed { index, key ->
                append(JSONObject.quote(key))
                append(':')
                append(canonicalize(json.get(key)))
                if (index < keys.lastIndex) {
                    append(',')
                }
            }
            append('}')
        }
    }

    private fun canonicalizeJSONArray(array: JSONArray): String = buildString {
        append('[')
        for (index in 0 until array.length()) {
            if (index > 0) {
                append(',')
            }
            append(canonicalize(array.get(index)))
        }
        append(']')
    }
}
