package com.selfservice.kiosk.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.selfservice.feature.payments.PaymentContact
import com.selfservice.kiosk.ui.navigation.KioskRoute
import com.selfservice.kiosk.ui.navigation.NavigationEvent
import com.selfservice.kiosk.ui.screens.AttractScreen
import com.selfservice.kiosk.ui.screens.ObdDetailsScreen
import com.selfservice.kiosk.ui.screens.ObdInputScreen
import com.selfservice.kiosk.ui.screens.ObdPrepScreen
import com.selfservice.kiosk.ui.screens.ObdResultsScreen
import com.selfservice.kiosk.ui.screens.ObdScanningScreen
import com.selfservice.kiosk.ui.screens.PaymentQRScreen
import com.selfservice.kiosk.ui.screens.ReportSentScreen
import com.selfservice.kiosk.ui.screens.ServiceSelectionScreen
import com.selfservice.kiosk.ui.screens.ThicknessInputScreen
import com.selfservice.kiosk.ui.screens.ThicknessInstructionsScreen
import com.selfservice.kiosk.ui.screens.ThicknessMeasurementScreen
import com.selfservice.kiosk.ui.screens.ThicknessPrepScreen
import com.selfservice.kiosk.ui.screens.ThicknessResultsScreen
import com.selfservice.kiosk.ui.screens.WelcomeScreen
import com.selfservice.kiosk.ui.state.ServiceType
import com.selfservice.kiosk.ui.state.SessionState
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import com.selfservice.kiosk.ui.viewmodels.SessionViewModel
import com.selfservice.kiosk.ui.viewmodels.ObdFlowViewModel
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel

@Composable
fun KioskFlowHost(
    sessionViewModel: SessionViewModel,
    thicknessFlowViewModel: ThicknessFlowViewModel,
    obdFlowViewModel: ObdFlowViewModel,
    paymentViewModel: PaymentViewModel,
    modifier: Modifier = Modifier
) {
    val currentRoute by sessionViewModel.currentRoute.collectAsState()
    val sessionState by sessionViewModel.sessionState.collectAsState()
    val obdState by obdFlowViewModel.state.collectAsState()
    val resetFlows = {
        thicknessFlowViewModel.reset()
        obdFlowViewModel.reset()
    }

    Crossfade(targetState = currentRoute, modifier = modifier) { route ->
        when (route) {
            KioskRoute.Attract -> AttractScreen(onTap = {
                sessionViewModel.onNavigationEvent(NavigationEvent.Tap)
            })

            KioskRoute.Welcome -> WelcomeScreen(
                onContinue = { sessionViewModel.onNavigationEvent(NavigationEvent.Agree) }
            )

            KioskRoute.ServiceSelection -> ServiceSelectionScreen(
                onSelectThickness = { sessionViewModel.onNavigationEvent(NavigationEvent.SelectThickness) },
                onSelectObd = { sessionViewModel.onNavigationEvent(NavigationEvent.SelectObd) }
            )

            KioskRoute.ThicknessInput -> ThicknessInputScreen { vehicleType, phone, email ->
                sessionViewModel.onNavigationEvent(
                    NavigationEvent.SubmitThicknessDetails(vehicleType, phone, email)
                )
            }

            KioskRoute.ThicknessPayment -> ThicknessPaymentRoute(
                sessionState = sessionState,
                sessionViewModel = sessionViewModel,
                paymentViewModel = paymentViewModel,
                resetFlows = resetFlows
            )

            KioskRoute.ThicknessPrep -> ThicknessPrepScreen(
                viewModel = thicknessFlowViewModel,
                onReady = { sessionViewModel.onNavigationEvent(NavigationEvent.DeviceReady) }
            )

            KioskRoute.ThicknessInstructions -> ThicknessInstructionsScreen(
                viewModel = thicknessFlowViewModel,
                onBack = { sessionViewModel.onNavigationEvent(NavigationEvent.PaymentComplete) },
                onStartMeasurements = { sessionViewModel.onNavigationEvent(NavigationEvent.BeginMeasurement) }
            )

            KioskRoute.ThicknessMeasurement -> ThicknessMeasurementScreen(
                viewModel = thicknessFlowViewModel,
                onComplete = { sessionViewModel.onNavigationEvent(NavigationEvent.MeasurementComplete) }
            )

            KioskRoute.ThicknessResults -> ThicknessResultsScreen(
                viewModel = thicknessFlowViewModel,
                onSendReport = {
                    sessionViewModel.onNavigationEvent(NavigationEvent.ReportSent)
                },
                onBackToMain = {
                    resetFlows()
                    sessionViewModel.onNavigationEvent(NavigationEvent.ToMain)
                }
            )

            KioskRoute.ObdInput -> ObdInputScreen { brand, phone, email ->
                sessionViewModel.onNavigationEvent(
                    NavigationEvent.SubmitObdDetails(
                        vehicleBrand = brand,
                        phone = phone,
                        email = email
                    )
                )
            }

            KioskRoute.ObdPayment -> ObdPaymentRoute(
                sessionState = sessionState,
                sessionViewModel = sessionViewModel,
                paymentViewModel = paymentViewModel,
                resetFlows = resetFlows
            )

            KioskRoute.ObdPrep -> ObdPrepScreen(
                viewModel = obdFlowViewModel,
                onReady = { sessionViewModel.onNavigationEvent(NavigationEvent.DeviceReady) }
            )

            KioskRoute.ObdScanning -> ObdScanningScreen(
                viewModel = obdFlowViewModel,
                onScanComplete = { sessionViewModel.onNavigationEvent(NavigationEvent.ScanComplete) },
                onAbort = {
                    resetFlows()
                    sessionViewModel.resetSession()
                }
            )

            KioskRoute.ObdResults -> ObdResultsScreen(
                state = obdState,
                onSendReport = {
                    sessionViewModel.onNavigationEvent(NavigationEvent.ReportSent)
                },
                onViewDetails = {
                    sessionViewModel.onNavigationEvent(NavigationEvent.ViewObdDetails)
                },
                onFinish = {
                    resetFlows()
                    sessionViewModel.onNavigationEvent(NavigationEvent.ToMain)
                }
            )

            KioskRoute.ObdPaywall -> ObdPaywallRoute(
                sessionState = sessionState,
                sessionViewModel = sessionViewModel,
                paymentViewModel = paymentViewModel,
                onBackToResults = {
                    sessionViewModel.onNavigationEvent(NavigationEvent.ScanComplete)
                }
            )

            KioskRoute.ObdDetails -> ObdDetailsScreen(
                state = obdState,
                viewModel = obdFlowViewModel,
                onSendReport = {
                    sessionViewModel.onNavigationEvent(NavigationEvent.ReportSent)
                },
                onClose = {
                    resetFlows()
                    sessionViewModel.onNavigationEvent(NavigationEvent.ToMain)
                }
            )

            KioskRoute.ReportSent -> ReportSentScreen(
                onDone = {
                    resetFlows()
                    sessionViewModel.onNavigationEvent(NavigationEvent.ToMain)
                }
            )
        }
    }
}

@Composable
private fun ThicknessPaymentRoute(
    sessionState: SessionState,
    sessionViewModel: SessionViewModel,
    paymentViewModel: PaymentViewModel,
    resetFlows: () -> Unit
) {
    val amountRub = (sessionState.serviceType as? ServiceType.Thickness)?.price ?: 0
    PaymentRoute(
        sessionState = sessionState,
        sessionViewModel = sessionViewModel,
        paymentViewModel = paymentViewModel,
        amountRub = amountRub,
        serviceTypeKey = "thickness",
        onSessionReset = resetFlows
    )
}

@Composable
private fun ObdPaymentRoute(
    sessionState: SessionState,
    sessionViewModel: SessionViewModel,
    paymentViewModel: PaymentViewModel,
    resetFlows: () -> Unit
) {
    val amountRub = (sessionState.serviceType as? ServiceType.OBD)?.price ?: 0
    PaymentRoute(
        sessionState = sessionState,
        sessionViewModel = sessionViewModel,
        paymentViewModel = paymentViewModel,
        amountRub = amountRub,
        serviceTypeKey = "obd",
        onSessionReset = resetFlows
    )
}

@Composable
private fun ObdPaywallRoute(
    sessionState: SessionState,
    sessionViewModel: SessionViewModel,
    paymentViewModel: PaymentViewModel,
    onBackToResults: () -> Unit
) {
    val amountRub = (sessionState.serviceType as? ServiceType.OBD)?.price ?: 0
    PaymentRoute(
        sessionState = sessionState,
        sessionViewModel = sessionViewModel,
        paymentViewModel = paymentViewModel,
        amountRub = amountRub,
        serviceTypeKey = "obd_paywall",
        title = "Детальный отчёт OBD-II",
        subtitle = "Для просмотра расшифровки кодов требуется подтверждённый платёж.",
        onPaymentConfirmed = {
            sessionViewModel.onNavigationEvent(NavigationEvent.PaywallPaid)
        },
        onCancelPayment = {
            paymentViewModel.cancelPayment()
            onBackToResults()
        },
        onTimeoutPayment = {
            paymentViewModel.cancelPayment()
            onBackToResults()
        }
    )
}

@Composable
private fun PaymentRoute(
    sessionState: SessionState,
    sessionViewModel: SessionViewModel,
    paymentViewModel: PaymentViewModel,
    amountRub: Int,
    serviceTypeKey: String,
    title: String = "Оплата услуги",
    subtitle: String = "Сканируйте QR-код через приложение банка. Сессия автоматически завершится через 10 минут.",
    onSessionReset: () -> Unit = {},
    onPaymentConfirmed: () -> Unit = {
        sessionViewModel.onNavigationEvent(NavigationEvent.PaymentComplete)
    },
    onCancelPayment: () -> Unit = {
        paymentViewModel.cancelPayment()
        sessionViewModel.resetSession()
        onSessionReset()
    },
    onTimeoutPayment: () -> Unit = {
        paymentViewModel.cancelPayment()
        sessionViewModel.resetSession()
        onSessionReset()
    }
) {
    val paymentContact = remember(sessionState.customerPhone, sessionState.customerEmail) {
        if (sessionState.customerPhone.isBlank() && sessionState.customerEmail.isBlank()) {
            null
        } else {
            PaymentContact(
                email = sessionState.customerEmail.ifBlank { null },
                phone = sessionState.customerPhone.ifBlank { null }
            )
        }
    }

    LaunchedEffect(sessionState.sessionId, amountRub, paymentContact, serviceTypeKey) {
        if (amountRub > 0) {
            paymentViewModel.createPaymentIntent(
                sessionId = sessionState.sessionId,
                amount = amountRub * 100L,
                serviceType = serviceTypeKey,
                contact = paymentContact
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { paymentViewModel.reset() }
    }

    PaymentQRScreen(
        viewModel = paymentViewModel,
        amount = amountRub,
        onPaymentComplete = onPaymentConfirmed,
        onCancel = onCancelPayment,
        onTimeout = onTimeoutPayment,
        title = title,
        subtitle = subtitle
    )
}

