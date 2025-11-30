# Change Report — 28.11.2025 (Session 48 — Supabase CLI sync)

## Цели

- Убедиться, что Supabase CLI установлена и готова к работе для синхронизации миграций.
- Задокументировать требования к переменным окружения и Docker перед запуском `supabase db push` / `supabase start`.

## Действия

1. Обновил Supabase CLI через Scoop до версии 2.62.10 (ранее 2.58.5). Проверка `supabase --version` подтверждает успешную установку.
2. Попытался выполнить `supabase db push`, чтобы прогнать миграции `supabase/migrations/*` на проект `ddaunoxyguqiejrjtwsf`. Команда завершилась ошибкой `failed to initialise login role` из-за отсутствия переменной `SUPABASE_DB_PASSWORD` (паспорт postgres роли хранится в Vault и не задан локально).
3. После запуска Docker Desktop заново выполнил `supabase status` / `supabase start` — локальный стенд успешно поднят (API 54321, Studio 54323, Postgres 54322). Зафиксированы остановленные вспомогательные сервисы (imgproxy/analytics/vector/pooler) — на функционал не влияют.
4. Выполнен health-check `agent_metrics?select=count` через `node -e fetch(...)`: REST отвечает (HTTP 404 по причине отсутствия таблицы — ожидаемо, так как миграции ещё не прогнаны).
5. Обновил документ `09-docs/02-application/plans/supabase-setup.md`: добавлен подпункт с инструкциями по установке CLI, линковке проекта, требованием `SUPABASE_DB_PASSWORD` и предусловиями Docker.

## Команды

- `scoop update supabase`
- `supabase --version`
- `supabase db push`
- `supabase status`

## Результаты

- CLI обновлена и готова (2.62.10).`supabase db push` требует задать `SUPABASE_DB_PASSWORD` (пока отсутствует). Локальный стенд успешно запускается после включения Docker (API 54321 / Studio 54323); REST отвечает, но возвращает 404 для `agent_metrics` из‑за неприменённых миграций.
- Документация теперь явно описывает зависимость от Vault-пароля и Docker, чтобы следующая сессия могла продолжить синхронизацию.

## Следующие шаги

1. Получить `SUPABASE_DB_PASSWORD` из Vault (`kv/selfservice/platform/supabase/dev/service`) и повторить `supabase db push`.
2. Запустить Docker Desktop и выполнить `supabase start`/`supabase status`, чтобы поднять локальный тестовый сервер.
3. После успешного `db push` — прогнать smoke-запросы из раздела мониторинга (`agent_metrics`, `report_ingest_queue`).
