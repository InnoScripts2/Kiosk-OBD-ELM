package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.platform.data.DtcDataLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManufacturerDictionaryInstallerTest {

    @AfterTest
    fun tearDown() {
        ManufacturerDictionaryInstaller.resetForTests()
    }

    @Test
    fun `ensureInstalledInternal attaches provider only once`() {
        ManufacturerDictionaryInstaller.resetForTests()
        ManufacturerDictionaryInstaller.ensureInstalledInternal {
            DtcDataLoader.loadDatabase(
                """
                {
                  "Toyota": [
                    {"code": "P1500", "description": "Hybrid battery voltage high", "source": "unit"}
                  ]
                }
                """.trimIndent()
            )
        }

        val manufacturers = ObdDictionaryManager.shared.manufacturerList()
        assertEquals(listOf("Toyota"), manufacturers)

        val attempts = AtomicInteger(0)
        ManufacturerDictionaryInstaller.ensureInstalledInternal {
            attempts.incrementAndGet()
            throw AssertionError("loader must not be invoked twice")
        }

        assertEquals(0, attempts.get())
    }

    @Test
    fun `failing loader leaves state uninstalled`() {
        ManufacturerDictionaryInstaller.resetForTests()
        val attempts = AtomicInteger(0)
        ManufacturerDictionaryInstaller.ensureInstalledInternal {
            attempts.incrementAndGet()
            throw IllegalStateException("boom")
        }

        assertEquals(1, attempts.get())
        ManufacturerDictionaryInstaller.ensureInstalledInternal {
            DtcDataLoader.loadDatabase(
                """
                {
                  "BMW": [
                    {"code": "P1472", "description": "Secondary air injection pump", "source": "unit"}
                  ]
                }
                """.trimIndent()
            )
        }

        assertEquals(listOf("BMW"), ObdDictionaryManager.shared.manufacturerList())
    }

        @Test
        fun `ensureInstalledInternal merges overrides`() {
                ManufacturerDictionaryInstaller.resetForTests()

                ManufacturerDictionaryInstaller.ensureInstalledInternal {
                        val base =
                                        DtcDataLoader.loadDatabase(
                                                        """
                                {
                                    "Toyota": [
                                        {"code": "P1500", "description": "Base", "source": "embedded"}
                                    ]
                                }
                                                        """
                                                                        .trimIndent()
                                        )
                        val overrides =
                                        DtcDataLoader.loadDatabase(
                                                        """
                                {
                                    "Toyota": [
                                        {"code": "P1500", "description": "Override", "source": "override"}
                                    ],
                                    "BMW": [
                                        {"code": "P1472", "description": "Secondary air injection pump", "source": "override"}
                                    ]
                                }
                                                        """
                                                                        .trimIndent()
                                        )
                        DtcDataLoader.merge(base, overrides)
                }

                val manufacturers = ObdDictionaryManager.shared.manufacturerList()
                assertEquals(listOf("BMW", "Toyota"), manufacturers)

                val provider = ObdDictionaryManager.shared
                val toyota = provider.lookupManufacturerDtc("Toyota", "P1500")
                assertNotNull(toyota)
                assertEquals("Override", toyota.label)

                val bmw = provider.lookupManufacturerDtc("BMW", "P1472")
                assertNotNull(bmw)
                assertEquals("Secondary air injection pump", bmw.label)
        }

                @Test
                fun `refreshInternal replaces existing provider`() {
                        ManufacturerDictionaryInstaller.resetForTests()

                        ManufacturerDictionaryInstaller.ensureInstalledInternal {
                                DtcDataLoader.loadDatabase(
                                        """
                                        {
                                            "Toyota": [
                                                {"code": "P1500", "description": "Base", "source": "embedded"}
                                            ]
                                        }
                                        """.trimIndent()
                                )
                        }

                        val initial = ObdDictionaryManager.shared.lookupManufacturerDtc("Toyota", "P1500")
                        assertNotNull(initial)
                        assertEquals("Base", initial.label)

                        val refreshed = ManufacturerDictionaryInstaller.refreshInternal {
                                DtcDataLoader.loadDatabase(
                                        """
                                        {
                                            "Toyota": [
                                                {"code": "P1500", "description": "Override", "source": "override"}
                                            ]
                                        }
                                        """.trimIndent()
                                )
                        }

                        assertTrue(refreshed)

                        val updated = ObdDictionaryManager.shared.lookupManufacturerDtc("Toyota", "P1500")
                        assertNotNull(updated)
                        assertEquals("Override", updated.label)
                }
}
