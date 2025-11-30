package com.selfservice.obd.core.dtc

import java.util.LinkedHashMap
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides access to the canonical list of diagnostic trouble codes generated from
 * consolidated CSV sources (dtc-index + brand overrides).
 */
object DtcCatalog {
    private const val INDEX_RESOURCE_PATH = "/com/selfservice/obd/core/dtc/dtc-index.json"
    private const val BRAND_RESOURCE_DIR = "/com/selfservice/obd/core/dtc/dtc-brand-overrides"

    private val catalog: CatalogIndex by lazy { loadCatalog() }
    private val brandRepository: BrandOverrideRepository by lazy { BrandOverrideRepository(BRAND_RESOURCE_DIR) }

    fun definitions(): List<ObdDtcDefinition> = catalog.definitions.map { it.copy() }

    fun size(): Int = catalog.definitions.size

    fun find(code: String): ObdDtcDefinition? {
        val normalized = normalizeCode(code)
        return catalog.byCode[normalized]?.copy()
    }

    fun listSystems(): List<ObdDtcDefinition.System> = catalog.systemList.toList()

    fun listBySystem(system: ObdDtcDefinition.System): List<ObdDtcDefinition> =
        catalog.systemBuckets[system].orEmpty().map { it.copy() }

    fun listBrands(): List<String> = catalog.brandNames.toList()

    fun findBrandOverride(brand: String, code: String): DtcBrandOverride? =
        brandRepository.find(brand, normalizeCode(code))

    fun findBrandOverrides(code: String): List<DtcBrandOverride> {
        val normalized = normalizeCode(code)
        val brands = catalog.byCode[normalized]?.brandOverrides.orEmpty()
        if (brands.isEmpty()) {
            return emptyList()
        }
        return brandRepository.findAll(brands, normalized)
    }

    fun listBrandOverrides(brand: String): List<DtcBrandOverride> =
        brandRepository.list(brand)

    fun countBrandOverrides(brand: String): Int = brandRepository.size(brand)

    private fun loadCatalog(): CatalogIndex {
        val stream = requireNotNull(DtcCatalog::class.java.getResourceAsStream(INDEX_RESOURCE_PATH)) {
            "Resource $INDEX_RESOURCE_PATH is missing from classpath"
        }
        val json = stream.bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val array = root.getJSONArray("entries")
        val definitions = ArrayList<ObdDtcDefinition>(array.length())
        val brandNames = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val definition = item.toDefinition()
            definitions += definition
            brandNames.addAll(definition.brandOverrides)
        }
        val sortedDefinitions = definitions.sortedBy { it.code }
        val systemBuckets = sortedDefinitions.groupBy { it.system }
            .mapValues { (_, list) -> list.sortedBy { it.code } }
        val systemList = systemBuckets.keys.sortedBy { it.value }
        val definitionIndex = sortedDefinitions.associateBy { it.code }
        val brandList = brandNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return CatalogIndex(
            definitions = sortedDefinitions,
            byCode = definitionIndex,
            systemBuckets = systemBuckets,
            systemList = systemList,
            brandNames = brandList
        )
    }

    private fun JSONObject.toDefinition(): ObdDtcDefinition {
        val code = normalizeCode(getString("code"))
        val system = ObdDtcDefinition.System.fromValue(getString("system"))
        val status = ObdDtcDefinition.Availability.fromValue(optStringOrNull("status"))
        val details = optJSONObject("details")?.toDetails()
        val sources = optJSONArray("sources").toStringList()
        val brandOverrides = optJSONArray("brandOverrides").toStringList()
        val label = optString("description").takeIf { it.isNotBlank() }
            ?: details?.preferred
            ?: code
        return ObdDtcDefinition(
            code = code,
            system = system,
            label = label,
            status = status,
            sources = sources,
            details = details,
            brandOverrides = brandOverrides
        )
    }

    private fun JSONObject.toDetails(): ObdDtcDefinition.Details? {
        val preferred = optStringOrNull("preferred")
        val base = optStringOrNull("base")
        val app = optStringOrNull("app")
        if (preferred == null && base == null && app == null) {
            return null
        }
        return ObdDtcDefinition.Details(
            preferred = preferred,
            base = base,
            app = app
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null || length() == 0) {
            return emptyList()
        }
        val list = ArrayList<String>(length())
        for (index in 0 until length()) {
            val value = optString(index)
            if (!value.isNullOrBlank()) {
                list.add(value.trim())
            }
        }
        return list.distinct()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun normalizeCode(code: String): String {
        val trimmed = code.trim().uppercase(Locale.US)
        val withoutPrefix = if (trimmed.startsWith("0X")) trimmed.substring(2) else trimmed
        require(withoutPrefix.length == 5) { "Invalid DTC code length for $code" }
        require(withoutPrefix[0] in "PCBHU") { "Invalid DTC prefix for $code" }
        require(withoutPrefix.substring(1).all { it.isDigit() || it in 'A'..'F' }) {
            "Invalid DTC suffix for $code"
        }
        return withoutPrefix
    }

    private data class CatalogIndex(
        val definitions: List<ObdDtcDefinition>,
        val byCode: Map<String, ObdDtcDefinition>,
        val systemBuckets: Map<ObdDtcDefinition.System, List<ObdDtcDefinition>>,
        val systemList: List<ObdDtcDefinition.System>,
        val brandNames: List<String>
    )

    private class BrandOverrideRepository(
        private val resourceDir: String
    ) {
        private val cache = mutableMapOf<String, BrandCatalog?>()
        private val lock = Any()

        fun find(brand: String, code: String): DtcBrandOverride? {
            if (brand.isBlank()) return null
            val catalog = loadCatalog(slugifyBrand(brand)) ?: return null
            return catalog.lookup(code)
        }

        fun findAll(brands: Collection<String>, code: String): List<DtcBrandOverride> {
            if (brands.isEmpty()) return emptyList()
            val overrides = ArrayList<DtcBrandOverride>(brands.size)
            val seen = HashSet<String>()
            val normalized = normalizeCode(code)
            for (brand in brands) {
                val slug = slugifyBrand(brand)
                if (!seen.add(slug)) {
                    continue
                }
                val catalog = loadCatalog(slug) ?: continue
                val override = catalog.lookup(normalized) ?: continue
                overrides += override
            }
            return overrides
        }

        fun list(brand: String): List<DtcBrandOverride> {
            if (brand.isBlank()) return emptyList()
            val catalog = loadCatalog(slugifyBrand(brand)) ?: return emptyList()
            return catalog.entries.values.map { it.copy() }
        }

        fun size(brand: String): Int {
            if (brand.isBlank()) return 0
            val catalog = loadCatalog(slugifyBrand(brand)) ?: return 0
            return catalog.entries.size
        }

        private fun loadCatalog(slug: String): BrandCatalog? {
            synchronized(lock) {
                cache[slug]?.let { return it }
                val catalog = readCatalog(slug)
                cache[slug] = catalog
                return catalog
            }
        }

        private fun readCatalog(slug: String): BrandCatalog? {
            val resourcePath = "$resourceDir/$slug.json"
            val stream = DtcCatalog::class.java.getResourceAsStream(resourcePath) ?: return null
            val json = stream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val brandName = root.optString("brand").ifBlank { slug }
            val entriesArray = root.optJSONArray("entries") ?: return null
            val entries = LinkedHashMap<String, DtcBrandOverride>()
            for (index in 0 until entriesArray.length()) {
                val item = entriesArray.optJSONObject(index) ?: continue
                val code = item.optString("code").takeIf { it.isNotBlank() } ?: continue
                val normalized = normalizeCode(code)
                val description = item.optString("description").takeIf { it.isNotBlank() } ?: continue
                val systemValue = item.optString("system").takeIf { it.isNotBlank() }
                val system = systemValue?.let {
                    runCatching { ObdDtcDefinition.System.fromValue(it) }.getOrNull()
                } ?: systemFromPrefix(normalized)
                val source = item.optString("source").takeIf { it.isNotBlank() }
                entries[normalized] = DtcBrandOverride(
                    brand = brandName,
                    code = normalized,
                    system = system,
                    description = description,
                    source = source
                )
            }
            if (entries.isEmpty()) {
                return null
            }
            return BrandCatalog(displayName = brandName, slug = slug, entries = entries)
        }
    }

    private data class BrandCatalog(
        val displayName: String,
        val slug: String,
        val entries: Map<String, DtcBrandOverride>
    ) {
        fun lookup(code: String): DtcBrandOverride? = entries[normalizeCode(code)]?.copy()
    }

    private fun slugifyBrand(raw: String): String {
        if (raw.isBlank()) {
            return "brand"
        }
        val lower = raw.trim().lowercase(Locale.US)
        val sanitized = lower.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (sanitized.isEmpty()) "brand" else sanitized
    }

    private fun systemFromPrefix(code: String): ObdDtcDefinition.System = when (code.firstOrNull()) {
        'B' -> ObdDtcDefinition.System.BODY
        'C' -> ObdDtcDefinition.System.CHASSIS
        'U' -> ObdDtcDefinition.System.NETWORK
        else -> ObdDtcDefinition.System.POWERTRAIN
    }
}
