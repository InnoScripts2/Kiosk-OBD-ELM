package com.selfservice.kiosk.mdm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MdmCommandManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val identity = DeviceIdentity(kioskId = "kiosk-qa", environment = "qa", mdmDeviceId = "mdm-qa")

    @Test
    fun `submit command records lifecycle and events`() = runTest {
        val writer = RecordingSupabaseOutboxWriter()
        val handler = RecordingHandler(
            MdmCommandResult.success(
                message = "Heartbeat enqueued",
                payload = mapOf("triggered" to true),
                metadata = mapOf("handler" to "default")
            )
        )
        val clock = FakeClock()
        val manager = createManager(writer, handler, clock::next)

        val command = MdmCommand(
            id = "cmd-1",
            type = "heartbeat",
            payload = mapOf("force" to true),
            issuedAtMillis = 5_000L,
            source = "supabase",
            metadata = mapOf("ticket" to "ops-1")
        )

        manager.submit(command)
        advanceUntilIdle()

        assertEquals(
            listOf(
                DeviceCommandSupabaseSchema.Status.RECEIVED.wireValue,
                DeviceCommandSupabaseSchema.Status.ACKNOWLEDGED.wireValue,
                DeviceCommandSupabaseSchema.Status.IN_PROGRESS.wireValue,
                DeviceCommandSupabaseSchema.Status.SUCCEEDED.wireValue
            ),
            writer.commandStatuses()
        )
        assertEquals(
            listOf("command_ack", "command_started", "command_succeeded"),
            writer.eventTypes()
        )

        assertEquals(listOf(command.id), handler.handled.map { it.id })

        val finalState = manager.lastCommandState.value
        assertNotNull(finalState)
        assertEquals(command.id, finalState?.id)
        assertEquals(DeviceCommandSupabaseSchema.Status.SUCCEEDED, finalState?.status)

        val lastCommandRow = writer.commandRows().last()
        @Suppress("UNCHECKED_CAST")
        val metadata = lastCommandRow[DeviceCommandSupabaseSchema.Columns.METADATA] as Map<String, Any?>
        assertEquals("ops-1", metadata["ticket"])
        @Suppress("UNCHECKED_CAST")
        val resultPayload = lastCommandRow[DeviceCommandSupabaseSchema.Columns.RESULT_PAYLOAD] as Map<String, Any?>
        assertEquals(true, resultPayload["triggered"])
    }

    @Test
    fun `handler failure records failure status and event`() = runTest {
        val writer = RecordingSupabaseOutboxWriter()
        val handler = RecordingHandler(
            MdmCommandResult.failure(
                message = "Missing context",
                errorPayload = mapOf("code" to "NO_CTX"),
                metadata = mapOf("retry" to false)
            )
        )
        val clock = FakeClock()
        val manager = createManager(writer, handler, clock::next)

        val command = MdmCommand(
            id = "cmd-error",
            type = "dictionary_sync",
            issuedAtMillis = 7_000L,
            source = "mdm",
            metadata = mapOf("ticket" to "ops-9")
        )

        manager.submit(command)
        advanceUntilIdle()

        val statuses = writer.commandStatuses()
        assertEquals(DeviceCommandSupabaseSchema.Status.FAILED.wireValue, statuses.last())

        val lastCommandRow = writer.commandRows().last()
        assertEquals("Missing context", lastCommandRow[DeviceCommandSupabaseSchema.Columns.ERROR_MESSAGE])
        @Suppress("UNCHECKED_CAST")
        val resultPayload = lastCommandRow[DeviceCommandSupabaseSchema.Columns.RESULT_PAYLOAD] as Map<String, Any?>
        assertEquals("NO_CTX", resultPayload["code"])

        val events = writer.eventTypes()
        assertEquals("command_failed", events.last())

        val failureEvent = writer.eventRows().last()
        assertEquals(
            DeviceEventSupabaseSchema.Severity.ERROR.wireValue,
            failureEvent[DeviceEventSupabaseSchema.Columns.SEVERITY]
        )
        assertEquals(command.id, failureEvent[DeviceEventSupabaseSchema.Columns.COMMAND_ID])
        assertEquals("Missing context", failureEvent[DeviceEventSupabaseSchema.Columns.MESSAGE])
    }

    private fun TestScope.createManager(
        writer: RecordingSupabaseOutboxWriter,
        handler: RecordingHandler,
        clock: () -> Long
    ): MdmCommandManager = MdmCommandManager(
        context = context,
        scope = this,
        writer = writer,
        identityProvider = { identity },
        handler = handler,
        clock = clock,
        enableDebugReceiver = false
    )

    private class RecordingSupabaseOutboxWriter : SupabaseOutboxWriter(
        directory = Files.createTempDirectory("supabase-outbox-test").toFile()
    ) {
        data class Operation(val table: String, val payload: Map<String, Any?>, val operation: String)

        val operations = mutableListOf<Operation>()

        override fun enqueue(table: String, payload: Map<String, Any?>, operation: String) {
            operations += Operation(table, payload, operation)
        }

        fun commandRows(): List<Map<String, Any?>> =
            operations.filter { it.table == DeviceCommandSupabaseSchema.TABLE_NAME }.map { it.payload }

        fun eventRows(): List<Map<String, Any?>> =
            operations.filter { it.table == DeviceEventSupabaseSchema.TABLE_NAME }.map { it.payload }

        fun commandStatuses(): List<String> =
            commandRows().map { it[DeviceCommandSupabaseSchema.Columns.STATUS] as String }

        fun eventTypes(): List<String> =
            eventRows().map { it[DeviceEventSupabaseSchema.Columns.EVENT_TYPE] as String }
    }

    private class RecordingHandler(
        private val result: MdmCommandResult
    ) : MdmCommandHandler {
        val handled = mutableListOf<MdmCommand>()
        override suspend fun handle(command: MdmCommand): MdmCommandResult {
            handled += command
            return result
        }
    }

    private class FakeClock(
        private var now: Long = 10_000L,
        private val step: Long = 250L
    ) {
        fun next(): Long {
            now += step
            return now
        }
    }
}
