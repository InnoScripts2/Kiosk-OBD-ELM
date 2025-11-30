@file:Suppress("DEPRECATION")

package com.selfservice.kiosk.supabase

@Deprecated(
    message = "Use shared SupabaseCanonicalizer from :core module",
    replaceWith = ReplaceWith(
        expression = "SupabaseCanonicalizer",
        imports = ["com.selfservice.core.supabase.SupabaseCanonicalizer"]
    )
)
typealias SupabaseCanonicalizer = com.selfservice.core.supabase.SupabaseCanonicalizer
