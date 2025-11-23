package com.selfservice.kiosk.ui.state

import java.util.UUID

/**
 * Session state for kiosk user flow
 * Tracks current session data and status
 */
data class SessionState(
    val sessionId: String = UUID.randomUUID().toString(),
    val serviceType: ServiceType = ServiceType.None,
    val customerPhone: String = "",
    val customerEmail: String = "",
    val vehicleType: VehicleType? = null,
    val vehicleBrand: VehicleBrand? = null,
    val paymentStatus: PaymentStatus = PaymentStatus.Pending,
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastActivityTimestamp: Long = System.currentTimeMillis()
) {
    fun isActive(): Boolean = 
        System.currentTimeMillis() - lastActivityTimestamp < TIMEOUT_MS
    
    fun updateActivity(): SessionState = 
        copy(lastActivityTimestamp = System.currentTimeMillis())
    
    companion object {
        const val TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * Service type selection
 */
sealed class ServiceType {
    object None : ServiceType()
    data class Thickness(val price: Int) : ServiceType()
    data class OBD(val price: Int) : ServiceType()
}

/**
 * Vehicle types for thickness measurement
 */
enum class VehicleType(val displayName: String, val price: Int) {
    SEDAN("Седан/Хэтчбек", 350),
    MINIVAN("Минивэн", 400),
    SUV("SUV/Кроссовер", 450)
}

/**
 * Vehicle brands for OBD diagnostics
 */
enum class VehicleBrand(val displayName: String) {
    TOYOTA("Toyota"),
    LEXUS("Lexus"),
    HYUNDAI("Hyundai"),
    KIA("Kia"),
    BMW("BMW"),
    MERCEDES("Mercedes-Benz"),
    AUDI("Audi"),
    VOLKSWAGEN("Volkswagen"),
    OTHER("Другая")
}

/**
 * Payment status tracking
 */
sealed class PaymentStatus {
    object Pending : PaymentStatus()
    data class Processing(val intentId: String) : PaymentStatus()
    data class Completed(val intentId: String, val timestamp: Long) : PaymentStatus()
    data class Failed(val error: String) : PaymentStatus()
}
