package com.selfservice.kiosk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KioskUrlResolverTest {

    @Test
    fun `dev mode prefers dev url with primary fallback`() {
        val resolution = KioskUrlResolver.resolve(
            appMode = "dev",
            options = KioskUrlOptions(
                primary = "https://prod.example.com/ui",
                remote = "https://cdn.example.com/ui",
                dev = "http://10.0.2.2:8080/",
                fallback = "https://fallback.example.com/"
            )
        )

        assertEquals("http://10.0.2.2:8080/", resolution.initialUrl)
        assertEquals(
            listOf(
                "https://prod.example.com/ui",
                "https://cdn.example.com/ui",
                "https://fallback.example.com/"
            ),
            resolution.fallbackChain
        )
    }

    @Test
    fun `prod mode prefers primary with fallback to backup`() {
        val resolution = KioskUrlResolver.resolve(
            appMode = "prod",
            options = KioskUrlOptions(
                primary = "https://prod.example.com/ui",
                remote = "https://cdn.example.com/ui",
                dev = "http://10.0.2.2:8080/",
                fallback = "https://fallback.example.com/"
            )
        )

        assertEquals("https://prod.example.com/ui", resolution.initialUrl)
        assertEquals(
            listOf(
                "https://cdn.example.com/ui",
                "https://fallback.example.com/",
                "http://10.0.2.2:8080/"
            ),
            resolution.fallbackChain
        )
    }

    @Test
    fun `empty inputs fall back to default about blank`() {
        val resolution = KioskUrlResolver.resolve(
            appMode = "prod",
            options = KioskUrlOptions(primary = "  ", remote = null, dev = null, fallback = null)
        )

        assertEquals(KioskUrlResolver.DEFAULT_URL, resolution.initialUrl)
        assertTrue(resolution.fallbackChain.isEmpty())
    }
}
