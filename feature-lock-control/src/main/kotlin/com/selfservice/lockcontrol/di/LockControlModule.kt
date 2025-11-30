package com.selfservice.lockcontrol.di

import android.content.Context
import com.selfservice.lockcontrol.BuildConfig
import com.selfservice.lockcontrol.LockController
import com.selfservice.lockcontrol.LockControllerImpl
import com.selfservice.lockcontrol.MockUsbSerialAdapter
import com.selfservice.lockcontrol.UsbSerialAdapter
import com.selfservice.lockcontrol.UsbSerialAdapterImpl
import com.selfservice.lockcontrol.UsbSerialConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier для LockControl Coroutine Scope
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LockControlScope

/**
 * Hilt модуль для предоставления зависимостей Lock Control
 */
@Module
@InstallIn(SingletonComponent::class)
object LockControlModule {
    
    /**
     * Предоставить конфигурацию USB Serial
     */
    @Provides
    @Singleton
    fun provideUsbSerialConfig(): UsbSerialConfig {
        return UsbSerialConfig(
            port = BuildConfig.ARDUINO_PORT,
            baudRate = BuildConfig.ARDUINO_BAUD,
            commandTimeout = BuildConfig.COMMAND_TIMEOUT_MS.toLong(),
            reconnectDelay = BuildConfig.RECONNECT_DELAY_MS.toLong(),
            heartbeatInterval = BuildConfig.HEARTBEAT_INTERVAL_MS.toLong()
        )
    }
    
    /**
     * Предоставить Coroutine Scope для LockControl
     */
    @Provides
    @Singleton
    @LockControlScope
    fun provideLockControlScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    
    /**
     * Предоставить USB Serial адаптер
     * Mock в DEV режиме, реальный в остальных случаях
     */
    @Provides
    @Singleton
    fun provideUsbSerialAdapter(
        @ApplicationContext context: Context,
        config: UsbSerialConfig,
        @LockControlScope scope: CoroutineScope
    ): UsbSerialAdapter {
        return if (BuildConfig.DEVICE_MOCK_LOCK) {
            MockUsbSerialAdapter(config)
        } else {
            UsbSerialAdapterImpl(context, config, scope)
        }
    }
    
    /**
     * Предоставить директорию для логов
     */
    @Provides
    @Singleton
    fun provideLockLogDir(@ApplicationContext context: Context): File {
        return File(context.filesDir, "logs/sessions")
    }
    
    /**
     * Предоставить функцию получения Session ID
     * TODO: Интегрировать с SessionManager
     */
    @Provides
    @Singleton
    fun provideSessionIdProvider(): () -> String {
        return { "session_${System.currentTimeMillis()}" }
    }
    
    /**
     * Предоставить LockController
     */
    @Provides
    @Singleton
    fun provideLockController(
        usbSerialAdapter: UsbSerialAdapter,
        logDir: File,
        sessionIdProvider: () -> String,
        @LockControlScope scope: CoroutineScope
    ): LockController {
        return LockControllerImpl(
            usbSerialAdapter = usbSerialAdapter,
            logDir = logDir,
            sessionIdProvider = sessionIdProvider,
            scope = scope,
            retryCount = 3,
            retryDelay = 1000
        )
    }
}
