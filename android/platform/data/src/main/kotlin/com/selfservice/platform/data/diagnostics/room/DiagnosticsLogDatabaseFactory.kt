package com.selfservice.platform.data.diagnostics.room

import android.content.Context
import androidx.room.Room

object DiagnosticsLogDatabaseFactory {
    fun create(context: Context): DiagnosticsLogDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            DiagnosticsLogDatabase::class.java,
            DiagnosticsLogDatabase.NAME
        ).addMigrations(*DiagnosticsLogDatabaseMigrations.ALL)
            .build()
    }
}
