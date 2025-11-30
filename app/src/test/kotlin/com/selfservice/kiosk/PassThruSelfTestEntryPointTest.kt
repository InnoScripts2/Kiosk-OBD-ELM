package com.selfservice.kiosk

import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.SelfTestResultDao
import com.selfservice.obd.core.session.ConnectedAdapter
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class PassThruSelfTestEntryPointTest {

        private val rootDir = Files.createTempDirectory("selftest-entrypoint").toFile()
        private val successStepHandler =
                        PassThruSelfTestExecutor.StepHandler { _, _ ->
                                        AdapterSelfTestExecution.Outcome.SUCCESS
                        }

        @After
    fun tearDown() {
        PassThruSelfTestEntryPoint.resetOverride()
        rootDir.deleteRecursively()
                        ManufacturerDictionaryInstaller.resetForTests()
    }

    @Test
    fun `create wires default dependencies`() {
        val storageDir = File(rootDir, "default")
        storageDir.deleteRecursively()

        val factoryInvoked = AtomicBoolean(false)
        val handle =
                PassThruSelfTestEntryPoint.create(
                        PassThruSelfTestEntryPoint.Config(
                                storageDirectory = storageDir,
                                adapterProvider = { adapter("AA:BB:CC") },
                                stepHandler = successStepHandler,
                                bridgeProvider = { FakeBridge() },
                                dictionarySchedule =
                                        DictionarySyncSchedule(
                                                intervalMillis = Long.MAX_VALUE,
                                                initialDelayMillis = Long.MAX_VALUE
                                        ),
                                resultDaoFactory = { dir ->
                                    factoryInvoked.set(true)
                                    assertEquals(storageDir, dir)
                                    SelfTestResultDaoStub()
                                }
                        )
                )

        assertEquals(storageDir, handle.resultStorageDirectory)
        assertTrue(factoryInvoked.get())
        assertEquals(DictionarySyncState.Idle, handle.dictionarySyncCoordinator.state().value)

        handle.shutdown()
    }

    @Test
    fun `override can short-circuit creation`() {
        val overrideInvoked = AtomicBoolean(false)
        PassThruSelfTestEntryPoint.override {
            overrideInvoked.set(true)
            null
        }

        val storageDir = File(rootDir, "override")
        val handle =
                PassThruSelfTestEntryPoint.create(
                        PassThruSelfTestEntryPoint.Config(
                                storageDirectory = storageDir,
                                adapterProvider = { adapter("BB:CC:DD") },
                                stepHandler = successStepHandler,
                                bridgeProvider = { FakeBridge() }
                        )
                )

        assertTrue(overrideInvoked.get())
        assertNotNull(handle)
        assertEquals(storageDir, handle.resultStorageDirectory)

        handle.shutdown()
    }

    private fun adapter(address: String): ConnectedAdapter =
            ConnectedAdapter(
                    device = BleDevice(address = address, name = "PassThru", rssi = -30),
                    protocol = "J2534"
            )

    private class SelfTestResultDaoStub : SelfTestResultDao {
        override suspend fun upsert(row: Map<String, Any?>) {}
    }

    private class FakeBridge : PassThruNativeBridge {
        override fun openChannel(config: com.selfservice.obd.core.passthru.PassThruChannelConfig): Result<Int> =
                Result.success(1)

        override fun closeChannel(channelId: Int): Result<Unit> = Result.success(Unit)

        override fun setReferenceVoltage(channelId: Int, millivolts: Int): Result<Unit> =
                Result.success(Unit)

        override fun writeMessage(
                message: com.selfservice.obd.core.passthru.PassThruMessage,
                timeoutMs: Int
        ): Result<Unit> = Result.success(Unit)

        override fun readMessage(
                channelId: Int,
                timeoutMs: Int
        ): Result<com.selfservice.obd.core.passthru.PassThruMessage?> = Result.success(null)
    }
}
