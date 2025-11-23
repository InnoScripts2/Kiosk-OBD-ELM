package com.selfservice.kiosk.dictionary

import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Polls the local dictionary updates file and notifies listeners when its contents change. Used to
 * trigger background synchronisation whenever a new payload is dropped by an offline workflow.
 */
class LocalDictionaryUpdateWatcher(
        private val scope: CoroutineScope,
        private val directoryResolver: () -> File,
        private val fileName: String = DEFAULT_FILE_NAME,
        private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
        private val ioContext: CoroutineContext = Dispatchers.IO,
        private val onUpdateAvailable: () -> Unit
) : Closeable {

    private val lastSignature = AtomicReference<FileSignature?>(null)
    private var job: Job? = null

    fun start() {
        if (job != null) {
            return
        }
        job = scope.launch {
            // Perform an eager check so updates created before start are detected immediately.
            if (refreshSignature()) {
                notifyListener()
            }
            if (pollIntervalMillis <= 0L) {
                return@launch
            }
            while (isActive) {
                delay(pollIntervalMillis)
                if (refreshSignature()) {
                    notifyListener()
                }
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private suspend fun refreshSignature(): Boolean =
            withContext(ioContext) {
                val directory = runCatching { directoryResolver() }
                        .getOrElse { error ->
                            Log.w(TAG, "Dictionary watcher failed to resolve directory", error)
                            return@withContext false
                        }
                val file = File(directory, fileName)
                val signature =
                        if (file.exists() && file.isFile) {
                            try {
                                FileSignature(file.lastModified(), file.length())
                            } catch (ioError: IOException) {
                                Log.w(TAG, "Dictionary watcher unable to access payload", ioError)
                                null
                            }
                        } else {
                            null
                        }
                val previous = lastSignature.getAndSet(signature)
                signature != null && signature != previous
            }

    private fun notifyListener() {
        try {
            onUpdateAvailable.invoke()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Dictionary watcher listener threw", throwable)
        }
    }

    private data class FileSignature(val lastModified: Long, val length: Long)

    companion object {
        private const val TAG = "DictionaryWatcher"
        private const val DEFAULT_FILE_NAME = "dictionary_updates.json"
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
