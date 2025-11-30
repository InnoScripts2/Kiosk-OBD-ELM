package com.selfservice.platform.bluetooth.connection

import com.selfservice.platform.bluetooth.ble.BleDevice
import com.selfservice.platform.bluetooth.config.BlePlatformConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для пула соединений
 * 
 * @since Session 07B
 */
class BleConnectionPoolTest {
    
    private lateinit var pool: BleConnectionPool
    private lateinit var config: BlePlatformConfig
    
    @Before
    fun setup() {
        config = BlePlatformConfig.testing()
        pool = BleConnectionPool(config, maxConnections = 3)
    }
    
    @Test
    fun `начальный пул должен быть пустым`() {
        assertEquals(0, pool.size())
        assertTrue(pool.getAll().isEmpty())
    }
    
    @Test
    fun `getOrCreate должен создавать новое соединение`() {
        val deviceAddress = "00:11:22:33:44:55"
        var factoryCalled = false
        
        val manager = pool.getOrCreate(deviceAddress) {
            factoryCalled = true
            MockBleConnectionManager()
        }
        
        assertTrue(factoryCalled)
        assertNotNull(manager)
        assertEquals(1, pool.size())
        assertTrue(pool.contains(deviceAddress))
    }
    
    @Test
    fun `getOrCreate должен возвращать существующее соединение`() {
        val deviceAddress = "00:11:22:33:44:55"
        var factoryCallCount = 0
        
        val manager1 = pool.getOrCreate(deviceAddress) {
            factoryCallCount++
            MockBleConnectionManager()
        }
        
        val manager2 = pool.getOrCreate(deviceAddress) {
            factoryCallCount++
            MockBleConnectionManager()
        }
        
        assertEquals(1, factoryCallCount)
        assertSame(manager1, manager2)
        assertEquals(1, pool.size())
    }
    
    @Test
    fun `пул должен ограничивать количество соединений`() {
        pool.getOrCreate("00:11:22:33:44:55") { MockBleConnectionManager() }
        pool.getOrCreate("00:11:22:33:44:66") { MockBleConnectionManager() }
        pool.getOrCreate("00:11:22:33:44:77") { MockBleConnectionManager() }
        
        assertThrows(IllegalStateException::class.java) {
            pool.getOrCreate("00:11:22:33:44:88") { MockBleConnectionManager() }
        }
    }
    
    @Test
    fun `remove должен удалять соединение`() {
        val deviceAddress = "00:11:22:33:44:55"
        pool.getOrCreate(deviceAddress) { MockBleConnectionManager() }
        
        assertEquals(1, pool.size())
        
        pool.remove(deviceAddress)
        
        assertEquals(0, pool.size())
        assertFalse(pool.contains(deviceAddress))
    }
    
    @Test
    fun `clear должен удалять все соединения`() {
        pool.getOrCreate("00:11:22:33:44:55") { MockBleConnectionManager() }
        pool.getOrCreate("00:11:22:33:44:66") { MockBleConnectionManager() }
        
        assertEquals(2, pool.size())
        
        pool.clear()
        
        assertEquals(0, pool.size())
    }
    
    @Test
    fun `get должен возвращать существующее соединение или null`() {
        val deviceAddress = "00:11:22:33:44:55"
        
        assertNull(pool.get(deviceAddress))
        
        pool.getOrCreate(deviceAddress) { MockBleConnectionManager() }
        
        assertNotNull(pool.get(deviceAddress))
    }
    
    private class MockBleConnectionManager : BleConnectionManager {
        override val connectionState = kotlinx.coroutines.flow.MutableStateFlow(
            com.selfservice.platform.bluetooth.ble.BleConnectionState.DISCONNECTED
        )
        override val connectedDevice = kotlinx.coroutines.flow.MutableStateFlow<BleDevice?>(null)
        override val connectionEvents = kotlinx.coroutines.flow.emptyFlow<ConnectionEvent>()
        
        override suspend fun connect(device: BleDevice, autoReconnect: Boolean, timeout: Long) {}
        override suspend fun disconnect() {}
        override suspend fun discoverServices() = emptyList<BleService>()
        override suspend fun readCharacteristic(serviceUuid: java.util.UUID, characteristicUuid: java.util.UUID) = byteArrayOf()
        override suspend fun writeCharacteristic(serviceUuid: java.util.UUID, characteristicUuid: java.util.UUID, data: ByteArray) {}
        override fun subscribeToNotifications(serviceUuid: java.util.UUID, characteristicUuid: java.util.UUID) = kotlinx.coroutines.flow.emptyFlow<ByteArray>()
        override suspend fun unsubscribeFromNotifications(serviceUuid: java.util.UUID, characteristicUuid: java.util.UUID) {}
        override suspend fun readRssi() = null
        override suspend fun requestMtu(mtu: Int) = 23
        override fun release() {}
    }
}

/**
 * Unit-тесты для стратегий переподключения
 */
class ReconnectionStrategyTest {
    
    @Test
    fun `ExponentialBackoffStrategy должен увеличивать задержку экспоненциально`() {
        val strategy = ExponentialBackoffStrategy(
            initialDelay = 1000L,
            maxDelay = 30000L,
            maxAttempts = 5
        )
        
        assertEquals(1000L, strategy.getDelay(1))
        assertEquals(2000L, strategy.getDelay(2))
        assertEquals(4000L, strategy.getDelay(3))
        assertEquals(8000L, strategy.getDelay(4))
        assertEquals(16000L, strategy.getDelay(5))
    }
    
    @Test
    fun `ExponentialBackoffStrategy должен ограничивать максимальную задержку`() {
        val strategy = ExponentialBackoffStrategy(
            initialDelay = 1000L,
            maxDelay = 5000L,
            maxAttempts = 10
        )
        
        assertTrue(strategy.getDelay(10) <= 5000L)
    }
    
    @Test
    fun `ExponentialBackoffStrategy должен ограничивать количество попыток`() {
        val strategy = ExponentialBackoffStrategy(maxAttempts = 3)
        
        assertTrue(strategy.shouldRetry(1))
        assertTrue(strategy.shouldRetry(2))
        assertTrue(strategy.shouldRetry(3))
        assertFalse(strategy.shouldRetry(4))
    }
    
    @Test
    fun `LinearBackoffStrategy должен возвращать фиксированную задержку`() {
        val strategy = LinearBackoffStrategy(delay = 2000L, maxAttempts = 3)
        
        assertEquals(2000L, strategy.getDelay(1))
        assertEquals(2000L, strategy.getDelay(2))
        assertEquals(2000L, strategy.getDelay(3))
    }
    
    @Test
    fun `LinearBackoffStrategy должен ограничивать количество попыток`() {
        val strategy = LinearBackoffStrategy(maxAttempts = 3)
        
        assertTrue(strategy.shouldRetry(1))
        assertTrue(strategy.shouldRetry(2))
        assertTrue(strategy.shouldRetry(3))
        assertFalse(strategy.shouldRetry(4))
    }
}

/**
 * Unit-тесты для координатора переподключений
 */
class ReconnectionCoordinatorTest {
    
    private lateinit var coordinator: ReconnectionCoordinator
    
    @Before
    fun setup() {
        coordinator = ReconnectionCoordinator(LinearBackoffStrategy(delay = 1000L, maxAttempts = 3))
    }
    
    @Test
    fun `registerAttempt должен возвращать задержку для первой попытки`() {
        val delay = coordinator.registerAttempt("00:11:22:33:44:55")
        
        assertNotNull(delay)
        assertEquals(1000L, delay)
        assertEquals(1, coordinator.getAttemptCount("00:11:22:33:44:55"))
    }
    
    @Test
    fun `registerAttempt должен увеличивать счетчик`() {
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:55")
        
        assertEquals(3, coordinator.getAttemptCount("00:11:22:33:44:55"))
    }
    
    @Test
    fun `registerAttempt должен возвращать null при превышении лимита`() {
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:55")
        
        val delay = coordinator.registerAttempt("00:11:22:33:44:55")
        
        assertNull(delay)
    }
    
    @Test
    fun `reset должен сбрасывать счетчик`() {
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:55")
        
        assertEquals(2, coordinator.getAttemptCount("00:11:22:33:44:55"))
        
        coordinator.reset("00:11:22:33:44:55")
        
        assertEquals(0, coordinator.getAttemptCount("00:11:22:33:44:55"))
    }
    
    @Test
    fun `clear должен сбрасывать все счетчики`() {
        coordinator.registerAttempt("00:11:22:33:44:55")
        coordinator.registerAttempt("00:11:22:33:44:66")
        
        coordinator.clear()
        
        assertEquals(0, coordinator.getAttemptCount("00:11:22:33:44:55"))
        assertEquals(0, coordinator.getAttemptCount("00:11:22:33:44:66"))
    }
}
