package com.selfservice.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для управления доставкой отчётов.
 * 
 * Оркестрирует процесс отправки отчёта по email/SMS:
 * - Управление состоянием доставки
 * - Retry логика при ошибках
 * - Обработка результатов доставки
 * - Интеграция с ReportService
 */
class ReportDeliveryViewModel(
    private val reportService: ReportServiceImpl
) : ViewModel() {
    
    private val _deliveryState = MutableStateFlow<DeliveryState>(DeliveryState.Idle)
    val deliveryState: StateFlow<DeliveryState> = _deliveryState.asStateFlow()
    
    private val _emailResult = MutableStateFlow<DeliveryResult?>(null)
    val emailResult: StateFlow<DeliveryResult?> = _emailResult.asStateFlow()
    
    private val _smsResult = MutableStateFlow<DeliveryResult?>(null)
    val smsResult: StateFlow<DeliveryResult?> = _smsResult.asStateFlow()
    
    /**
     * Отправляет отчёт по email.
     * 
     * @param sessionId ID сессии
     * @param email адрес получателя
     * @param reportType тип отчёта (THICKNESS или DIAGNOSTICS)
     * @param attachPdf прикрепить PDF файл
     */
    fun sendEmail(
        sessionId: String,
        email: String,
        reportType: ReportType,
        attachPdf: Boolean = true
    ) {
        viewModelScope.launch {
            _deliveryState.value = DeliveryState.SendingEmail
            
            try {
                val result = reportService.sendReportByEmail(
                    sessionId = sessionId,
                    email = email,
                    reportType = reportType,
                    attachPdf = attachPdf
                )
                
                _emailResult.value = result
                
                if (result.success) {
                    _deliveryState.value = DeliveryState.EmailSent
                } else {
                    _deliveryState.value = DeliveryState.Error(
                        channel = DeliveryChannel.EMAIL,
                        error = result.error ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _emailResult.value = DeliveryResult(
                    success = false,
                    channel = DeliveryChannel.EMAIL,
                    messageId = null,
                    recipient = email,
                    deliveryTimeMs = 0,
                    error = e.message
                )
                _deliveryState.value = DeliveryState.Error(
                    channel = DeliveryChannel.EMAIL,
                    error = e.message ?: "Exception during email delivery"
                )
            }
        }
    }
    
    /**
     * Отправляет SMS с кратким резюме отчёта.
     * 
     * @param sessionId ID сессии
     * @param phone номер телефона
     * @param reportType тип отчёта
     */
    fun sendSms(
        sessionId: String,
        phone: String,
        reportType: ReportType
    ) {
        viewModelScope.launch {
            _deliveryState.value = DeliveryState.SendingSms
            
            try {
                val result = reportService.sendReportBySms(
                    sessionId = sessionId,
                    phone = phone,
                    reportType = reportType
                )
                
                _smsResult.value = result
                
                if (result.success) {
                    _deliveryState.value = DeliveryState.SmsSent
                } else {
                    _deliveryState.value = DeliveryState.Error(
                        channel = DeliveryChannel.SMS,
                        error = result.error ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _smsResult.value = DeliveryResult(
                    success = false,
                    channel = DeliveryChannel.SMS,
                    messageId = null,
                    recipient = phone,
                    deliveryTimeMs = 0,
                    error = e.message
                )
                _deliveryState.value = DeliveryState.Error(
                    channel = DeliveryChannel.SMS,
                    error = e.message ?: "Exception during SMS delivery"
                )
            }
        }
    }
    
    /**
     * Отправляет отчёт по обоим каналам (email + SMS).
     * 
     * @param sessionId ID сессии
     * @param email адрес получателя
     * @param phone номер телефона
     * @param reportType тип отчёта
     * @param attachPdf прикрепить PDF к email
     */
    fun sendBoth(
        sessionId: String,
        email: String,
        phone: String,
        reportType: ReportType,
        attachPdf: Boolean = true
    ) {
        viewModelScope.launch {
            _deliveryState.value = DeliveryState.SendingBoth
            
            // Отправка email
            val emailResult = try {
                reportService.sendReportByEmail(sessionId, email, reportType, attachPdf)
            } catch (e: Exception) {
                DeliveryResult(
                    success = false,
                    channel = DeliveryChannel.EMAIL,
                    messageId = null,
                    recipient = email,
                    deliveryTimeMs = 0,
                    error = e.message
                )
            }
            _emailResult.value = emailResult
            
            // Отправка SMS
            val smsResult = try {
                reportService.sendReportBySms(sessionId, phone, reportType)
            } catch (e: Exception) {
                DeliveryResult(
                    success = false,
                    channel = DeliveryChannel.SMS,
                    messageId = null,
                    recipient = phone,
                    deliveryTimeMs = 0,
                    error = e.message
                )
            }
            _smsResult.value = smsResult
            
            // Определение финального состояния
            _deliveryState.value = when {
                emailResult.success && smsResult.success -> DeliveryState.BothSent
                emailResult.success || smsResult.success -> DeliveryState.PartiallySent
                else -> DeliveryState.Error(
                    channel = DeliveryChannel.EMAIL, // можно указать оба
                    error = "Both email and SMS delivery failed"
                )
            }
        }
    }
    
    /**
     * Повторяет попытку отправки email.
     */
    fun retryEmail(
        sessionId: String,
        email: String,
        reportType: ReportType,
        attachPdf: Boolean = true
    ) {
        sendEmail(sessionId, email, reportType, attachPdf)
    }
    
    /**
     * Повторяет попытку отправки SMS.
     */
    fun retrySms(
        sessionId: String,
        phone: String,
        reportType: ReportType
    ) {
        sendSms(sessionId, phone, reportType)
    }
    
    /**
     * Сбрасывает состояние доставки в Idle.
     */
    fun resetState() {
        _deliveryState.value = DeliveryState.Idle
        _emailResult.value = null
        _smsResult.value = null
    }
}

/**
 * Состояния процесса доставки отчёта.
 */
sealed class DeliveryState {
    /** Ожидание действия пользователя */
    object Idle : DeliveryState()
    
    /** Отправка email */
    object SendingEmail : DeliveryState()
    
    /** Email успешно отправлен */
    object EmailSent : DeliveryState()
    
    /** Отправка SMS */
    object SendingSms : DeliveryState()
    
    /** SMS успешно отправлен */
    object SmsSent : DeliveryState()
    
    /** Отправка обоих каналов */
    object SendingBoth : DeliveryState()
    
    /** Оба канала успешно отправлены */
    object BothSent : DeliveryState()
    
    /** Один из каналов отправлен, другой упал */
    object PartiallySent : DeliveryState()
    
    /** Ошибка доставки */
    data class Error(
        val channel: DeliveryChannel,
        val error: String
    ) : DeliveryState()
}

/**
 * Extension для получения человекочитаемого сообщения состояния.
 */
val DeliveryState.displayMessage: String
    get() = when (this) {
        is DeliveryState.Idle -> "Готов к отправке"
        is DeliveryState.SendingEmail -> "Отправляем отчёт на email..."
        is DeliveryState.EmailSent -> "Отчёт отправлен на email"
        is DeliveryState.SendingSms -> "Отправляем SMS..."
        is DeliveryState.SmsSent -> "SMS отправлено"
        is DeliveryState.SendingBoth -> "Отправляем отчёт..."
        is DeliveryState.BothSent -> "Отчёт отправлен на email и SMS"
        is DeliveryState.PartiallySent -> "Отчёт отправлен частично"
        is DeliveryState.Error -> "Ошибка: $error"
    }

/**
 * Extension для проверки, является ли состояние финальным (успех или ошибка).
 */
val DeliveryState.isTerminal: Boolean
    get() = this is DeliveryState.EmailSent 
            || this is DeliveryState.SmsSent 
            || this is DeliveryState.BothSent 
            || this is DeliveryState.PartiallySent
            || this is DeliveryState.Error

/**
 * Extension для проверки, идёт ли отправка.
 */
val DeliveryState.isLoading: Boolean
    get() = this is DeliveryState.SendingEmail 
            || this is DeliveryState.SendingSms 
            || this is DeliveryState.SendingBoth
