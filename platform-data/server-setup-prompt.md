# Prompt: Полная настройка сервера Supabase/DB/PSP для киоска

Этот промпт предназначен для автоматизации перехода на новый сервер Supabase и
согласования всех зависимых компонентов (Android-приложение, Node-агент,
инфраструктурные планы). Он описывает обязательные шаги для агента или оператора,
чтобы обновление прошло консистентно и без утечек секретов.

## 1. Цель
1. Обновить значения Supabase/DB переменных окружения во всех целевых `.env`
   файлах (локальные DEV-шаблоны, CI-пайплайны, Node-агент).
2. Синхронизировать Gradle-параметры и проверки `:app:checkSupabaseServiceKey`
   / `:app:checkYooKassaWebhookSecret`, чтобы сборка падала при отсутствии
   новых ключей.
3. Обновить документацию и реестр секретов (`plan-secrets-config.md`,
   `09-docs/02-application/security/credential-inventory.md`, `plan-testing.md`).
4. Провести smoke-тесты Supabase edge-sync + платежные проверки и зафиксировать
   результаты в `android/session-logs/`.

## 2. Входные данные
- Новый Supabase URL и ключи (anon/service role/JWT secret, Postgres DSN).
- PSP/YooKassa параметры (shopId, secret, webhookSecret) — подтягиваются из Vault.
- Целевые окружения: `DEV`, `QA`, `PROD` (указывать явно при запуске промпта).
- Пути к Vault (`kv/selfservice/platform/supabase/{env}` и
  `kv/selfservice/payments/{env}/psp`).

## 3. Шаги промпта
1. **Валидация входных данных**
   - Проверить, что Supabase URL принадлежит домену `supabase.co`.
   - Убедиться, что ключи переданы вне репозитория (prompt-параметры, Vault). Ничего
     не записывать в git кроме плейсхолдеров.
2. **Обновление `.env` шаблонов**
   - Отредактировать `./.env` и `03-apps/02-application/kiosk-shell/agent/.env.example`
     так, чтобы все переменные ссылались на новые значения. В commit добавлять
     только комментарии и заглушки вида `<SUPABASE_SERVICE_ROLE_KEY>`.
   - Если `.env` содержит реальные значения (как в DEV), пометить их как TODO для
     удаления перед пушем.
3. **Gradle + скрипты**
   - В `android/gradle.properties` (или CI переменных) обновить параметры:
     `supabase.url`, `supabase.serviceKey`, `payments.yookassa.*` (при необходимости).
   - Проверить, что таски `:app:checkSupabaseServiceKey` и
     `:app:checkYooKassaWebhookSecret` вызываются в CI. При отсутствии — добавить
     шаг в `.github/workflows/android-ci-bootstrap.yml`.
4. **Документация и планы**
   - `android/plan-multi-language-consolidation.md`: добавить заметку в раздел
     «Следующие шаги», что Supabase/DB артефакты привязаны к новому серверу и
     нужно вынести TS-агента в `android/platform-ui/web/`.
   - `plan-secrets-config.md`: указать дату миграции Supabase ключей,
     ответственных и ссылку на Vault-путь.
   - `09-docs/02-application/security/credential-inventory.md`: обновить секцию
     Supabase/DB (URL, типы ключей, сроки ротации).
5. **Edge-cache / Node-агент**
   - В `03-apps/02-application/kiosk-shell/agent/src/integrations/supabase/`
     проверить, что `.env` переменные читаются из `process.env`. При необходимости
     обновить README агента.
   - Запустить `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern supabase`.
6. **Android smoke**
   - Выполнить `./gradlew :app:testDebugUnitTest --tests "*Supabase*"` и
     `./gradlew :feature-reports:test`.
   - Если Check таски падают из‑за отсутствия ключей — промпт должен вывести
     инструкцию вызвать `pwsh ./android/scripts/export-supabase-service-key.ps1`.
7. **Логи и отчётность**
   - Создать файл `android/session-logs/YYYYMMDD-server-migration.md` с
     результатами smoke и ссылкой на CI.
   - Обновить `plan-80-session-roadmap.md` в соответствующей сессии (категория G2).

## 4. Выходные данные
- Обновлённые `.env.example` / README с placeholders (без секретов).
- Документационные записи в перечисленных файлах.
- Логи smoke-тестов и подтверждение успешного `./gradlew lint test assembleDebug`.
- TODO/issue, если какой-либо шаг заблокирован (например, нет доступа к Vault).

## 5. Проверки перед завершением
- `git status` чист от секретов (нет изменений в `.env` с реальными значениями).
- `npm --prefix 03-apps/... agent test` зелёный.
- `./gradlew lint test assembleDebug` завершён успешно.
- Все изменения задокументированы в session log и credential inventory.

Используйте этот промпт при каждом переносе Supabase/DB/PSP окружений, чтобы
соблюдать правила из `.github/instructions/instructions.instructions.md` и
исключить появление секретов в git-history.


