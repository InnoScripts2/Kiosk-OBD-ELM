# Session 22 — Интеграция PSP YooKassa

Дата: 21.11.2025
Диапазон: Фаза E, сессия 22 (платёжный шлюз и аудит)

## Цели
- Заменить dev-эмулятор реальным платёжным шлюзом YooKassa в модуле `feature-payments`.
- Протянуть конфигурацию PSP в Android-приложение через BuildConfig и обеспечить фолбэк в DEV.
- Обновить PCI/секретные инструкции и чек-листы тестирования.

## Выполненные действия
- Реализован `YooKassaPaymentGateway` с собственным HTTP-клиентом, сериализацией квитанций, метаданных, webhook-обработчиком и конвертацией статусов PSP → `PaymentStatus`.
- Добавлены unit-тесты `YooKassaPaymentGatewayTest` (валидируют payload, квитанции, webhook) и вспомогательный `HttpClient` для изоляции HTTP.
- В `app` создан `PaymentGatewayResolver`, который выбирает dev/YooKassa шлюз на основе BuildConfig и валидирует наличие `shopId/secretKey`. Добавлены unit-тесты `PaymentGatewayResolverTest`.
- `KioskApp` теперь передаёт GatewayResolver в `PaymentModule`. BuildConfig обогащён полями `PAYMENTS_GATEWAY`, `YOOKASSA_*`, значения читаются из Gradle-параметров `payments.*`.
- Обновлён `plan-secrets-config.md` (секреты PSP, пример gradle.properties, шаги ротации) и `plan-testing.md` (новые команды для Android-тестов YooKassa).

## Тесты
- `./gradlew :feature-payments:testDebugUnitTest :app:testDebugUnitTest`

## Следующие шаги
- Подготовить smoke-инструкции для PSP в `plan-testing.md` (Supabase audit + YooKassa webhooks) и связать с будущей сессией 23 (ingest Diagnostics UI).
- После получения реальных реквизитов заполнить `payments.yookassa.*` в CI и повторить Android smoke на устройстве.
