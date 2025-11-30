# Supabase — настройка проекта киоска

**Цель**: хранить резервный фронтенд и телеметрию в Supabase `ddaunoxyguqiejrjtwsf.supabase.co`,
в то время как основной UI теперь обслуживается локальным агентом `/ui`. Supabase остаётся
обязательным для отчётов и fallback-публикации `kiosk-ui` на случай недоступности агента.

## 1. CLI и авторизация

```bash
# Установите или обновите CLI (если требуется)
npm install -g supabase

# Авторизация (токен берётся в https://supabase.com/dashboard/account/tokens)
supabase login
```

## 2. Привязка проекта

```bash
# Перейдите в корень репозитория
cd Kiosk-OBD-ELM

# Линкуем проект по reference ID
supabase link --project-ref ddaunoxyguqiejrjtwsf
```

## 3. Хранилище фронтенда (резерв)

1. Создайте бакет `kiosk-ui` (public):

   ```bash
   supabase storage create-bucket kiosk-ui --public
   ```

2. Соберите фронтенд (`npm run build` в `platform/ui/web/kiosk-frontend`). Эти же артефакты
   автоматически подхватывает локальный агент.
3. Запустите `pwsh scripts/powershell/publish-kiosk-frontend.ps1 -ClearBucket -UpdateAndroidString -AppendCacheBuster`, чтобы собрать (если нужно)
   и рекурсивно скопировать `dist/` в `ss:///kiosk-ui` с помощью `supabase storage cp -r`. Скрипт поддерживает
   параметры `-SkipBuild`, `-BucketName`, `-ParallelJobs`, `-DryRun`, а также `-UpdateAndroidString` (синхронизация `kiosk_url_remote`)
   и `-AppendCacheBuster`/`-CacheBusterToken` (добавление `?v=...`).
4. При необходимости ручного контроля выполните `supabase storage cp -r dist ss:///kiosk-ui`.
5. Если автоматическое обновление строки Android было пропущено, получайте публичный URL через CLI/Dashboard и убедитесь, что по адресу
   `https://ddaunoxyguqiejrjtwsf.supabase.co/storage/v1/object/public/kiosk-ui/index.html` (возможно с `?v=<timestamp>`)
   открывается актуальная версия UI и отражена в `kiosk_url_remote`.

## 4. Переменные окружения Android/Agent

1. Скопируйте `.env.example` → `.env.local` и заполните:

   ```bash
   SUPABASE_URL=https://ddaunoxyguqiejrjtwsf.supabase.co
   SUPABASE_ANON_KEY=<Anon Key>
   SUPABASE_SERVICE_ROLE_KEY=<Service Role Key>
   SUPABASE_JWT_SECRET=<JWT Secret>
   ```

2. Для Android выполните:

   ```powershell
   $env:SUPABASE_SERVICE_KEY="<Service Role Key>"
   ./gradlew :app:checkSupabaseServiceKey -Pkiosk.environment=dev
   ```

3. Для Node-агента:

   ```bash
   npm --prefix 03-apps/02-application/kiosk-shell/agent run lint
   npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern supabase
   ```

4. Heartbeat-публикация агента настраивается через переменные:

   ```bash
   AGENT_HEARTBEAT_INTERVAL_MS=60000 # интервал отправки метрик, мс
   AGENT_HEARTBEAT_COMPONENT=agent_runtime # имя компонента в payload agent_heartbeat
   ```

5. CLI и миграции:

   1. Установите Supabase CLI (Windows: `scoop install supabase`). Проверка версии: `supabase --version` (ожидаем ≥2.62.x).
   2. Для удалённого проекта `ddaunoxyguqiejrjtwsf` выполните `supabase login` и `supabase link --project-ref ddaunoxyguqiejrjtwsf` (требуется Supabase access token).
   3. Перед `supabase db push` обязательно задайте `SUPABASE_DB_PASSWORD` (пароль `postgres` роли из Vault). Без него API возвращает `failed to initialise login role`.
   4. Для локального стенда нужен Docker Desktop (или совместимый движок). Команды `supabase start/status` используют контейнеры `supabase_db_*`; без запущенного Docker они завершаются ошибкой `The system cannot find the file specified`.
   5. После применения миграций проверьте их через `supabase db dump --linked --schema public --search-path public` либо выполнив smoke-запросы из раздела 6.


## 5. Database и Edge Functions

```bash
# Применить миграции (если есть supabase/migrations)
supabase db push

# Деплой edge function kiosk-heartbeat
supabase functions deploy kiosk-heartbeat --project-ref ddaunoxyguqiejrjtwsf
```

> ⚠️ Храните реальные соединения в `09-docs/02-application/security/local-credential-notes.local.md` и/или Vault.

## 6. Мониторинг

- Включите в `plan-testing.md` smoke-тест WebView (загрузка Supabase UI + падение в офлайн).
- Используйте расширение **LogcatWin, ADB interface for VSCode** для live-логов `MainActivity`.
- Для телеметрии Supabase создайте dashboard по таблицам `diagnostics_reports`, `payments_audit`, `mdm_status`.
- Единый heartbeat киосков фиксируется в `agent_metrics` (`metric_type = 'agent_heartbeat'`). Патчи состояния отчётов из `PUT /reports/:ingestId/state` публикуются как payload `component = 'report_ingest_state'` с полями `ingestId`, `status`, `retries`, `slaDeadlineMs`, `lastError`. Проверка:

   ```sql
   select recorded_at, payload->>'ingestId' as ingest_id, payload->>'status' as status
   from agent_metrics
   where metric_type = 'agent_heartbeat' and payload->>'component' = 'report_ingest_state'
   order by recorded_at desc limit 50;
   ```

- Плановые heartbeat-записи с `component = 'agent_runtime'` несут снимок окружения агента: `payload->'runtime'` содержит `nodeVersion`, `platform`, `uptimeMs` и блок `memory` (rss/heap/external/arrayBuffers в байтах). Эти поля позволяют отслеживать деградации (рост heap, перезапуски). Быстрая проверка:

   ```sql
   select recorded_at,
          (payload->'runtime'->>'nodeVersion') as node_version,
          (payload->'runtime'->>'uptimeMs')::bigint as uptime_ms,
          (payload->'runtime'->'memory'->>'heapUsedBytes')::bigint as heap_used_bytes
   from agent_metrics
   where metric_type = 'agent_heartbeat' and payload->>'component' = 'agent_runtime'
   order by recorded_at desc limit 50;
   ```

## 7. Офлайн fallback

- Файл `android/app/src/main/assets/html/offline.html` отображает инструкцию оператору.
- Жест три касания повторно открывает диалог URL, позволяя переключаться между `/ui`, Supabase и офлайн.
- Основной URL теперь `http://127.0.0.1:7070/ui`. Supabase конфигурация указывается как `kiosk_url_remote`.
- При необходимости подмените fallback на локальный React/Compose UI, но храните его в assets.

## 8. Checklist перед выкладкой

- [ ] `supabase storage list kiosk-ui` показывает актуальные ассеты
- [ ] `./gradlew clean test assembleDebug` использует `supabase.url`
- [ ] `adb logcat | Select-String "Switching kiosk UI URL"` пуст после успешной загрузки
- [ ] `09-docs/02-application/security/local-credential-notes.local.md` содержит актуальные ключи
- [ ] Vault записи обновлены (`kv/selfservice/platform/supabase/dev/service`)

Следуйте этому документу для любых будущих миграций Supabase/hosting.
