# План наполнения миграций Supabase

Цель: разбить общую схему на тематические миграции, чтобы `supabase db push`
оставался детерминированным и каждая область (пользователи, контент, сторидж,
RLS, функции, индексы) могла эволюционировать независимо.

## Разбиение

1. **20251125043501_complete_database_schema.sql**
   - Содержит только создание расширений (`uuid-ossp`, `pgcrypto`, `pg_trgm`).
   - Выполняет базовый каркас таблиц `profiles`, `posts`, `comments`, `likes`,
     `follows` без индексов/политик.
   - Все CHECK/DEFAULT остаются здесь, чтобы повторная инициализация `db reset`
     получала целостные таблицы.

2. **20251125043504_create_users_and_profiles.sql**
   - Переносит бизнес-логику вокруг `profiles`: комментарии, дополнительные
     поля, каскадные связи c `auth.users`.
   - Добавляет первичный индекс по `username`, проверки длины био/ника.
   - Планируется вынести сюда `handle_new_user` + триггер `on_auth_user_created`.

3. **20251125043508_create_posts_and_comments.sql**
   - Таблицы `posts`, `comments`, `likes`, `follows` (если потребуется
     дополнительная структура), а также их внешние ключи.
   - Здесь же — бизнес CHECK-и (например, запрет `follower_id = following_id`).
   - Сюда переезжают комментарии к таблицам.

4. **20251125043514_create_storage_buckets.sql**
   - Создание bucket-ов `avatars`, `posts`, `private` через вставку в
     `storage.buckets`.
   - Политики доступа к storage (`storage.objects`).

5. **20251125043539_setup_rls_policies.sql**
   - Все `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` и политики для `profiles`,
     `posts`, `comments`, `likes`, `follows`.
   - Каждая политика именована и документирована (соответствие инструкциям).

6. **20251125043542_create_functions_and_triggers.sql**
   - Функции `update_updated_at_column`, `handle_new_user`, `get_post_stats`,
     `get_user_stats`.
   - Соответствующие триггеры (`update_*_updated_at`, `on_auth_user_created`).

7. **20251125043546_create_indexes_and_constraints.sql**
   - Все вторичные индексы (`idx_posts_user_id`, `idx_comments_post_id`, ...),
     включая `gin` для `profiles.username`.
   - Дополнительные `UNIQUE` / `CHECK`, которые не были заданы в основной
     таблице, переносятся сюда для управляемости.

## Порядок действий

1. Отредактировать `complete_database_schema.sql`, оставив только расширения и
   ddl таблиц (без RLS/функций/индексов) — всё остальное переедет в тематические
   файлы.
2. Заполнить каждый модульный файл соответственными блоками (с `IF NOT EXISTS`
   для повторного запуска). Выполнять в порядке имени файла — Supabase CLI
   гарантирует сортировку.
3. После разделения — прогон `supabase db reset` локально (когда Docker будет
   доступен) и `supabase db diff` → `supabase db push`.
4. Сгенерировать типы TS: `supabase gen types typescript --linked >
   src/types/supabase.ts` (в Vite-проекте) и синхронизировать с агентом.

> Пока Docker Desktop отвечает ошибкой 500, разделение выполняем без `db push`;
> проверку повторим сразу после восстановления Docker API.

## Статус на 25.11.2025

- `20251125043501_complete_database_schema.sql` оставлен только с расширениями и
   DDL таблиц — индексы, функции, триггеры и RLS вынесены в тематические файлы.
- Заполнены отдельные миграции: `create_users_and_profiles.sql`
   (комментарии, `handle_new_user` + триггер), `create_posts_and_comments.sql`
   (комментарии для контентных сущностей), `create_storage_buckets.sql`
   (buckets + политики `storage.objects`), `setup_rls_policies.sql`,
   `create_functions_and_triggers.sql`, `create_indexes_and_constraints.sql`.
- `supabase status`/`supabase start` по‑прежнему завершаются ошибкой named pipe
   (`open //./pipe/dockerDesktopLinuxEngine`); утренние и дневные ретраи 25.11
   (по запросу владельца) не изменили картину: CLI не видит контейнеры и не
   может создать shadow DB, поэтому `db reset`/`db diff`/`db push` остаются в
   очереди до восстановления доступа к Docker Desktop.
- Docker Desktop теперь запущен (контекст `desktop-linux` отвечает, `docker
   version` показывает сервер 29.0.1), однако `supabase start` падает уже на
   этапе инициализации Realtime/Storage с ошибкой `StorageBackendError:
   Migration iceberg-catalog-ids not found`; CLI автоматически останавливает и
   удаляет контейнеры (`supabase_db_My_project`, `supabase_config_*`) после
   этой ошибки, поэтому `supabase status` видит их как отсутствующие. Требуется
   разобраться с миграцией storage либо обновить локальный образ.
- Следующий шаг: после запуска Docker выполнить `supabase db reset`, затем
   `supabase db diff` → `supabase db push` и генерацию типов TypeScript.
