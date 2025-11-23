package com.selfservice.kiosk.supabase

import android.util.Patterns
import com.selfservice.kiosk.BuildConfig

/**
 * Provides Supabase REST credentials sourced from packaged configuration at runtime. Credentials
 * are optional so that development builds can operate without remote connectivity.
 */
data class SupabaseCredentials(val restUrl: String, val serviceKey: String) {
    init {
        require(restUrl.isNotBlank()) { "Supabase REST url must not be blank" }
        require(serviceKey.isNotBlank()) { "Supabase service key must not be blank" }
    }

    fun tableEndpoint(table: String): String =
            restUrl.trimEnd('/') + "/rest/v1/" + table
}

object SupabaseCredentialsProvider {

    fun fromBuildConfig(): SupabaseCredentials? {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_SERVICE_KEY.trim()
        if (url.isEmpty() || key.isEmpty()) {
            return null
        }
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            return null
        }
        return SupabaseCredentials(restUrl = url, serviceKey = key)
    }
}
