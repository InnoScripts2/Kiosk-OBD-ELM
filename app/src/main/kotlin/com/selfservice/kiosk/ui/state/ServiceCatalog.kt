package com.selfservice.kiosk.ui.state

/**
 * Статическое описание услуг киоска. Используется UI и тестами для контроля контента.
 */
object ServiceCatalog {
    enum class ServiceId { THICKNESS, OBD }

    data class ServiceDescriptor(
        val id: ServiceId,
        val title: String,
        val heroLine: String,
        val description: String,
        val priceLabel: String,
        val durationHint: String,
        val sellingPoints: List<String>,
        val complianceNote: String
    )

    fun primaryServices(): List<ServiceDescriptor> = listOf(thickness(), obd())

    fun descriptor(id: ServiceId): ServiceDescriptor = when (id) {
        ServiceId.THICKNESS -> thickness()
        ServiceId.OBD -> obd()
    }

    private fun thickness(): ServiceDescriptor = ServiceDescriptor(
        id = ServiceId.THICKNESS,
        title = "Толщинометрия ЛКП",
        heroLine = "Показывает перекрасы и шпаклёвку",
        description = "40–60 точек замеров по всей машине, пошаговые подсказки на экране.",
        priceLabel = "от 350 ₽",
        durationHint = "8–12 мин",
        sellingPoints = listOf(
            "Замеряем зоны кузова и сразу подсвечиваем отклонения",
            "После оплаты киоск открывает слот выдачи толщиномера",
            "Отчёт отправляем по SMS/email и храним контакты ≤30 дней"
        ),
        complianceNote = "Устройство выдаётся после подтверждённой оплаты"
    )

    private fun obd(): ServiceDescriptor = ServiceDescriptor(
        id = ServiceId.OBD,
        title = "Диагностика OBD-II",
        heroLine = "Читает и расшифровывает DTC",
        description = "Сканирует блоки управления, показывает статус систем и предлагает Clear DTC.",
        priceLabel = "480 ₽",
        durationHint = "до 90 сек",
        sellingPoints = listOf(
            "Разворачивает список кодов с пояснениями и статусами",
            "В DEV есть кнопка пропуска, в PROD работает только реальный адаптер",
            "Clear DTC выполняется после подтверждения клиента"
        ),
        complianceNote = "Соединение с адаптером обязательно, симуляции запрещены"
    )
}
