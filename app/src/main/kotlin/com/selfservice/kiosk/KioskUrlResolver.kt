package com.selfservice.kiosk

import java.util.LinkedHashSet
import java.util.Locale

internal data class KioskUrlOptions(
    val primary: String?,
    val remote: String? = null,
    val dev: String?,
    val fallback: String?
)

internal data class KioskUrlResolution(
    val initialUrl: String,
    val fallbackChain: List<String>
)

internal object KioskUrlResolver {

    internal const val DEFAULT_URL = "about:blank"

    fun resolve(appMode: String, options: KioskUrlOptions): KioskUrlResolution {
        val sanitizedOptions = options.sanitize()
        val normalizedMode = appMode.trim().uppercase(Locale.ROOT)
        val initial = pickInitialUrl(normalizedMode, sanitizedOptions)
        val fallbackChain = pickFallbackChain(normalizedMode, sanitizedOptions, initial)
        return KioskUrlResolution(initial, fallbackChain)
    }

    private fun KioskUrlOptions.sanitize(): KioskUrlOptions {
        fun sanitize(value: String?): String? = value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return KioskUrlOptions(
            primary = sanitize(primary),
            remote = sanitize(remote),
            dev = sanitize(dev),
            fallback = sanitize(fallback)
        )
    }

    private fun pickInitialUrl(mode: String, options: KioskUrlOptions): String {
        val priority = when (mode) {
            "DEV" -> listOf(options.dev, options.primary, options.remote, options.fallback)
            "QA", "STAGE", "STAGING", "UAT" -> listOf(
                options.primary,
                options.remote,
                options.fallback,
                options.dev
            )
            else -> listOf(options.primary, options.remote, options.fallback, options.dev)
        }
        return priority.firstOrNull { !it.isNullOrEmpty() } ?: DEFAULT_URL
    }

    private fun pickFallbackChain(
        mode: String,
        options: KioskUrlOptions,
        initial: String
    ): List<String> {
        val queue = LinkedHashSet<String>()
        fun append(url: String?) {
            if (!url.isNullOrEmpty() && !url.equals(initial, ignoreCase = true)) {
                queue.add(url)
            }
        }

        val priority = when (mode) {
            "DEV" -> listOf(options.primary, options.remote, options.fallback)
            else -> listOf(options.remote, options.fallback, options.dev, options.primary)
        }
        priority.forEach(::append)
        return queue.toList()
    }
}
