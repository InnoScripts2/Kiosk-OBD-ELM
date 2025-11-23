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
                _sessionState.update { it.copy(serviceType = ServiceType.OBD(480)) }
                navigateTo(KioskRoute.ObdInput)
            }
            is NavigationEvent.PaymentComplete -> handlePaymentComplete()
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
