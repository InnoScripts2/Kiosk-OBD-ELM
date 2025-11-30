# План 80 сессий — Roadmap (обновлено 27.11.2025)

Этот документ фиксирует фактический статус всех целевых сессий проекта терминального киоска. Он синхронизирован с `AI_AGENT_BRIEFING.md`, `DONOR_STATUS.md`, `agp-mirror-playbook.md` и change-reports в `outbox/change-reports/`.

## 1. Правила ведения

- **Статусы**: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED (причина)`, `COMPLETE (дата/артефакт)`, `SKIPPED (обоснование)`.
- **Обновление**: после каждого шага, влияющего на сессию, обновляем таблицу, указываем ссылку на change-report и дату.
- **Минимум 5 активных сессий**: одновременно поддерживаем ≥5 строк со статусом `IN_PROGRESS`. В каждом ответе фиксируем прогресс по этим сессиям.
- **Артефакты**: ссылка на apk/test отчёты или конкретные файлы (например, `app/build/reports/tests/index.html`).
- **Тесты**: перечисляем обязательные пакеты команд (`./gradlew ...`, `npm run ...`). Если тесты не запускались, явно пишем `—` и причину.
- **APK вес и покрытие**: каждые 10 сессий фиксируем вес `app-debug.apk`; каждые 5 — суммарное покрытие. Последние показатели указываем в разделе 4.

## 2. Активные блоки (сессии 41–52)

| № | Категория | Фокус | Ключевые задачи | Обязательные тесты | Зависимости/заметки | Статус | Артефакты / change-report |
|---|-----------|-------|-----------------|--------------------|---------------------|--------|---------------------------|
| 41 | B (BLE/OBD) | OBD словари | Реструктурировать базы DTC/PID, синхронизировать `base/` и `feature-obd-core` | `./gradlew :feature-obd-core:test`, dictionary parity suite | Использовать каталоги `base/`; обновить локализацию | IN_PROGRESS (27.11.2025 — SAE parity + dtc-index активы) | CR: `outbox/change-reports/2025-11-27-session-bootstrap.md`, `outbox/change-reports/2025-11-27-session41-dtc-plan.md`, `outbox/change-reports/2025-11-27-session41-dtc-export.md`, `outbox/change-reports/2025-11-27-session41-dtc-brands.md`, `outbox/change-reports/2025-11-27-session41-dtc-parity.md`, `outbox/change-reports/2025-11-27-session41-dtc-assets.md`, `outbox/change-reports/2025-11-28-session41-pid-assets.md`; План: `09-docs/02-application/plans/plan-dtc-pid-refactor.md`; Тест: `DtcCatalogParityTest` |
| 42 | G (Device Owner) | Kiosk mode | Перенести политики Device Owner, активировать `feature-kiosk-mode` в `settings.gradle.kts` | Instrumented tests (DevicePolicyManager), `./gradlew :feature-kiosk-mode:connectedCheck` | Требуются ключи админа, железо | IN_PROGRESS (27.11.2025) | — |
| 43 | C (Reports) | Универсальный шаблон отчётов | Объединить Thickness/OBD шаблоны, добавить PDF/HTML генерацию | `./gradlew :feature-reports:test`, snapshot diff | Работать с `docs-unified` и Supabase outbox | IN_PROGRESS (27.11.2025) | — |
| 44 | C (Payments) | Webhook SLA | Реализовать обработку подтверждений, мок вебхуков, Supabase запись | `npm --prefix 03-apps/02-application/kiosk-shell/agent test`, `./gradlew :feature-payments:test` | Зависимость от Supabase, credential inventory | IN_PROGRESS (27.11.2025) | — |
| 45 | B (Telemetry) | Единый формат логов | Выровнять `platform-logging`, агентские события, heartbeat | `./gradlew :platform-logging:test`, telemetry replay | Логи JSON, соблюдать 500 MB ротацию | IN_PROGRESS (27.11.2025) | — |
| 46 | B (Hardware locks) | Прошивка реле | Актуализировать `feature-lock-control`, тестовый стенд | Hardware relay tests, `npm --prefix agent lint` | Требуется lab-setup | NOT_STARTED | — |
| 47 | G (Accessibility) | WCAG отчёт | A11y аудит `app`/`feature-obd-ui`, добавить тесты | Compose accessibility tests, Accessibility Scanner | Совместно с дизайном | NOT_STARTED | — |
| 48 | B (Kiosk agent) | Устройства | Обновить Node агент, покрыть lint/tests, поглотить kiosk-frontend hero/welcome/service | `npm --prefix agent lint`, `npm --prefix agent test`, `./gradlew :app:testDebugUnitTest :platform-ui:test` | Device lab, Compose экраны app | IN_PROGRESS (28.11.2025 — runtime snapshot + Supabase CLI sync + миграции Supabase; 30.11.2025 — DeviceCommandService long-poll + device_events для PING + Jest 13/13 suites PASS; 01.12.2025 — LOCK_CONTROL и REBOOT обработчики + env `DEVICE_COMMAND_REBOOT_GRACE_MS`; 02.12.2025 — перенос attract/welcome/service selection в Compose и вывод Supabase CDN из эксплуатации; 03.12.2025 — Admin Bridge журнал/command bus + потоковые `adminbridge:event` с манифестом команд + отчётные события (generate/send) в Admin Bridge журнале; 04.12.2025 — состояния отчётов в Admin Bridge, карточки «Отчёт толщиномера/диагностики» и команда `report:send`; 05.12.2025 — предпросмотр использует `deliverReport`, Admin Bridge публикует ссылку отчёта и добавлена кнопка копирования ссылки для Windows/Android админ-клиентов; 06.12.2025 — фильтры журнала Admin Bridge + команда `events:setfilters` и дескриптор фильтров в handshake; 07.12.2025 — ThicknessMeasurementScreen получил meta-чипы, панель прогресса, легенду статусов и guidance-панель + unit-тесты для статусов; 07.12.2025 — ThicknessInputScreen обновлён под frontend макет (meta-чипы, FlowRow сетка типов, responsive contact panel, обновлённые androidTest); 07.12.2025 — Добавлен `ThicknessResultsScreen` (meta-row, highlights, таблица замеров, расчёт средних и unit-тесты) + Gradle `:app:testDebugUnitTest :platform-ui:test` PASS; 07.12.2025 — ThicknessInstructionsScreen закрывает шаг после выдачи устройства (meta-ряд, инструкции, do/don't, зоны) + unit-тесты генераторов; 30.11.2025 — `KioskComposeActivity` стала основным LAUNCHER, `PaymentViewModelFactory` прокидывает `PaymentModule`, UI совпадает с фронтендом; 30.11.2025 — `PaymentQRScreen` получил 12-колоночный layout, LazyVerticalGrid мета-карт и адаптивную панель действий + Gradle `:app:testDebugUnitTest :platform-ui:test` PASS; 08.12.2025 — ObdInputScreen и ObdPayment выровнены с фронтендом, вынесены ContactCaptureComponents, PaymentRoute переиспользуется толщиномером и диагностикой, SessionViewModel/тесты синхронизированы; 09.12.2025 — автоматизирован Supabase fallback publish (PowerShell скрипт обновляет Android strings и cache-buster, docs + briefing синхронизированы; 09.12.2025 — ReportSentScreen переведён на KioskTheme-компоненты, добавлен hero-индикатор и panel meta-info + Gradle `:app:testDebugUnitTest :platform-ui:test` PASS)) | CR: `outbox/change-reports/2025-11-29-session48-report-ingest-state.md`, `outbox/change-reports/2025-11-29-session48-report-heartbeat.md`, `outbox/change-reports/2025-11-28-session48-runtime-heartbeat.md`, `outbox/change-reports/2025-11-28-session48-supabase-cli.md`, `outbox/change-reports/2025-11-28-session48-supabase-migrations-fix.md`, `outbox/change-reports/2025-11-30-session48-device-commands.md`, `outbox/change-reports/2025-11-30-session48-payment-ui-refresh.md`, `outbox/change-reports/2025-12-01-session48-lock-control-reboot.md`, `outbox/change-reports/2025-12-03-session48-admin-command-bus.md`, `outbox/change-reports/2025-12-03-session48-admin-event-stream.md`, `outbox/change-reports/2025-12-03-session48-report-admin-events.md`, `outbox/change-reports/2025-12-04-session48-report-resend-controls.md`, `outbox/change-reports/2025-12-05-session48-report-share-links.md`, `outbox/change-reports/2025-12-06-session48-admin-event-filters.md`, `outbox/change-reports/2025-12-07-session48-thickness-measurement-ui.md`, `outbox/change-reports/2025-12-07-session48-thickness-input-ui.md`, `outbox/change-reports/2025-12-07-session48-thickness-results-ui.md`, `outbox/change-reports/2025-12-07-session48-thickness-instructions-ui.md`, `outbox/change-reports/2025-12-08-session48-obd-input-payment.md`, `outbox/change-reports/2025-11-30-session48-supabase-fallback-automation.md`, `outbox/change-reports/2025-12-09-session48-report-sent-refresh.md` |
| 49 | G (DevOps) | Mirror upgrade | Обновление Gradle/Node mirrors, CI артефакты | `./gradlew clean test assembleDebug --offline`, CI scripts | agp-mirror-playbook.md | NOT_STARTED | — |
| 50 | G (Security) | Credential inventory | Сверка ключей, обновление runbooks | Security checklists | 09-docs/02-application/security | NOT_STARTED | — |
| 51 | B (Connectivity) | BLE монитор | Создать мониторинг BLE состояния | `./gradlew :platform-bluetooth:test`, plan-connectivity | Требует integration rig | NOT_STARTED | — |
| 52 | C (Diagnostics bridge) | JS API | Мостик между Android и агентом | `npm` agent tests, diagnostics JS bridge plan | Требует фронта | NOT_STARTED | — |

> **Примечание.** По мере выполнения сессий 46–52 переводятся в `IN_PROGRESS` с подробностями и ссылками на change-reports.

## 3. Процедура обновления записи

1. Зафиксируйте номер и категорию сессии.
2. Внесите изменения (код, документация, тесты) в релевантных модулях.
3. Запустите обязательные тесты и сборки.
4. Создайте change-report с командами, логами и результатами.
5. Обновите строку в таблице: статус, дата, ссылки на артефакты.
6. Отразите сессию в `AI_AGENT_BRIEFING.md` (номер, прогресс, артефакты).

## 4. Метрики APK/покрытия

- **Вес app-debug.apk (контроль каждые 10 сессий)**: последние данные отсутствуют. Следующая точка — по завершении сессии 40 → требуется запустить `./gradlew assembleDebug` в офлайн-режиме и зафиксировать размер.
- **Покрытие тестов (контроль каждые 5 сессий)**: актуальных цифр нет, требуется повторный замер после прогонов `./gradlew test` и `npm run test`.

## 5. История изменений

- **27.11.2025** — восстановлен документ, зафиксированы активные сессии и правила обновления.
