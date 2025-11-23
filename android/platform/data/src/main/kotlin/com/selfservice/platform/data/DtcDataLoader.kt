package com.selfservice.platform.data

import android.content.res.AssetManager
import java.io.File
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object DtcDataLoader {
    @JvmStatic
    fun loadDatabaseFromPath(path: String): ManufacturerDtcDatabase {
        val file = File(path)
        require(file.exists()) { "DTC database not found: $path" }
        return file.inputStream().use { loadDatabase(it) }
    }

    @JvmStatic
    fun loadDatabase(stream: InputStream): ManufacturerDtcDatabase {
        val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return loadDatabase(json)
    }

    @JvmStatic
    fun loadDatabase(assetManager: AssetManager, assetName: String): ManufacturerDtcDatabase {
        return assetManager.open(assetName).use { loadDatabase(it) }
    }

    @JvmStatic
    fun loadDatabase(json: String): ManufacturerDtcDatabase {
        val root = JSONObject(json)
        val catalogs = LinkedHashMap<String, ManufacturerCatalog>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val rawName = keys.next()
            val array = root.optJSONArray(rawName) ?: continue
            val catalog = parseManufacturer(rawName, array) ?: continue
            catalogs[catalog.key] = catalog
        }
        return finalizeDatabase(catalogs)
    }

    @JvmStatic
    fun merge(
            primary: ManufacturerDtcDatabase,
            overlay: ManufacturerDtcDatabase
    ): ManufacturerDtcDatabase {
        if (primary.isEmpty()) {
            return overlay
        }
        if (overlay.isEmpty()) {
            return primary
        }
        val accumulators = LinkedHashMap<String, CatalogAccumulator>()
        fun absorb(database: ManufacturerDtcDatabase) {
            for (displayName in database.manufacturers()) {
                val normalized = ManufacturerKeyNormalizer.normalize(displayName)
                if (normalized.isEmpty()) {
                    continue
                }
                val accumulator =
                        accumulators.getOrPut(normalized) {
                            CatalogAccumulator(displayName.trim(), LinkedHashMap())
                        }
                val entries = database.list(displayName)
                for (entry in entries) {
                    accumulator.entries[entry.code] = entry.copy()
                }
            }
        }
        absorb(primary)
        absorb(overlay)
        val catalogs = LinkedHashMap<String, ManufacturerCatalog>()
        for ((key, accumulator) in accumulators) {
            if (accumulator.entries.isEmpty()) {
                continue
            }
            val catalogEntries = LinkedHashMap<String, ManufacturerDtcEntry>()
            for (entry in accumulator.entries.values) {
                catalogEntries[entry.code] = entry.copy()
            }
            catalogs[key] =
                    ManufacturerCatalog(
                            displayName = accumulator.displayName.ifBlank { key },
                            key = key,
                            entries = catalogEntries
                    )
        }
        return finalizeDatabase(catalogs)
    }

    private fun parseManufacturer(name: String, array: JSONArray): ManufacturerCatalog? {
        val displayName = name.trim()
        if (displayName.isEmpty()) {
            return null
        }
        val normalizedName = ManufacturerKeyNormalizer.normalize(displayName)
        if (normalizedName.isEmpty()) {
            return null
        }
        val entries = LinkedHashMap<String, ManufacturerDtcEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rawCode = item.optString("code")
            if (rawCode.isBlank()) {
                continue
            }
            val normalizedCode = DtcCodeNormalizer.normalizeOrNull(rawCode) ?: continue
            val description = item.optString("description").trim()
            if (description.isEmpty()) {
                continue
            }
            val sourceValue = item.optString("source").trim()
            val source = if (sourceValue.isEmpty()) "unknown" else sourceValue
            entries.putIfAbsent(
                    normalizedCode,
                    ManufacturerDtcEntry(
                            manufacturer = displayName,
                            code = normalizedCode,
                            description = description,
                            source = source
                    )
            )
        }
        if (entries.isEmpty()) {
            return null
        }
        return ManufacturerCatalog(displayName = displayName, key = normalizedName, entries = entries)
    }

    private fun finalizeDatabase(
            catalogs: Map<String, ManufacturerCatalog>
    ): ManufacturerDtcDatabase {
        if (catalogs.isEmpty()) {
            return ManufacturerDtcDatabase(emptyMap(), emptyMap())
        }
        val frozenCatalogs = LinkedHashMap<String, ManufacturerCatalog>()
        val index = mutableMapOf<String, MutableList<ManufacturerDtcEntry>>()
        catalogs.forEach { (key, catalog) ->
            val frozenEntries = LinkedHashMap<String, ManufacturerDtcEntry>()
            catalog.entries.values.forEach { entry ->
                val copy = entry.copy()
                frozenEntries[copy.code] = copy
                index.getOrPut(copy.code) { mutableListOf() }.add(copy)
            }
            if (frozenEntries.isNotEmpty()) {
                frozenCatalogs[key] =
                        ManufacturerCatalog(
                                displayName = catalog.displayName,
                                key = catalog.key,
                                entries = frozenEntries
                        )
            }
        }
        if (frozenCatalogs.isEmpty()) {
            return ManufacturerDtcDatabase(emptyMap(), emptyMap())
        }
        val frozenIndex = index.mapValues { (_, value) -> value.toList() }
        return ManufacturerDtcDatabase(frozenCatalogs, frozenIndex)
    }

    private data class CatalogAccumulator(
            val displayName: String,
            val entries: LinkedHashMap<String, ManufacturerDtcEntry>
    )
}

data class ManufacturerDtcEntry(
        val manufacturer: String,
        val code: String,
        val description: String,
        val source: String
)

class ManufacturerDtcDatabase internal constructor(
        private val catalogs: Map<String, ManufacturerCatalog>,
        private val entriesByCode: Map<String, List<ManufacturerDtcEntry>>
) {
    fun manufacturers(): List<String> = catalogs.values.map { it.displayName }.sorted()

    fun size(): Int = catalogs.values.sumOf { it.entries.size }

    fun isEmpty(): Boolean = catalogs.isEmpty()

    fun find(manufacturer: String, code: String): ManufacturerDtcEntry? {
        val catalog = catalogs[ManufacturerKeyNormalizer.normalize(manufacturer)] ?: return null
        val entry = catalog.entries[DtcCodeNormalizer.normalizeOrNull(code) ?: return null]
        return entry?.copy()
    }

    fun findAll(code: String): List<ManufacturerDtcEntry> {
        val normalized = DtcCodeNormalizer.normalizeOrNull(code) ?: return emptyList()
        return entriesByCode[normalized]?.map { it.copy() }.orEmpty()
    }

    fun list(manufacturer: String): List<ManufacturerDtcEntry> {
        val catalog = catalogs[ManufacturerKeyNormalizer.normalize(manufacturer)] ?: return emptyList()
        return catalog.entries.values.map { it.copy() }
    }
}

internal data class ManufacturerCatalog(
        val displayName: String,
        val key: String,
        val entries: Map<String, ManufacturerDtcEntry>
)

internal object ManufacturerKeyNormalizer {
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        val lower = raw.trim().lowercase(Locale.US)
        val buffer = StringBuilder(lower.length)
        for (char in lower) {
            if (char in 'a'..'z' || char in '0'..'9') {
                buffer.append(char)
            }
        }
        return buffer.toString()
    }
}

internal object DtcCodeNormalizer {
    fun normalizeOrNull(raw: String): String? {
        if (raw.isBlank()) return null
        val trimmed = raw.trim().uppercase(Locale.US)
        val withoutPrefix = if (trimmed.startsWith("0X")) trimmed.substring(2) else trimmed
        if (withoutPrefix.length != 5) return null
        if (withoutPrefix[0] !in VALID_PREFIX) return null
        if (!withoutPrefix.substring(1).all { it.isDigit() }) return null
        return withoutPrefix
    }

    private val VALID_PREFIX = setOf('P', 'B', 'C', 'U', 'H')
}
