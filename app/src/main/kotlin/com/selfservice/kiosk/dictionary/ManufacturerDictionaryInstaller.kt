package com.selfservice.kiosk.dictionary

import android.content.Context
import android.util.Log
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.dtc.ManufacturerDtcProvider
import com.selfservice.platform.data.DtcDataLoader
import com.selfservice.platform.data.ManufacturerDtcDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Подключает каталог фирменных DTC к [ObdDictionaryManager.shared] единожды на процесс.
 * Каталог читается из assets лениво, повторные вызовы ничего не делают.
 */
object ManufacturerDictionaryInstaller {

    private const val TAG = "ManufacturerDtc"
    private const val DEFAULT_ASSET_NAME = "dtc_database.json"
    private const val OVERRIDE_SUBDIR = "dictionaries"
    private const val OVERRIDE_FILE_NAME = "manufacturer_dtc_overrides.json"

    private val installed = AtomicBoolean(false)
    private val lock = Any()

    fun ensureInstalled(context: Context, assetName: String = DEFAULT_ASSET_NAME) {
        ensureInstalledInternal { buildDatabase(context, assetName) }
    }

    internal fun ensureInstalledInternal(loader: () -> ManufacturerDtcDatabase) {
        if (installed.get()) {
            return
        }
        synchronized(lock) {
            if (installed.get()) {
                return
            }
            installLocked(loader)
        }
    }

    fun refresh(context: Context, assetName: String = DEFAULT_ASSET_NAME): Boolean {
        return refreshInternal { buildDatabase(context, assetName) }
    }

    internal fun refreshInternal(loader: () -> ManufacturerDtcDatabase): Boolean {
        return synchronized(lock) { installLocked(loader) }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            installed.set(false)
            ObdDictionaryManager.shared.detachManufacturerProvider()
        }
    }

    private fun buildDatabase(context: Context, assetName: String): ManufacturerDtcDatabase {
        val base = DtcDataLoader.loadDatabase(context.assets, assetName)
        val overrides = loadOverrides(context)
        if (overrides != null && !overrides.isEmpty()) {
            logInfo(
                    "Applying manufacturer DTC overrides " +
                            "entries=${overrides.size()} manufacturers=${overrides.manufacturers().size}"
            )
            return DtcDataLoader.merge(base, overrides)
        }
        return base
    }

    private fun loadOverrides(context: Context): ManufacturerDtcDatabase? {
        val directory = File(context.filesDir, OVERRIDE_SUBDIR)
        val file = File(directory, OVERRIDE_FILE_NAME)
        if (!file.exists() || !file.isFile) {
            return null
        }
        return try {
            DtcDataLoader.loadDatabaseFromPath(file.absolutePath)
        } catch (error: Throwable) {
            logWarn("Unable to load manufacturer DTC overrides", error)
            null
        }
    }

    private fun installLocked(loader: () -> ManufacturerDtcDatabase): Boolean {
        val database = try {
            loader()
        } catch (error: Throwable) {
            logWarn("Unable to load manufacturer DTC catalog", error)
            return false
        }
        val manufacturers = database.manufacturers().size
        ObdDictionaryManager.shared.attachManufacturerProvider(ManufacturerDtcProvider(database))
        installed.set(true)
        logInfo("Attached manufacturer DTC catalog manufacturers=$manufacturers")
        return true
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(message: String, error: Throwable) {
        runCatching { Log.w(TAG, message, error) }
    }
}
