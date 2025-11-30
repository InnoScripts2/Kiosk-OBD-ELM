package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfservice.kiosk.ui.navigation.KioskRoute
import com.selfservice.kiosk.ui.navigation.NavigationEvent
import com.selfservice.kiosk.ui.state.SessionState
import com.selfservice.kiosk.ui.state.ServiceType
import com.selfservice.kiosk.ui.state.VehicleType
import com.selfservice.kiosk.ui.state.VehicleBrand
import com.selfservice.kiosk.ui.state.PaymentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

private const val DEFAULT_OBD_PRICE = 480

/**
 * Manages kiosk session state and navigation
 * Coordinates between different flow states
 */
class SessionViewModel : ViewModel() {
    
    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    private val _currentRoute = MutableStateFlow<KioskRoute>(KioskRoute.Attract)
    val currentRoute: StateFlow<KioskRoute> = _currentRoute.asStateFlow()
    
    private val _routeHistory = MutableStateFlow<List<KioskRoute>>(emptyList())
    
    /**
     * Handle navigation events and route transitions
     */
    fun onNavigationEvent(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.Tap -> navigateTo(KioskRoute.Welcome)
            is NavigationEvent.Agree -> navigateTo(KioskRoute.ServiceSelection)
            is NavigationEvent.SelectThickness -> {
                _sessionState.update { it.copy(serviceType = ServiceType.Thickness(350)) }
                navigateTo(KioskRoute.ThicknessInput)
            }
            is NavigationEvent.SelectObd -> {
                _sessionState.update { it.copy(serviceType = ServiceType.OBD(DEFAULT_OBD_PRICE)) }
                navigateTo(KioskRoute.ObdInput)
            }
            is NavigationEvent.SubmitThicknessDetails -> handleThicknessDetails(event)
            is NavigationEvent.SubmitObdDetails -> handleObdDetails(event)
            is NavigationEvent.PaymentComplete -> handlePaymentComplete()
            is NavigationEvent.DeviceReady -> handleDeviceReady()
            is NavigationEvent.BeginMeasurement -> handleBeginMeasurement()
            is NavigationEvent.MeasurementComplete -> handleMeasurementComplete()
            is NavigationEvent.ScanComplete -> handleScanComplete()
            is NavigationEvent.PaywallPaid -> handlePaywallPaid()
            is NavigationEvent.ViewObdDetails -> navigateTo(KioskRoute.ObdPaywall)
            is NavigationEvent.ReportSent -> navigateTo(KioskRoute.ReportSent)
            is NavigationEvent.ToMain -> resetSession()
            is NavigationEvent.Timeout -> resetSession()
            else -> {} // Handle in specific ViewModels
        }
        updateActivity()
    }
    
    /**
     * Navigate to a new route
     */
    private fun navigateTo(route: KioskRoute) {
        _routeHistory.update { it + _currentRoute.value }
        _currentRoute.value = route
    }
    
    /**
     * Update session data
     */
    fun updateSession(
        phone: String? = null,
        email: String? = null,
        vehicleType: VehicleType? = null,
        vehicleBrand: VehicleBrand? = null,
        paymentStatus: PaymentStatus? = null
    ) {
        _sessionState.update { current ->
            current.copy(
                customerPhone = phone ?: current.customerPhone,
                customerEmail = email ?: current.customerEmail,
                vehicleType = vehicleType ?: current.vehicleType,
                vehicleBrand = vehicleBrand ?: current.vehicleBrand,
                paymentStatus = paymentStatus ?: current.paymentStatus
            ).updateActivity()
        }
    }
    
    /**
     * Handle payment completion
     */
    private fun handlePaymentComplete() {
        val currentService = _sessionState.value.serviceType
        val nextRoute = when (currentService) {
            is ServiceType.Thickness -> KioskRoute.ThicknessPrep
            is ServiceType.OBD -> KioskRoute.ObdPrep
            else -> KioskRoute.Attract
        }
        navigateTo(nextRoute)
    }

    private fun handleThicknessDetails(event: NavigationEvent.SubmitThicknessDetails) {
        _sessionState.update { current ->
            current.copy(
                serviceType = ServiceType.Thickness(event.vehicleType.price),
                vehicleType = event.vehicleType,
                customerPhone = event.phone,
                customerEmail = event.email
            ).updateActivity()
        }
        navigateTo(KioskRoute.ThicknessPayment)
    }

    private fun handleObdDetails(event: NavigationEvent.SubmitObdDetails) {
        _sessionState.update { current ->
            val price = (current.serviceType as? ServiceType.OBD)?.price ?: DEFAULT_OBD_PRICE
            current.copy(
                serviceType = ServiceType.OBD(price),
                vehicleBrand = event.vehicleBrand,
                customerPhone = event.phone,
                customerEmail = event.email
            ).updateActivity()
        }
        navigateTo(KioskRoute.ObdPayment)
    }

    private fun handleDeviceReady() {
        val nextRoute = when (_sessionState.value.serviceType) {
            is ServiceType.Thickness -> KioskRoute.ThicknessInstructions
            is ServiceType.OBD -> KioskRoute.ObdScanning
            else -> KioskRoute.Attract
        }
        navigateTo(nextRoute)
    }

    private fun handleBeginMeasurement() {
        if (_sessionState.value.serviceType is ServiceType.Thickness) {
            navigateTo(KioskRoute.ThicknessMeasurement)
        }
    }

    private fun handleMeasurementComplete() {
        if (_sessionState.value.serviceType is ServiceType.Thickness) {
            navigateTo(KioskRoute.ThicknessResults)
        }
    }

    private fun handleScanComplete() {
        if (_sessionState.value.serviceType is ServiceType.OBD) {
            navigateTo(KioskRoute.ObdResults)
        }
    }

    private fun handlePaywallPaid() {
        if (_sessionState.value.serviceType is ServiceType.OBD) {
            navigateTo(KioskRoute.ObdDetails)
        }
    }
    
    /**
     * Reset session and return to attract screen
     */
    fun resetSession() {
        _sessionState.value = SessionState(sessionId = UUID.randomUUID().toString())
        _currentRoute.value = KioskRoute.Attract
        _routeHistory.value = emptyList()
    }
    
    /**
     * Update last activity timestamp
     */
    private fun updateActivity() {
        _sessionState.update { it.updateActivity() }
    }
    
    /**
     * Start timeout monitoring
     */
    fun startTimeoutMonitoring() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // Check every 30 seconds
                if (!_sessionState.value.isActive()) {
                    onNavigationEvent(NavigationEvent.Timeout)
                }
            }
        }
    }
}
