package com.selfservice.kiosk.mdm

import androidx.annotation.VisibleForTesting
import com.selfservice.kiosk.BuildConfig
import java.util.Locale

object MdmRuntimeConfig {

    private val SEPARATORS = Regex("[,;\n\r\t ]+")
    @Volatile private var allowedOverride: Set<String>? = null

    fun allowedPackages(
        rawValue: String = BuildConfig.MDM_ALLOWED_PACKAGES,
        fallbackPackage: String = BuildConfig.APPLICATION_ID
    ): Set<String> {
        allowedOverride?.let { return it }
        val parsed = rawValue
            .split(SEPARATORS)
            .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() }?.lowercase(Locale.US) }
            .toSet()
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return setOf(fallbackPackage.lowercase(Locale.US))
    }

    fun isPackageAllowed(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val normalized = packageName.lowercase(Locale.US)
        return allowedPackages().any { it.equals(normalized, ignoreCase = true) }
    }

    @VisibleForTesting
    fun overrideAllowedPackages(packages: Set<String>?) {
        allowedOverride = packages?.map { it.lowercase(Locale.US) }?.toSet()
    }
}
