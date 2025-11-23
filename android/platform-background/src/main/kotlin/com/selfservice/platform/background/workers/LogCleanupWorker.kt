package com.selfservice.platform.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Worker for cleaning up old log files
 * Removes logs older than 30 days
 */
class LogCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val logsDir = File(applicationContext.filesDir, "logs")
            if (!logsDir.exists()) {
                return@withContext Result.success()
            }
            
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            var deletedCount = 0
            
            logsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        const val RETENTION_DAYS = 30L
        const val WORK_NAME = "log_cleanup"
    }
}
