# План модернизации серверного стека киоска

_Обновлено: 28.11.2025_

## 1. Цели и соотнесение с roadmap

| Направление | Цель пользователя | Привязанные сессии | Основные метрики |
|-------------|-------------------|--------------------|------------------|
| C1 — Отчёты | Повышенная приёмка и обработка отчётов, агрегация и SLA по доставке | #43 (Reports), #44 (Payments) | 95% отчётов доставляются < 60 сек, очереди < 10 сообщений на киоск |
| C2 — Обновления и мониторинг | Дистанционное обновление приложения, real-time мониторинг состояний, фильтрация телеметрии | #42 (Kiosk mode), #45 (Telemetry), #51 (Connectivity) | <5 мин между heartbeat, <60 сек на команду OTA/MDM |
| B1 — Supabase расширения | Расширение БД, edge-функции для админ-панели и real-time каналов | #44, #45, #52 | Полное покрытие схемой Typescript типов, 100% edge функций покрыты тестами |
| C3 — SLA отчётов | Таймауты генерации, минимизация задержек, контроль на уровне агента и Supabase | #43 | Менее 5 сек на сериализацию HTML, обнаружение зависаний < 15 сек |
| G1 — Admin console | Подготовка серверных API/каналов для внешней панели управления | #45, #48, #52 | API readiness checklist выполнен, публичный contract в docs |

## 2. Трек C1 — Пайплайн отчётов

1. **Report Intake API** (agent `/reports`):
   - POST `/reports/intake` — принимает HTML/PDF, метаданные, контакты, тип (diagnostics/thickness), SLA таймауты.
   - PUT `/reports/:id/state` — метки повторной генерации, отмена.
   - Источник данных: Android/Windows клиенты + локальный UI.
2. **Locally persisted queue**:
   - Журнал `reports_outbox.sqlite` с состояниями `pending/sent/failed`, дедлайнами.
   - Утилита ретраев с экспоненциальной задержкой, раздельные воркеры для diagnostics/thickness.
3. **Supabase writer**:
   - Новый сервис `SupabaseReportWriter` публикует отчёт → `diagnostics_reports`/`thickness_reports` + `*_deliveries`.
   - Интеграция с `ReportDeliveryWorker` через shared config + heartbeat (Prom metrics → Supabase `report_worker_metrics`).
4. **SLA контроль**:
   - Таймаут генерации (клиент уведомляет агента о старте). Агент запускает watchdog (15 секунд), по истечении пишет в Supabase `report_timeouts`.

## 3. Трек C2 — OTA и мониторинг

1. **Device Heartbeat API** — агент публикует статусы каждые 60 сек в Supabase `device_status` + локальный кеш.
2. **Command Poller** — сервис `DeviceCommandService`:
   - Long-poll `device_commands` с фильтрацией по `environment`/`kiosk_id`.
   - Поддержка команд: `PING`, `REBOOT`, `UPDATE_APP`, `SYNC_CONFIG`.
   - Ответы и события → `device_events` + WebSocket трансляция `/ws/admin`.
3. **Update orchestrator** — интеграция с admin-панелью:
   - Слой абстракции поверх установщика приложений (hook в Windows service/Android Device Owner).
   - Hook в `feature-kiosk-mode` (сессия #42) для Device Owner политик.

## 4. Трек B1 — Supabase расширения

1. **Новые таблицы**:
   - `report_ingest_queue` — черновые записи до публикации.
   - `agent_metrics` — храним метрики (latency, queue-depth, cpu, mem) для админ панели.
   - `admin_sessions` — трекинг операторов.
2. **Edge функции** (plpgsql + http invoke):
   - `fn_enqueue_report_delivery(report_id, channel, recipient)`.
   - `fn_publish_device_command(kiosk_id, command_type, payload)` (с проверкой лимитов).
   - `fn_admin_dashboard_snapshot()` — агрегированные данные (для админ UI).
3. **Реплика типов** — обновить `supabase/migrations` и `agent/src/integrations/supabase/types.ts`.

## 5. Трек C3 — Таймауты отчётов

- Таймеры: генерация (локально ≤ 5 сек), отправка в Supabase ≤ 3 сек, доставка ≤ 60 сек.
- Watchdog: если отчёт не доставлен в SLA → событие `report_timeout` в `device_events`, webhook в admin UI.
- Конфиги: `.env` параметры `REPORT_GENERATION_TIMEOUT_MS`, `REPORT_DELIVERY_SLA_MS`.

## 6. Трек G1 — Admin console readiness

- REST contract:
  - GET `/admin/telemetry` — фильтрованные события (query: severity, kiosk, interval).
  - GET `/admin/device/:id/commands` — история команд.
  - WS `/ws/admin` — multiplex: locks, heartbeats, queue depth, OTA progress.
- Auth placeholder: API key scopes (DEV) с последующим переходом на Supabase Auth.
- Документация: OpenAPI spec + README (решение `docs-unified/admin-console.md`).

## 7. Инкрементальные релизы

| Релиз | Содержание | Тесты |
|-------|------------|-------|
| R1 — Intake MVP | Маршруты `/reports`, локальный outbox, Supabase writer, базовые таймауты | `npm test` (agent), `./gradlew :feature-reports:test`, integration mocks |
| R2 — Monitoring | Heartbeat публишер, `DeviceCommandService`, новые WS каналы | `npm test`, e2e mock server |
| R3 — DB/Edge | SQL миграции, edge функции, types sync, admin snapshot API | `supabase db lint`, Jest tests for edge wrappers |
| R4 — Admin prep | REST admin endpoints, auth stub, dashboards | `npm test`, contract tests |

## 8. Риски и зависимости

- Требуется сервисный ключ Supabase (secure storage + inventory). Ответственный: #50.
- OTA требует финализации Device Owner (сессия #42). Без неё команды `UPDATE_APP` будут заглушками.
- Локальный outbox должен быть устойчив к power loss → SQLite + WAL + fsync (task #49 DevOps для backups).
- Edge функции не покрыты автоматическими тестами → добавить unit tests через `supabase/functions/tests`.

## 9. Следующие шаги (R1)

1. Добавить `.env` параметры Supabase и таймаутов.
2. Реализовать `SupabaseAgentClient` (shared между сервером и воркером).
3. Создать миграцию `20251128_server_upgrade_stage1.sql` (таблицы `report_ingest_queue`, `agent_metrics`).
4. Добавить HTTP маршруты `/reports/intake`, `/reports/:id/state` и сервис `ReportIngestService`.
5. Покрыть unit-тестами: `ReportIngestService.test.ts`, `report-delivery-worker` (новые сценарии таймаутов).

_Ответственные:_
- Архитектура/Docs — Copilot (session #43 owner)
- Backend/Agent — Copilot (sessions #43, #45 coordination)
- Supabase — Copilot (session #44)
- Admin console — Copilot (session #52)
