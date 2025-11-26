# Supabase — настройка проекта киоска

**Цель**: убрать зависимость от домена reg.ru и обслуживать фронтенд/телеметрию через Supabase
`ddaunoxyguqiejrjtwsf.supabase.co` с резервом на офлайн-страницу.

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

## 3. Хранилище фронтенда

1. Создайте бакет `kiosk-ui` (public):
   ```bash
   supabase storage create-bucket kiosk-ui --public
   ```
2. Соберите фронтенд (`npm run build` в `src/` или другом проекте).
3. Загрузите `index.html` и статические ассеты:
   ```bash
   supabase storage upload kiosk-ui/index.html dist/index.html --content-type text/html
   supabase storage upload kiosk-ui/assets/app.js dist/assets/app.js --content-type application/javascript
   supabase storage upload kiosk-ui/assets/app.css dist/assets/app.css --content-type text/css
   ```
4. Получите публичный URL через CLI или Dashboard и убедитесь, что по адресу
   `https://ddaunoxyguqiejrjtwsf.supabase.co/storage/v1/object/public/kiosk-ui/index.html`
   открывается актуальная версия UI.

## 4. Переменные окружения Android/Agent

1. Скопируйте `.env.example` → `.env.local` и заполните:
   ```
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

## 7. Офлайн fallback

- Файл `android/app/src/main/assets/html/offline.html` отображает инструкцию оператору.
- Жест три касания повторно открывает диалог URL, позволяя вернуть Supabase после восстановления сети.
- При необходимости подмените fallback на локальный React/Compose UI, но храните его в assets.

## 8. Checklist перед выкладкой

- [ ] `supabase storage list kiosk-ui` показывает актуальные ассеты
- [ ] `./gradlew clean test assembleDebug` использует `supabase.url`
- [ ] `adb logcat | Select-String "Switching kiosk UI URL"` пуст после успешной загрузки
- [ ] `09-docs/02-application/security/local-credential-notes.local.md` содержит актуальные ключи
- [ ] Vault записи обновлены (`kv/selfservice/platform/supabase/dev/service`)

Следуйте этому документу для любых будущих миграций Supabase/hosting.
