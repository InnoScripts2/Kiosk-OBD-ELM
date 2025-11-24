# План тестирования — контрольные листы

**Дата**: Обновлено 24.11.2025 (Session 14Z)  
**Версия**: 1.4

## Оглавление
1. [Smoke-тесты DEV-окружения](#smoke-тесты-dev-окружения)
2. [Текущие блокеры](#текущие-блокеры)
3. [Сессия 19 — генерация отчётов диагностики](#сессия-19--генерация-отчётов-диагностики)
4. [Сессия 21 — Edge cache и оффлайн синхронизация](#сессия-21--edge-cache-и-оффлайн-синхронизация)
5. [Сессия 22 — платёжный шлюз и аудит](#сессия-22--платёжный-шлюз-и-аудит)
6. [Сессии 31–33 — подготовка к полевым тестам](#сессии-3133--подготовка-к-полевым-тестам-uiobdelm)

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

## Текущие блокеры и ограничения (обновлено 24.11.2025, Session 14Z)

### AGP 8.4.1 недоступен (критический) ❌

**Статус**: Открыт с Session 10C, проанализирован в Session 14Z  
**Воздействие**: Блокирует все Android-related тесты и сборки

**Проблема**:
- Android Gradle Plugin 8.4.1 не найден из-за сетевой блокировки
- Доступ к `dl.google.com` (Google Maven) заблокирован
- Доступ к `maven.aliyun.com` (китайские зеркала) заблокирован
- AGP не публикуется в Maven Central (только версии ≤2.3.0)
- AGP не публикуется в Gradle Plugin Portal
- Невозможно выполнить `./gradlew` команды
- Все unit/instrumented тесты Android не могут быть запущены

**Сетевая диагностика** (Session 14Z):
| Репозиторий | Статус | Примечание |
|------------|--------|------------|
| Google Maven | ❌ БЛОКИРОВАН | Could not resolve host: dl.google.com |
| Aliyun mirrors | ❌ БЛОКИРОВАН | Could not resolve host: maven.aliyun.com |
| Maven Central | ✅ ДОСТУПЕН | AGP только ≤2.3.0 (несовместимо) |
| Gradle Plugin Portal | ✅ ДОСТУПЕН | AGP не публикуется там |

**Попытки решения** (Session 10C):
- ❌ AGP 8.4.1 (исходная версия)
- ❌ AGP 8.3.2
- ❌ AGP 8.2.2
- ❌ AGP 7.4.2
- ❌ Downgrade до AGP ≤2.3.0 (несовместимо с Kotlin 1.9.24, compileSdk 35, Gradle 8.7)

**Стратегии разблокировки** (Session 14Z):

Детальный анализ в `docs/infra/agp-unblock-plan.md`.

1. **GitHub-hosted runner** (приоритет 1, рекомендуется):
   - Использовать `ubuntu-latest` вместо self-hosted runner
   - ИЛИ запросить whitelist для `dl.google.com`
   - Время: 1-2 часа
   - Требования: Изменение CI/CD политик

2. **Корпоративный прокси** (приоритет 2):
   - Настроить прокси с доступом к Google Maven
   - Добавить секреты: `PROXY_HOST`, `PROXY_PORT`, `PROXY_USER`, `PROXY_PASSWORD`
   - Время: 2-4 часа
   - Требования: Прокси-сервер, учётные данные

3. **Локальное зеркало Maven (Nexus)** (приоритет 3):
   - Развернуть Nexus Repository Manager
   - Загрузить AGP артефакты вручную
   - Время: 4-8 часов
   - Требования: Сервер (4GB RAM, 50GB storage), Docker

**Workaround** (временные меры):
- Код пишется и проверяется через code review
- Тесты пишутся, но остаются не выполненными (0/N run)
- Node.js компоненты тестируются полностью (46/46 tests green)
- TypeScript разработка продолжается без ограничений
- Статический анализ Kotlin кода (без компиляции)
- Документация поддерживается в актуальном состоянии

**Требуется**: **РЕШЕНИЕ ВЛАДЕЛЬЦА ПРОЕКТА** по выбору стратегии (A, B или D) и предоставлению ресурсов

**Связанные документы**:
- `docs/infra/agp-unblock-plan.md` - Комплексный план разблокировки (4 стратегии)
- `docs/infra/agp-access-handbook.md` - Руководство по обслуживанию AGP
- `android/session-logs/session-14z.md` - Детальные логи диагностики
- `logs/issues/agp-blocker-14z.json` - Issue log в JSON формате
- `SESSION_10C_AGP_BLOCKER_ANALYSIS.md` - Первое обнаружение
- `SESSION_14Z_SUMMARY.md` - Комплексный анализ

**Команды проверки** (после снятия блокера):
```bash
# Проверка доступности репозиториев
./infra/scripts/check-maven-access.sh

# Сборка Android
cd android
./gradlew clean --refresh-dependencies
./gradlew assembleDebug

# Запуск тестов
./gradlew testDebugUnitTest

# Линтинг
./gradlew lint detekt
```

**Ожидаемые результаты** (после снятия блокера):
- ✅ `./gradlew assembleDebug` завершается успешно
- ✅ APK размером ≥60 MB генерируется
- ✅ 300+ unit-тестов проходят
- ✅ Lint без критичных ошибок
- ✅ Build time: 3-5 минут (чистая сборка)

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
- `SESSION_11B_SUMMARY.md`, `SESSION_11C_SUMMARY.md` — сводки Reports/BLE fixes
- `SESSION_12B_SUMMARY.md`, `SESSION_12C_SUMMARY.md` — сводки Thickness/Payment fixes
- `SESSION_1G_SUMMARY.md`, `SESSION_2G_SUMMARY.md` — сводки G-категории
- `logs/sessions/README.md` — каталог логов с примерами

---

## История изменений

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 19.11.2025 | Первоначальная версия с smoke-тестами Session 19-22 |
| 1.1 | 23.11.2025 | Добавлены DEV smoke-тесты (Session 1G) |
| 1.2 | 24.11.2025 | Добавлены текущие блокеры и ограничения (Session 2G) |
| 1.3 | 24.11.2025 | Актуализация Current blockers, добавлены ссылки на Session 12B/12C (Session 3G) |
| 1.4 | 24.11.2025 | Расширение раздела AGP blocker с сетевой диагностикой, 3 стратегиями разблокировки, детальной документацией (Session 14Z) |
