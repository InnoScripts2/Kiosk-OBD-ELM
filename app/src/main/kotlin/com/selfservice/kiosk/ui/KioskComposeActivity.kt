package com.selfservice.kiosk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.KioskApp
import com.selfservice.kiosk.ui.viewmodels.ObdFlowViewModel
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModelFactory
import com.selfservice.kiosk.ui.viewmodels.SessionViewModel
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.platform.ui.foundation.KioskTheme

/**
 * Основная Compose-активити, отражающая фронтенд-дизайн внутри нативного приложения.
 */
class KioskComposeActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()
    private val thicknessFlowViewModel: ThicknessFlowViewModel by viewModels()
    private val obdFlowViewModel: ObdFlowViewModel by viewModels()
    private val paymentViewModel: PaymentViewModel by viewModels {
        PaymentViewModelFactory(KioskApp.payments(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionViewModel.startTimeoutMonitoring()

        setContent {
            KioskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KioskFlowHost(
                        sessionViewModel = sessionViewModel,
                        thicknessFlowViewModel = thicknessFlowViewModel,
                        obdFlowViewModel = obdFlowViewModel,
                        paymentViewModel = paymentViewModel
                    )
                }
            }
        }
    }
}
