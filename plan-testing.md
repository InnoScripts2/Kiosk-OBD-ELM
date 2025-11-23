# План тестирования — контрольные листы

## Сессия 19 — генерация отчётов диагностики

- [x] Обновить контроль очереди Supabase для отчётов диагностики (см. `05-integrations/02-application/supabase/control-reports/supabase-diagnostics-report-deliveries-control.session19.md`).
- [x] Провести сквозной smoke Supabase очереди (генерация → ingest → runner) — тест `src/reports/supabase-queue-smoke.test.ts` в `kiosk-shell/agent` запускается командой `npm --prefix 03-apps/02-application/kiosk-shell/agent test` и подтверждает доставку email/SMS и метрики Prometheus (`report_delivered_total`, `report_delivery_pending`).

## Сессия 21 — Edge cache и оффлайн синхронизация

- [x] Покрыть локальный SQLCipher-store тестами `src/storage/edge-cache/EdgeCacheStore.test.ts` (проверка сохранения отчёта, постановки фаз sync_queue и перехода статусов `pending → metadata_synced → synced`).
- [x] Покрыть координацию синхронизации тестом `src/edge-cache/EdgeSyncCoordinator.test.ts` (успешная загрузка метаданных/пейлоада + отложенная обработка payload при ошибке metadata).
- [x] Зафиксировать контрольные метрики Prometheus: `edge_sync_pending` (глубина очереди) и `edge_sync_failures_total{phase,kind}` (ошибки по фазам/типам отчётов). Доступ через `/metrics` агента.
- [x] Документировать конфигурацию:
	- `EDGE_CACHE_DISABLED` — выключает хранение оффлайн-отчётов.
	- `EDGE_CACHE_DB_PATH`, `EDGE_CACHE_ENCRYPTION_KEY`, `EDGE_CACHE_RETENTION_DAYS`/`EDGE_CACHE_RETENTION_MS` — путь, ключ SQLCipher и retention (по умолчанию 30 суток).
	- `EDGE_SYNC_DISABLED`, `EDGE_SYNC_POLL_MS`, `EDGE_SYNC_BATCH`, `EDGE_SYNC_RETRY_DELAY_MS` — параметры координации выгрузки в Supabase.
	- `EDGE_CACHE_PRUNE_INTERVAL_MS` — периодичность прунинга оффлайн-кеша и сессий.
- [x] Команда прогона: `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern edge-cache` либо полный `npm test` для сверки smoke.

## Сессия 22 — платёжный шлюз и аудит

- [x] PSP sandbox smoke: 
	- `npm --prefix 04-packages/02-application/modules/service/payments/core test` — обновлённые тесты `payment-module.test.ts` проверяют идемпотентное создание intent, расчёт долей партнёров, ручные подтверждения и метрики стора (`pendingOlderThanCount`).
	- `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern payments` — smoke `src/payments/routes.test.ts` поднимает Express-приложение с `PaymentModuleRouter`, эмулирует PSP и проверяет `/payments/*` (создание intent, confirm-dev, manual-confirm, сериализацию QR-кодов и `/payments/metrics`).
	- `pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment <ENV> -EmitPipelineVariables` → `./gradlew :app:checkYooKassaWebhookSecret -Pkiosk.environment=<ENV> -Ppayments.gateway=yookassa` — последовательность fail-fast smoke, которая сначала экспортирует секрет из Vault в переменные окружения, а затем проверяет, что Gradle действительно его видит перед запуском unit-тестов/сборки.
	- `./gradlew :feature-payments:testDebugUnitTest --tests "*YooKassaPaymentGatewayTest*"` — проверяет сериализацию payload, webhook-обработчик, валидацию подписи `X-YooKassa-Signature-Sha256` (HMAC-SHA256) и преобразование статусов боевого PSP; перед запуском нужно выставить `payments.yookassa.webhookSecret` в Gradle.
	- `./gradlew :app:testDebugUnitTest --tests "*PaymentGatewayResolverTest*"` — убеждаемся, что `PaymentGatewayResolver` корректно переключается между dev/YooKassa шлюзами, валидирует наличие обязательных PSP-секретов и не допускает работу YooKassa в QA/PROD без `YOOKASSA_WEBHOOK_SECRET`.
	- `pwsh ./android/scripts/export-supabase-service-key.ps1 -Environment <ENV> -EmitPipelineVariables` → `./gradlew :app:checkSupabaseServiceKey -Pkiosk.environment=<ENV>` — fail-fast проверка Supabase service key перед запуском Android unit-тестов/сборки. После успешного шага выполняем `./gradlew :app:testDebugUnitTest --tests "*SupabasePaymentAuditSinkTest*"` и Supabase smoke `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern supabase`.
- [x] PCI/Security чек-лист: `plan-secrets-config.md` дополнен требованием хранить `.selfservice/payments/intents.json` на шифрованном разделе, описан процесс ротации `PAYMENTS_ENCRYPTION_KEY` через Vault и smoke-команды (`npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern payments`).
- [x] Метрики платежей: 
	- REST-снимок `/payments/metrics` возвращает `environment`, `gateway`, `store.byStatus`, `pendingOlderThanMs`/`pendingOlderThanCount` и `capturedAt`; значения фиксируются в runbook.
	- `/metrics` (Prometheus) включает показатели `payments_total_intents`, `payments_status_total{status=…}`, `payments_pending_over_90_seconds{threshold_ms}` и `payments_pending_oldest_ms`; перед smoke вызывается `paymentsPromCollector.update()`.
- [x] Аудит платежей:
	- `./gradlew :feature-payments:testDebugUnitTest --tests "*PaymentModuleTest.audit*"` и `--tests "*FilePaymentAuditSinkTest*"` подтверждают генерацию событий `intent_created`, `status_checked`, `intent_manual_confirmed` и ротацию файла аудита.
	- manual smoke: `adb shell run-as com.selfservice.kiosk cat files/.selfservice/payments/audit.ndjson | tail -n 5` показывает последние записи перед выгрузкой в Supabase (`payments_audit`).
	- `./gradlew :app:testDebugUnitTest --tests "*SupabasePaymentAuditSinkTest*"` проверяет упаковку событий в формат Supabase outbox (`payments_audit`, ключи `event_type`, `kiosk_id`, `environment`).

## Сессии 31–33 — подготовка к полевым тестам (UI/OBD/ELM)

**Цель цикла:** получить полевой билд, в котором UI диагностических экранов, стек OBD-II и ELM327-транспорт проходят smoke без ADB/IDE и имеют понятные инструкции для инженеров.

### Сессия 31 *(текущая — планирование и гейты)*
- Зафиксировать чек-лист готовности UI/OBD/ELM (гейты: diag overlay, PID каталоги, ELM snapshot tooling).
- Обновить `plan-testing.md` и `session-logs/session-30.md` ссылками на новые инструменты (`ElmPidSnapshotGenerator`, Mode 09 payload parity, DTC проверки).
- Подтвердить, что unit-тесты `:feature-obd-core:testDebugUnitTest` и `:feature-obd-elm-port:testDebugUnitTest` служат минимальным быстрым smoke перед любыми полевыми выездами.

### Сессия 32 *(Инструментация UI + OBD runtime smoke)*
- **UI:**
  - Добавить в `MainActivity` DEV-флаг `field_test_mode`, который переключает диагностический overlay в режим «крупный шрифт + статусы VIN/CAL ID/DTC» без подключения дебагера.
  - Проверить `DiagnosticsJavascriptBridgeTest`/`MainActivityDiagnosticsMetricsPanelTest` с новым режимом (`./gradlew :app:testDebugUnitTest --tests "*Diagnostics*"`).
- **OBD/ELM:**
  - Интегрировать snapshot`ы Mode 09 в `feature-obd-diagnostics` (UI отображение ожидаемой длины VIN/CAL ID, подсветка ошибок).
  - Прогнать `./gradlew :feature-obd-core:testDebugUnitTest :feature-obd-elm-port:testDebugUnitTest` и `node tmp-emulator-test/EmulatorTests.test.js` (имитация ELM327).
- **Документация:** описать процедуру включения field-test режима и команды smoke в `plan-testing.md` / runbook.

### Сессия 33 *(Хардварные проверки + релизный билд)*
- **UI:** e2e `./gradlew :app:connectedDebugAndroidTest` на стенде с Device Owner, убедиться в корректной работе overlay/MDM подсистемы.
- **OBD:** `pwsh ./android/tools/pass-thru-smoke.ps1 -Adapter ELM327` + ручной прогон USB/BLE транспорта; зафиксировать логи в `session-logs/session-33.md`.
- **ELM:** собрать `ElmPidSnapshotGenerator` артефакты для VIN/CAL/ECU Name и приложить к отчёту, подтвердить, что реальные ответы соответствуют длинам из каталога.
- **Релизный пакет:** `./gradlew :app:assembleFieldTest -Pkiosk.environment=qa -Ppayments.gateway=stub` (вариант профиля) + выгрузка чек-листа: список smoke-команд, список секретов, шаблон отчёта по выезду.

**Критерии готовности:**
1. UI диагностических экранов включается аппаратной комбинацией, отображает VIN/CAL ID/DTC c подсветкой ошибок Mode 09.
2. Все гейты `:feature-obd-core:testDebugUnitTest`, `:feature-obd-elm-port:testDebugUnitTest`, `tmp-emulator-test`, `pass-thru-smoke.ps1` проходят без ручных фиксов.
3. Для QA/field инженеров есть инструкция: включение `field_test_mode`, экспорт секретов (`export-supabase-service-key.ps1`, `export-yookassa-webhook-secret.ps1`), список adb/Gradle команд и шаблон отчёта.
