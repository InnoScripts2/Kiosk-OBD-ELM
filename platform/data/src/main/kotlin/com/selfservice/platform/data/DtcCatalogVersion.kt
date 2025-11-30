package com.selfservice.platform.data

import android.content.res.AssetManager
import java.io.InputStream
import org.json.JSONObject

/**
 * Метаданные версии каталога DTC.
 * 
 * Содержит информацию о версии, дате сборки, количестве записей
 * и источниках данных для каталогов DTC.
 */
data class DtcCatalogVersion(
    val version: String,
    val buildTimestamp: String,
    val genericEntries: Int,
    val manufacturerEntries: Int,
    val manufacturers: List<String>,
    val sources: CatalogSources
) {
    data class CatalogSources(
        val csharpCatalog: String,
        val dtcMapping: String,
        val manufacturerDirectories: List<String>
    )

    companion object {
        /**
         * Загружает метаданные версии каталога из assets.
         * 
         * @param assetManager AssetManager для доступа к assets
         * @param assetName имя файла с метаданными (по умолчанию catalog_version.json)
         * @return DtcCatalogVersion с метаданными каталога
         */
        @JvmStatic
        fun load(assetManager: AssetManager, assetName: String = "catalog_version.json"): DtcCatalogVersion {
            return assetManager.open(assetName).use { load(it) }
        }

        /**
         * Загружает метаданные версии каталога из потока.
         */
        @JvmStatic
        fun load(stream: InputStream): DtcCatalogVersion {
            val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(json)
        }

        /**
         * Парсит JSON строку с метаданными версии каталога.
         */
        @JvmStatic
        fun parse(json: String): DtcCatalogVersion {
            val root = JSONObject(json)
            val sourcesObj = root.getJSONObject("sources")
            
            val manufacturers = mutableListOf<String>()
            val manufacturersArray = root.getJSONArray("manufacturers")
            for (i in 0 until manufacturersArray.length()) {
                manufacturers.add(manufacturersArray.getString(i))
            }
            
            val manufacturerDirs = mutableListOf<String>()
            val dirsArray = sourcesObj.getJSONArray("manufacturer_directories")
            for (i in 0 until dirsArray.length()) {
                manufacturerDirs.add(dirsArray.getString(i))
            }
            
            val sources = CatalogSources(
                csharpCatalog = sourcesObj.getString("csharp_catalog"),
                dtcMapping = sourcesObj.getString("dtc_mapping"),
                manufacturerDirectories = manufacturerDirs
            )
            
            return DtcCatalogVersion(
                version = root.getString("version"),
                buildTimestamp = root.getString("build_timestamp"),
                genericEntries = root.getInt("generic_entries"),
                manufacturerEntries = root.getInt("manufacturer_entries"),
                manufacturers = manufacturers,
                sources = sources
            )
        }
    }

    /**
     * Возвращает краткую информацию о версии каталога для логирования.
     */
    fun toLogString(): String {
        return "DTC Catalog v$version (built $buildTimestamp): " +
               "$genericEntries generic + $manufacturerEntries manufacturer entries " +
               "from ${manufacturers.size} manufacturers"
    }
}
