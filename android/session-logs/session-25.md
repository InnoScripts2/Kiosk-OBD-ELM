# Session 25 — YooKassa Secret Guardrail

Дата: 23.11.2025
Диапазон: Фаза E, сессия 25 (PSP безопасность / CI smoke)

## Цели
- Протянуть `YOOKASSA_WEBHOOK_SECRET` из Vault в Gradle через переменные окружения, чтобы CI мог передавать секрет без локальных файлов.
- Добавить fail-fast smoke, блокирующий сборки/тесты QA/PROD без секрета.
- Обновить планы секретов/тестирования, зафиксировав новую команду и требования к pipeline.

## Выполненные действия
- В `android/app/build.gradle.kts` добавлен обходной путь: Gradle теперь читает `payments.yookassa.webhookSecret` из параметра, а при его отсутствии — из `YOOKASSA_WEBHOOK_SECRET`/`PAYMENTS_YOOKASSA_WEBHOOK_SECRET`. Зарегистрирован таск `checkYooKassaWebhookSecret`, который подключён к `preBuild` и всем unit-тестам и прекращает сборку, если секрет обязателен для окружения QA/PROD, но не задан.
- Обновлены `plan-secrets-config.md` и `plan-testing.md`: описан новый smoke, добавлено требование экспортировать переменную окружения на шаге CI, уточнён порядок запуска `checkYooKassaWebhookSecret → YooKassaPaymentGatewayTest → PaymentGatewayResolverTest`.
- Интеграции с донорскими каталогами: не выполнялись — вся работа внутри `android/` и документов плана.

## Тесты
- `./gradlew :app:checkYooKassaWebhookSecret`
- `./gradlew :app:testDebugUnitTest --tests com.selfservice.kiosk.payments.PaymentGatewayResolverTest`

## Следующие шаги
- Настроить pipeline Vault → env, чтобы `YOOKASSA_WEBHOOK_SECRET` автоматически экспортировался в QA/PROD и запускал новый smoke до сборки.
- Расширить telemetry/Supabase webhook audit, добавив метрики и e2e-smoke для REST вебхуков в `kiosk-shell/agent`.
- Зафиксировать требование в `credential-inventory.md` после загрузки секрета в Vault и обновления CI.
