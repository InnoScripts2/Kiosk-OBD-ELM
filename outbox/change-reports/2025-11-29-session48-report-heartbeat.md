# Change Report — 29.11.2025 (Session 48 — Kiosk agent heartbeat metrics)

## Цели

- Зафиксировать патчи состояния отчётов в мониторинге Supabase и runbook.
- Подготовить документацию, чтобы heartbeat метрики можно было отслеживать из agent_metrics.

## Действия

1. `src/services/ReportIngestService.ts`: после любого `updateState` теперь отправляется heartbeat-метрика (`metric_type = agent_heartbeat`, payload component `report_ingest_state`) с ID инжеста, статусом, SLA и счётчиком ретраев.
2. `src/services/ReportIngestService.test.ts`: расширены тесты — проверяем, что heartbeat метрика уходит и что при валидационных ошибках запись не выполняется.
3. `09-docs/02-application/plans/supabase-setup.md`: добавлены SQL-проверка `report_ingest_state` и раздел про переменные `AGENT_HEARTBEAT_INTERVAL_MS` / `AGENT_HEARTBEAT_COMPONENT`.
4. `src/config/agent-config.ts`: новая секция конфигурации heartbeat (интервал + компонент) для дальнейшего переиспользования.
5. `src/services/AgentHeartbeatService.ts` + `.test.ts`: реализован сервис, публикующий метки `agent_heartbeat` с состояниями замков и дополнительным payload, покрыт unit-тестами (fake timers, payload проверки).
6. `src/index.ts`: сервис подключён к основному приложению, heartbeat запускается после инициализации контроллера замков и останавливается при выключении.

## Команды

- `npm --prefix 03-apps/02-application/kiosk-shell/agent run lint`
- `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --runInBand`

## Результаты

- ESLint завершился без ошибок (присутствует предупреждение о поддерживаемой версии TypeScript 5.9.3 > 5.3.x).
- Jest: 11 suites / 61 tests PASS (~30 c), логи Arduino/Lock остаются в mock-режиме.
- Агент теперь публикует два типа heartbeat payload: автоматические обновления статуса отчёта (`report_ingest_state`) и плановые замеры `agent_runtime` с текущим состоянием замков и дополнительными метриками.

## Следующие шаги

- Интегрировать heartbeat payload с будущей админ-панелью и Device Command Service (план серверного апгрейда, трек C2/C3).
- Обновить Supabase dashboards, чтобы визуализировать `agent_heartbeat` → `report_ingest_state` в реальном времени.
