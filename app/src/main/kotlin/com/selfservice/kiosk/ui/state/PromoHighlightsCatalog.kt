package com.selfservice.kiosk.ui.state

/**
 * Каталог маркетинговых сообщений для экранов приветствия.
 * Данные вынесены из UI, чтобы их можно было переиспользовать и покрыть тестами.
 */
object PromoHighlightsCatalog {
    data class PromoHighlight(
        val title: String,
        val description: String,
        val accent: String
    )

    data class QuickStat(
        val label: String,
        val value: String,
        val helper: String
    )

    fun heroHighlights(): List<PromoHighlight> = listOf(
        PromoHighlight(
            title = "Толщинометрия",
            description = "40–60 точек замеров, слот выдаёт прибор после оплаты",
            accent = "8–12 минут"
        ),
        PromoHighlight(
            title = "Диагностика OBD-II",
            description = "Чтение и расшифровка DTC, при необходимости Clear DTC",
            accent = "90 сек сканирования"
        ),
        PromoHighlight(
            title = "Готовый отчёт",
            description = "PDF и SMS/Email отправка, контакты храним 30 дней",
            accent = "сразу после услуги"
        )
    )

    fun welcomeSteps(): List<PromoHighlight> = listOf(
        PromoHighlight(
            title = "Выберите услугу",
            description = "Толщинометрия ЛКП или диагностика OBD-II",
            accent = "Шаг 1"
        ),
        PromoHighlight(
            title = "Оплатите QR-код",
            description = "Принимаем YooKassa, подтверждение приходит автоматически",
            accent = "Шаг 2"
        ),
        PromoHighlight(
            title = "Следуйте подсказкам",
            description = "Киоск показывает как подключать адаптер и где измерять",
            accent = "Шаг 3"
        )
    )

    fun agreementClauses(): List<String> = listOf(
        "Самостоятельный формат: подсказки на каждом шаге",
        "Устройства выдаются после подтверждения оплаты",
        "Контакты нужны только для отправки отчёта и удаляются через 30 дней"
    )

    fun quickStats(): List<QuickStat> = listOf(
        QuickStat(
            label = "Время услуги",
            value = "8–12 мин",
            helper = "от старта до отчёта"
        ),
        QuickStat(
            label = "Подсказки",
            value = "24/7",
            helper = "на экране и в отчёте"
        ),
        QuickStat(
            label = "Оплата",
            value = "QR / YooKassa",
            helper = "подтверждение автоматически"
        )
    )
}
