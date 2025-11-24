# План тестирования — контрольные листы

**Дата**: Обновлено 24.11.2025 (Session 2G)  
**Версия**: 1.2

## Оглавление
1. [Smoke-тесты DEV-окружения](#smoke-тесты-dev-окружения)
2. [Сессия 19 — генерация отчётов диагностики](#сессия-19--генерация-отчётов-диагностики)
3. [Сессия 21 — Edge cache и оффлайн синхронизация](#сессия-21--edge-cache-и-оффлайн-синхронизация)
4. [Сессия 22 — платёжный шлюз и аудит](#сессия-22--платёжный-шлюз-и-аудит)
5. [Сессии 31–33 — подготовка к полевым тестам](#сессии-3133--подготовка-к-полевым-тестам-uiobdelm)

---

## Smoke-тесты DEV-окружения

**Обновлено**: 23.11.2025 (Session 1G)

### Цель
Быстрая валидация основной функциональности без реальных устройств и платежей. Подходит для CI и локальной разработки.

### Предварительные условия
1. Создать файл `.env` на базе `.env.example`
2. Установить флаги DEV-режима:
   ```bash
   APP_MODE=DEV
   PAYMENT_MOCK=true
   DEVICE_MOCK_OBD=true
   DEVICE_MOCK_THICKNESS=true
   EMAIL_PROVIDER=MOCK
   SMS_PROVIDER=MOCK
   ```
3. Убедиться, что зависимости установлены:
   ```bash
   # Node.js компоненты
   npm --prefix 03-apps/02-application/kiosk-shell/agent install
   npm --prefix packages/report install
   npm --prefix packages/device-obd install
   npm --prefix packages/device-thickness install
   
   # Android (когда AGP 8.4.1 станет доступен)
   cd android && ./gradlew clean build
   ```

### Node.js Agent Smoke

**Команды**:
```bash
# Линтинг
npm --prefix 03-apps/02-application/kiosk-shell/agent run lint

# Все тесты
npm --prefix 03-apps/02-application/kiosk-shell/agent test

# Быстрый smoke (основные сервисы)
npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern "ArduinoAdapter|LockController|PaymentService|ReportService"
```

**Ожидаемый результат**:
- Линтинг: 0 ошибок, 0 warnings
- Тесты: все зелёные (46/46 для Session 21+)
- Время выполнения: < 60 сек

**Что проверяется**:
- ✅ Arduino Serial протокол (OPEN_*/CLOSE_*/STATUS/PING)
- ✅ Lock контроллер (выдача устройств, моки)
- ✅ Payment service (dev симуляция QR, confirmPayment)
- ✅ Report service (HTML/PDF генерация, delivery моки)

### Android Unit Tests Smoke

**Команды** (когда AGP 8.4.1 доступен):
```bash
cd android

# Быстрый smoke основных модулей
./gradlew :core:testDebugUnitTest \
          :feature-obd-core:testDebugUnitTest \
          :feature-obd-elm-port:testDebugUnitTest \
          :feature-lock-control:testDebugUnitTest \
          :feature-payments:testDebugUnitTest \
          :feature-reports:testDebugUnitTest \
          --parallel

# Полный прогон (без instrumented)
./gradlew test --parallel
```

**Ожидаемый результат**:
- Все unit-тесты зелёные (300+ тестов для Session 12+)
- Время выполнения: < 5 минут
- APK size check: ≥ 5.63 MB (Session 12 baseline)

**Что проверяется**:
- ✅ BLE сканер и connection manager
- ✅ OBD протокол (ELM327, ISO-TP, DTC parsing)
- ✅ Lock control через USB Serial
- ✅ Payment intents (AES-256 шифрование, dev/prod разделение)
- ✅ Report генераторы (HTML, PDF, checksums)

### Packages Smoke

**Команды**:
```bash
# device-obd
npm --prefix packages/device-obd test

# device-thickness
npm --prefix packages/device-thickness test

# report
npm --prefix packages/report test
```

**Ожидаемый результат**:
- Все тесты зелёные
- Время выполнения: < 30 сек
- Coverage: ≥ 70% (unit-tests)

### UI Navigation Smoke (Manual)

**Процедура**:
1. Запустить Android приложение в эмуляторе (когда доступно):
   ```bash
   cd android
   ./gradlew :app:installDebug
   adb shell am start -n com.selfservice.kiosk/.MainActivity
   ```
2. Пройти по экранам с кнопкой "Пропустить" (DEV-only):
   - Attract Screen → Welcome Screen → Service Selection
   - Толщиномер: Input → QR Payment (имитация) → Device Prep → Instructions → Measurements (mock) → Results → Report Sent
   - Диагностика: Input → QR Payment (имитация) → Adapter Prep → Scanning (mock) → Results → Paywall → Details → Report → Report Sent
3. Проверить автосброс (5 минут бездействия → Attract Screen)

**Ожидаемый результат**:
- Все экраны доступны без краша
- Кнопка "Пропустить" видна в правом верхнем углу
- Mock данные генерируются корректно (60 замеров толщиномера, 2-3 DTC коды)
- Таймауты работают (переход на Attract после 5 мин)

### Checklist DEV Smoke

- [ ] Node.js agent lint clean (0 errors)
- [ ] Node.js agent tests green (46/46)
- [ ] Android unit tests green (300+) *(когда AGP 8.4.1)*
- [ ] Packages tests green (device-obd, device-thickness, report)
- [ ] UI navigation complete (все экраны проходимы)
- [ ] Mock устройства работают (толщиномер, OBD)
- [ ] Mock платежи работают (имитация QR)
- [ ] Mock отчёты генерируются (HTML/PDF)
- [ ] Авто-сброс работает (5 мин → Attract)
- [ ] Логи пишутся в `logs/sessions/`, `logs/issues/`

---

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

## Сессия 31 *(текущая — планирование и гейты)*
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

---

## Текущие блокеры и ограничения (обновлено 24.11.2025, Session 2G)

### AGP 8.4.1 недоступен (критический) ❌

**Статус**: Открыт с Session 10C  
**Воздействие**: Блокирует все Android-related тесты

**Проблема**:
- Android Gradle Plugin 8.4.1 не найден ни в Google Maven, ни в зеркалах (maven.aliyun.com)
- Невозможно выполнить `./gradlew` команды
- Все unit/instrumented тесты Android не могут быть запущены

**Попытки решения** (Session 10C):
- ❌ AGP 8.4.1 (исходная версия)
- ❌ AGP 8.3.2
- ❌ AGP 8.2.2
- ❌ AGP 7.4.2

**Workaround**:
- Код пишется и проверяется через code review
- Тесты пишутся, но остаются не выполненными (0/N run)
- Node.js компоненты тестируются полностью
- Документация поддерживается в актуальном состоянии

**Требуется**: Внешнее вмешательство для настройки доступа к Google Maven или локального Maven репозитория

**Связанные логи**: `logs/issues/2025-11-23-agp-blocker.json`

### Maven зеркала неполные (высокий) ⚠️

**Статус**: Открыт с Session 08  
**Воздействие**: Некоторые Android зависимости недоступны

**Проблема**:
- Зеркала (maven.aliyun.com) содержат не все артефакты
- Compose dependencies могут быть недоступны
- androidx.* библиотеки иногда fail при sync

**Workaround**:
- Используем JitPack для сторонних библиотек
- Минимизируем новые зависимости
- Документируем все зависимости в `dependency-inventory.md`

### Отсутствие тестовых устройств (средний) ⚠️

**Статус**: Открыт с начала проекта  
**Воздействие**: Невозможность полевых (field) тестов

**Проблема**:
- Нет физических толщиномеров для BLE интеграции
- Нет OBD-II адаптеров ELM327 для реальных тестов
- Нет Arduino для тестирования замков выдачи

**Workaround**:
- DEV-моки для всех устройств (DEVICE_MOCK_*=true)
- Эмуляторы протоколов (Arduino Serial, BLE GATT, ELM327 AT команды)
- Подготовка к закупке оборудования в Session 60+

**Резолюция**: Planned для Session 60+ (приобретение оборудования)

---

## Связанные документы

- `plan-80-session-roadmap.md` — общий roadmap проекта
- `plan-maintenance-backlog.md` — backlog задач категории G
- `.env.example` — переменные окружения и флаги
- `logs/README.md` — структура логов и checksums
- `logs/issues/2025-11-23-agp-blocker.json` — детали AGP блокера
- `SESSION_11B_SUMMARY.md`, `SESSION_11C_SUMMARY.md`, `SESSION_1G_SUMMARY.md` — сводки последних сессий

---

## История изменений

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 19.11.2025 | Первоначальная версия с smoke-тестами Session 19-22 |
| 1.1 | 23.11.2025 | Добавлены DEV smoke-тесты (Session 1G) |
| 1.2 | 24.11.2025 | Добавлены текущие блокеры и ограничения (Session 2G) |
