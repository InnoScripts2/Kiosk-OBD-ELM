package com.selfservice.kiosk.ui.navigation

/**
 * Navigation routes for kiosk UI flow
 * Maps to 12 screens defined in docs/navigation-flow.md
 */
sealed class KioskRoute(val route: String) {
    // Main flow
    object Attract : KioskRoute("attract")
    object Welcome : KioskRoute("welcome")
    object ServiceSelection : KioskRoute("service_selection")
    
    // Thickness flow
    object ThicknessInput : KioskRoute("thickness_input")
    object ThicknessPayment : KioskRoute("thickness_payment")
    object ThicknessPrep : KioskRoute("thickness_prep")
    object ThicknessInstructions : KioskRoute("thickness_instructions")
    object ThicknessMeasurement : KioskRoute("thickness_measurement")
    object ThicknessResults : KioskRoute("thickness_results")
    
    // OBD flow
    object ObdInput : KioskRoute("obd_input")
    object ObdPayment : KioskRoute("obd_payment")
    object ObdPrep : KioskRoute("obd_prep")
    object ObdScanning : KioskRoute("obd_scanning")
    object ObdResults : KioskRoute("obd_results")
    object ObdPaywall : KioskRoute("obd_paywall")
    object ObdDetails : KioskRoute("obd_details")
    
    // Common
    object ReportSent : KioskRoute("report_sent")
}

/**
 * Navigation events for triggering transitions
 */
sealed class NavigationEvent {
    object Tap : NavigationEvent()
    object Agree : NavigationEvent()
    object SelectThickness : NavigationEvent()
    object SelectObd : NavigationEvent()
    object PaymentComplete : NavigationEvent()
    object DeviceReady : NavigationEvent()
    object BeginMeasurement : NavigationEvent()
    object MeasurementComplete : NavigationEvent()
    object ScanComplete : NavigationEvent()
    object PaywallPaid : NavigationEvent()
    object ReportSent : NavigationEvent()
    object Timeout : NavigationEvent()
    object ToMain : NavigationEvent()
    object Skip : NavigationEvent() // DEV only
}

/**
 * Navigation state management
 */
data class NavigationState(
    val currentRoute: KioskRoute = KioskRoute.Attract,
    val history: List<KioskRoute> = emptyList(),
    val canGoBack: Boolean = false
)
