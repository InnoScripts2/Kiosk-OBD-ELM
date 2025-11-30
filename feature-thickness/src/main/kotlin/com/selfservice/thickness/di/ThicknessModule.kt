package com.selfservice.thickness.di

import com.selfservice.core.logging.Logger
import com.selfservice.platform.bluetooth.BleConnectionManager
import com.selfservice.platform.bluetooth.BlessedBleScanner
import com.selfservice.thickness.ThicknessDevice
import com.selfservice.thickness.ble.ThicknessBleAdapter
import com.selfservice.thickness.mock.MockThicknessDevice
import com.selfservice.thickness.models.MockDeviceConfig
import com.selfservice.thickness.models.ThicknessDeviceConfig
import com.selfservice.thickness.protocol.ThicknessProtocolParser

/**
 * Модуль Dependency Injection для feature-thickness
 * 
 * Предоставляет зависимости для работы с толщиномером.
 * Поддерживает DEV/QA/PROD режимы.
 * 
 * @since Session 12B
 */
object ThicknessModule {
    
    /**
     * Предоставить ThicknessDevice на основе режима работы
     * 
     * @param mode Режим работы приложения (DEV/QA/PROD)
     * @param bleScanner BLE сканер (для PROD/QA)
     * @param bleConnectionManager BLE менеджер подключений (для PROD/QA)
     * @param logger Логгер
     * @param config Конфигурация устройства
     * @param mockConfig Конфигурация мок-устройства (для DEV)
     * @return ThicknessDevice
     */
    fun provideThicknessDevice(
        mode: DeviceMode,
        bleScanner: BlessedBleScanner? = null,
        bleConnectionManager: BleConnectionManager? = null,
        logger: Logger,
        config: ThicknessDeviceConfig = ThicknessDeviceConfig(),
        mockConfig: MockDeviceConfig = MockDeviceConfig()
    ): ThicknessDevice {
        return when (mode) {
            DeviceMode.DEV -> {
                logger.info(TAG, "Using MockThicknessDevice [MOCK MODE]")
                MockThicknessDevice(mockConfig)
            }
            DeviceMode.QA, DeviceMode.PROD -> {
                requireNotNull(bleScanner) { "BLE scanner required for QA/PROD mode" }
                requireNotNull(bleConnectionManager) { "BLE connection manager required for QA/PROD mode" }
                
                logger.info(TAG, "Using BleThicknessDevice (real device)")
                RealThicknessDeviceImpl(
                    bleAdapter = ThicknessBleAdapter(bleScanner, bleConnectionManager, logger, config),
                    parser = ThicknessProtocolParser(config),
                    logger = logger,
                    config = config
                )
            }
        }
    }
    
    /**
     * Предоставить ThicknessBleAdapter
     */
    fun provideThicknessBleAdapter(
        bleScanner: BlessedBleScanner,
        bleConnectionManager: BleConnectionManager,
        logger: Logger,
        config: ThicknessDeviceConfig = ThicknessDeviceConfig()
    ): ThicknessBleAdapter {
        return ThicknessBleAdapter(bleScanner, bleConnectionManager, logger, config)
    }
    
    /**
     * Предоставить ThicknessProtocolParser
     */
    fun provideThicknessProtocolParser(
        config: ThicknessDeviceConfig = ThicknessDeviceConfig()
    ): ThicknessProtocolParser {
        return ThicknessProtocolParser(config)
    }
    
    /**
     * Предоставить конфигурацию устройства
     */
    fun provideThicknessDeviceConfig(): ThicknessDeviceConfig {
        return ThicknessDeviceConfig()
    }
    
    /**
     * Предоставить конфигурацию мок-устройства
     */
    fun provideMockDeviceConfig(mode: DeviceMode): MockDeviceConfig {
        return when (mode) {
            DeviceMode.DEV -> MockDeviceConfig.forTesting()
            DeviceMode.QA -> MockDeviceConfig.forDemo()
            DeviceMode.PROD -> MockDeviceConfig(enabled = false)
        }
    }
    
    private const val TAG = "ThicknessModule"
    
    /**
     * Режим работы устройства
     */
    enum class DeviceMode {
        /** Режим разработки - используется мок-устройство */
        DEV,
        /** Режим тестирования - реальное устройство с расширенным логированием */
        QA,
        /** Production режим - реальное устройство */
        PROD
    }
}

/**
 * Реализация ThicknessDevice с использованием реального BLE устройства
 */
private class RealThicknessDeviceImpl(
    private val bleAdapter: ThicknessBleAdapter,
    private val parser: ThicknessProtocolParser,
    private val logger: Logger,
    private val config: ThicknessDeviceConfig
) : ThicknessDevice {
    
    override suspend fun connect(): Result<Unit> {
        return bleAdapter.connect().map { }
    }
    
    override suspend fun disconnect() {
        bleAdapter.disconnect()
    }
    
    override suspend fun startMeasurements(): kotlinx.coroutines.flow.Flow<com.selfservice.thickness.ThicknessMeasurement> {
        return kotlinx.coroutines.flow.flow {
            // Подписываемся на уведомления от устройства
            val notificationsFlow = bleAdapter.subscribeToNotifications()
            
            notificationsFlow.collect { data ->
                try {
                    val value = parser.parse(data)
                    
                    emit(com.selfservice.thickness.ThicknessMeasurement(
                        timestamp = System.currentTimeMillis(),
                        value = value,
                        zone = "unknown", // Зона определяется на уровне StateMachine
                        status = com.selfservice.thickness.MeasurementStatus.VALID
                    ))
                } catch (e: Exception) {
                    logger.error("RealThicknessDevice", "Failed to parse measurement", e)
                    
                    emit(com.selfservice.thickness.ThicknessMeasurement(
                        timestamp = System.currentTimeMillis(),
                        value = 0f,
                        zone = "unknown",
                        status = com.selfservice.thickness.MeasurementStatus.ERROR
                    ))
                }
            }
        }
    }
    
    override suspend fun stopMeasurements() {
        bleAdapter.unsubscribeFromNotifications()
    }
    
    override fun getConnectionStatus(): com.selfservice.thickness.ConnectionStatus {
        return if (bleAdapter.isConnected()) {
            com.selfservice.thickness.ConnectionStatus.CONNECTED
        } else {
            com.selfservice.thickness.ConnectionStatus.DISCONNECTED
        }
    }
}
