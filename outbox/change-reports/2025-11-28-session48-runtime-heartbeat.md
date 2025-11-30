# Change Report — 28.11.2025 (Session 48 — Kiosk agent runtime heartbeat)

## Цели

- Добавить в heartbeat агента объективные показатели состояния процесса (uptime, память, версия Node) для дальнейшего мониторинга.
- Провести рефреш документации Supabase, чтобы операторы знали, как читать новые поля `agent_runtime`.

## Действия

1. `src/services/AgentHeartbeatService.ts`: payload heartbeat дополнен снимком `runtime` (pid, nodeVersion, platform, uptimeMs и блок памяти). Добавлена утилита `collectRuntimeSnapshot`, чтобы метрики были единообразными.
2. `src/services/AgentHeartbeatService.test.ts`: тесты проверяют наличие `runtime`, сопоставляют версию Node/платформу и валидируют числовые поля памяти.
3. `09-docs/02-application/plans/supabase-setup.md`: раздел мониторинга расширен SQL-примером для чтения `payload->'runtime'` и объяснением целей метрики.

## Команды

- `npm --prefix 03-apps/02-application/kiosk-shell/agent run lint`
- `npm --prefix 03-apps/02-application/kiosk-shell/agent test`

## Результаты

- ESLint — без ошибок (предупреждение о версии TypeScript 5.9.3 сохранено).
- Jest — 12/12 suites, 64/64 tests PASS (~24 c); логи Arduino/Lock остаются частью моков.
- Heartbeat метрики теперь несут сведения об uptime и использовании памяти, что позволит на Supabase dashboards отслеживать деградации агента.

## Следующие шаги

- Добавить виджеты в Supabase dashboard для `agent_runtime` (heap, uptime) и связать их с оповещениями DevOps.
- Пробросить эти же метрики в будущий Device Command Service, чтобы можно было запрашивать состояние киоска on-demand.
