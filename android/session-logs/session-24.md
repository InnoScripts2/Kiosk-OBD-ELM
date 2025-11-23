# Session 24 — YooKassa Webhook Hardening

Дата: 23.11.2025
Диапазон: Фаза E, сессия 24 (PSP безопасность и аудит)

## Цели
- Исключить обработку неподписанных вебхуков YooKassa в Android-клиенте.
- Протянуть секрет подписи в конфигурацию `feature-payments` и зафиксировать требования в планах секретов/тестирования.
- Расширить unit-тесты платёжного модуля и PSP-адаптера, покрыв сценарии HMAC-подписи и прокси необработанного payload.

## Выполненные действия
- Добавлен `payments.yookassa.webhookSecret` в `YooKassaGatewayConfig`: хранение в Vault, чтение в Gradle и использование в `YooKassaPaymentGateway`. Реализована проверка заголовков `X-YooKassa-Signature-*` (HMAC-SHA256/HMAC-SHA1), константное сравнение подписи (hex/base64) и ранний отказ при отсутствии secret/raw body.
- В `app/build.gradle.kts` и `PaymentGatewayResolver` протянут Gradle-параметр `payments.yookassa.webhookSecret` → `BuildConfig.YOOKASSA_WEBHOOK_SECRET` → `YooKassaGatewayConfig`, чтобы секрет вебхуков задавался на уровне окружения без хардкода и был доступен PSP-адаптеру.
- `PaymentGatewayResolver` теперь запрещает включать YooKassa в QA/PROD при отсутствии `YOOKASSA_WEBHOOK_SECRET`, автоматически откатываясь к dev-шлюзу и логируя ошибку.
- Расширен контракт `PaymentGateway.handleWebhook(...)` и `PaymentModule.handleWebhook(...)` дополнительным параметром `rawPayload` — теперь модуль передаёт сырое тело вебхука в шлюз (обновлены `DevPaymentGateway`, тестовые двойники и `PaymentModuleTest`).
- Обновлены unit-тесты `YooKassaPaymentGatewayTest` (валидная/невалидная подписи) и `PaymentModuleTest` (проверка проксирования raw payload). Документы `plan-secrets-config.md` и `plan-testing.md` дополнены инструкциями по `YOOKASSA_WEBHOOK_SECRET` и SMOKE-командам.

## Тесты
- `./gradlew :feature-payments:testDebugUnitTest --tests "*YooKassaPaymentGatewayTest*" --tests "*PaymentModuleTest*"`

## Следующие шаги
- Настроить прокатку `payments.yookassa.webhookSecret` через Vault → Gradle (CI) и добавить smoke, проверяющий, что секрет действительно задан перед запуском интеграционных тестов/сборки.
- Интегрировать webhook metrics/telemetry в Supabase (`payments_audit`) и добавить e2e smoke для REST вебхуков в `03-apps/02-application/kiosk-shell/agent`.
- Согласовать ротацию секрета и обновить `credential-inventory.md` после загрузки в Vault.
