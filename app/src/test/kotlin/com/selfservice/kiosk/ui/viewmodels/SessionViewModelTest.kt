package com.selfservice.kiosk.ui.viewmodels

import com.selfservice.kiosk.ui.navigation.KioskRoute
import com.selfservice.kiosk.ui.navigation.NavigationEvent
import com.selfservice.kiosk.ui.state.ServiceType
import com.selfservice.kiosk.ui.state.VehicleBrand
import com.selfservice.kiosk.ui.state.VehicleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionViewModelTest {

    @Test
    fun `thickness flow transitions include instructions and results`() = runTest {
        val viewModel = SessionViewModel()

        viewModel.onNavigationEvent(NavigationEvent.SelectThickness)
        assertEquals(KioskRoute.ThicknessInput, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(
            NavigationEvent.SubmitThicknessDetails(
                vehicleType = VehicleType.SEDAN,
                phone = "+79999999999",
                email = "user@example.com"
            )
        )
        assertEquals(KioskRoute.ThicknessPayment, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.PaymentComplete)
        assertEquals(KioskRoute.ThicknessPrep, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.DeviceReady)
        assertEquals(KioskRoute.ThicknessInstructions, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.BeginMeasurement)
        assertEquals(KioskRoute.ThicknessMeasurement, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.MeasurementComplete)
        assertEquals(KioskRoute.ThicknessResults, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.ReportSent)
        assertEquals(KioskRoute.ReportSent, viewModel.currentRoute.value)
    }

    @Test
    fun `submit thickness details updates session state`() = runTest {
        val viewModel = SessionViewModel()

        viewModel.onNavigationEvent(NavigationEvent.SelectThickness)
        viewModel.onNavigationEvent(
            NavigationEvent.SubmitThicknessDetails(
                vehicleType = VehicleType.SUV,
                phone = "+79998887766",
                email = "owner@example.com"
            )
        )

        val session = viewModel.sessionState.value
        val service = session.serviceType as ServiceType.Thickness
        assertEquals(VehicleType.SUV, session.vehicleType)
        assertEquals("+79998887766", session.customerPhone)
        assertEquals("owner@example.com", session.customerEmail)
        assertEquals(VehicleType.SUV.price, service.price)
    }

    @Test
    fun `obd flow transitions cover scanning and paywall completion`() = runTest {
        val viewModel = SessionViewModel()

        viewModel.onNavigationEvent(NavigationEvent.SelectObd)
        assertEquals(KioskRoute.ObdInput, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(
            NavigationEvent.SubmitObdDetails(
                vehicleBrand = VehicleBrand.TOYOTA,
                phone = "+79991112233",
                email = "driver@example.com"
            )
        )
        assertEquals(KioskRoute.ObdPayment, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.PaymentComplete)
        assertEquals(KioskRoute.ObdPrep, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.DeviceReady)
        assertEquals(KioskRoute.ObdScanning, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.ScanComplete)
        assertEquals(KioskRoute.ObdResults, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.ViewObdDetails)
        assertEquals(KioskRoute.ObdPaywall, viewModel.currentRoute.value)

        viewModel.onNavigationEvent(NavigationEvent.PaywallPaid)
        assertEquals(KioskRoute.ObdDetails, viewModel.currentRoute.value)
    }

    @Test
    fun `submit obd details updates session state`() = runTest {
        val viewModel = SessionViewModel()

        viewModel.onNavigationEvent(NavigationEvent.SelectObd)
        viewModel.onNavigationEvent(
            NavigationEvent.SubmitObdDetails(
                vehicleBrand = VehicleBrand.BMW,
                phone = "+79995556677",
                email = "bmw@example.com"
            )
        )

        val session = viewModel.sessionState.value
        val service = session.serviceType as ServiceType.OBD
        assertEquals(VehicleBrand.BMW, session.vehicleBrand)
        assertEquals("+79995556677", session.customerPhone)
        assertEquals("bmw@example.com", session.customerEmail)
        assertEquals(480, service.price)
    }
}
