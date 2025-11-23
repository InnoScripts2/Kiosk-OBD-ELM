package com.selfservice.kiosk.dictionary

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.selfservice.obd.core.dictionary.DictionaryRevisionSnapshot
import com.selfservice.obd.core.dtc.DtcDictionaryRevision
import com.selfservice.obd.core.pid.PidDictionaryRevision
import java.io.File
import java.nio.file.Files
import kotlin.test.assertFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class LocalDictionaryUpdateProviderTest {

    private lateinit var root: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        root = Files.createTempDirectory("local-dictionary-provider").toFile()
        context = object : ContextWrapper(Application()) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun returnsNullWhenPayloadMissing() = runBlocking {
        val provider = LocalDictionaryUpdateProvider(context)

        val snapshot = snapshot(pidVersion = null, dtcVersion = null, refreshedAt = 0L)

        val batch = provider.fetchUpdate(snapshot)

        assertNull(batch)
    }

    @Test
    fun returnsBatchWhenVersionsAdvance() = runBlocking {
        val provider = LocalDictionaryUpdateProvider(context)
        val directory = File(root, "dictionaries").apply { mkdirs() }
        val json = """
            {
              "source": "dev-local",
              "updated_at_ms": 1700000000000,
              "pid": {
                "version": "pid-2025-11-16",
                "definitions": [
                  { "mode": "01", "pid": "0C", "label": "Engine RPM" }
                ]
              },
              "dtc": {
                "version": "dtc-2025-11-16",
                "definitions": [
                  { "code": "P0123", "system": "powertrain", "label": "Throttle Position" }
                ]
              }
            }
        """.trimIndent()
        File(directory, "dictionary_updates.json").writeText(json)

        val snapshot = snapshot(pidVersion = "pid-2025-10-01", dtcVersion = "dtc-2025-10-01", refreshedAt = 1_600_000_000_000)

        val batch = requireNotNull(provider.fetchUpdate(snapshot))

        assertEquals("dev-local", batch.source)
        assertEquals("pid-2025-11-16", batch.pidVersionLabel)
        assertEquals("dtc-2025-11-16", batch.dtcVersionLabel)
        assertEquals(1, batch.pidDefinitions?.size)
        assertEquals("Engine RPM", batch.pidDefinitions?.first()?.label)
        assertEquals(1, batch.dtcDefinitions?.size)
        assertEquals("P0123", batch.dtcDefinitions?.first()?.code)
    }

    @Test
    fun returnsNullWhenSnapshotAlreadyCurrent() = runBlocking {
        val provider = LocalDictionaryUpdateProvider(context)
        val directory = File(root, "dictionaries").apply { mkdirs() }
        val json = """
            {
              "source": "dev-local",
              "updated_at_ms": 1700000000000,
              "pid": {
                "version": "pid-2025-11-15",
                "definitions": [
                  { "mode": "01", "pid": "05", "label": "Coolant Temperature" }
                ]
              }
            }
        """.trimIndent()
        File(directory, "dictionary_updates.json").writeText(json)

        val snapshot = snapshot(pidVersion = "pid-2025-11-15", dtcVersion = null, refreshedAt = 1_800_000_000_000)

        val first = provider.fetchUpdate(snapshot)
        // first fetch returns null because snapshot is considered fresher than payload timestamp
        assertNull(first)
    }

    @Test
    fun returnsNullOnDuplicatePayload() = runBlocking {
        val provider = LocalDictionaryUpdateProvider(context)
        val directory = File(root, "dictionaries").apply { mkdirs() }
        val json = """
            {
              "pid": {
                "version": "pid-2025-11-16",
                "definitions": [
                  { "mode": "01", "pid": "0C", "label": "Engine RPM" }
                ]
              }
            }
        """.trimIndent()
        File(directory, "dictionary_updates.json").writeText(json)

        val snapshot = snapshot(pidVersion = "pid-2025-10-01", dtcVersion = null, refreshedAt = 1_600_000_000_000)

        val first = provider.fetchUpdate(snapshot)
        val second = provider.fetchUpdate(snapshot)

        assertFalse(first == null)
        assertNull(second)
    }

    private fun snapshot(
            pidVersion: String?,
            dtcVersion: String?,
            refreshedAt: Long
    ): DictionaryRevisionSnapshot {
        val pid =
                PidDictionaryRevision(
                        source = "test",
                        versionLabel = pidVersion,
                        refreshedAtMillis = refreshedAt,
                        entryCount = 0
                )
        val dtc =
                DtcDictionaryRevision(
                        source = "test",
                        versionLabel = dtcVersion,
                        refreshedAtMillis = refreshedAt,
                        entryCount = 0
                )
        return DictionaryRevisionSnapshot(pid = pid, dtc = dtc)
    }
}
