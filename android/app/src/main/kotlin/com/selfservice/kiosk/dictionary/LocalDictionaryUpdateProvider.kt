package com.selfservice.kiosk.dictionary

import android.content.Context
import android.util.Log
import com.selfservice.obd.core.dictionary.DictionaryRevisionSnapshot
import com.selfservice.obd.core.dictionary.DictionaryUpdateBatch
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Loads dictionary update payloads from the kiosk's private storage. Updates are provided via a
 * JSON document that mirrors Supabase rows so the synchroniser can operate offline during field
 * testing. When no updates are available or the snapshot is current, `null` is returned.
 */
class LocalDictionaryUpdateProvider(
        private val context: Context,
        private val directoryResolver: (Context) -> File = { ctx -> File(ctx.filesDir, SUBDIR) },
        private val fileName: String = DEFAULT_FILE_NAME,
        private val charset: Charset = Charsets.UTF_8,
        private val ioContext: CoroutineContext = Dispatchers.IO,
        private val parser: DictionaryUpdatePayloadParser = DictionaryUpdatePayloadParser()
) : DictionaryUpdateProvider {

    private val lastDelivered: AtomicReference<DictionaryUpdateSignature?> = AtomicReference(null)

    override suspend fun fetchUpdate(
            current: DictionaryRevisionSnapshot
    ): DictionaryUpdateBatch? =
            withContext(ioContext) {
                val directory = directoryResolver(context)
                val file = File(directory, fileName)
                if (!file.exists()) {
                    return@withContext null
                }

                val rawPayload = try {
                    file.readText(charset)
                } catch (ioError: IOException) {
                    Log.w(TAG, "Unable to read dictionary update payload", ioError)
                    return@withContext null
                }

                val json = try {
                    JSONObject(rawPayload)
                } catch (parseError: JSONException) {
                    Log.w(TAG, "Invalid dictionary update payload", parseError)
                    return@withContext null
                }

                val payload = parser.parse(
                        root = json,
                        fallbackSource = DEFAULT_SOURCE,
                        fallbackUpdatedAtMillis = file.lastModified()
                ) ?: return@withContext null

                if (payload.isRedundant(current)) {
                    return@withContext null
                }

                val signature = payload.signature()
                val previous = lastDelivered.get()
                if (previous.sameAs(signature)) {
                    return@withContext null
                }

                lastDelivered.set(signature)

                payload.toBatch(DEFAULT_SOURCE)
            }

    companion object {
        private const val TAG = "LocalDictionaryUpdate"
        private const val SUBDIR = "dictionaries"
        private const val DEFAULT_FILE_NAME = "dictionary_updates.json"
        private const val DEFAULT_SOURCE = "local"
    }
}
