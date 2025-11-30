package com.selfservice.platform.data.diagnostics.profile

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Загружает JSON-профили расчётных метрик из assets.
 */
class DiagnosticsMetricDefinitionAssetSource(
    private val assetManager: AssetManager,
    private val assetName: String = DEFAULT_ASSET_NAME
) {

    fun load(): List<DiagnosticsMetricDefinition> {
        return runCatching {
            assetManager.open(assetName).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val json = reader.readText()
                    DiagnosticsMetricDefinitionJsonParser.parse(json)
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        const val DEFAULT_ASSET_NAME: String = "diagnostics_metric_profiles.json"
    }
}
