# Session 28 — Supabase Service Key Enforcement

Дата: 23.11.2025
Диапазон: Фаза E, сессия 28 (секреты / Supabase telemetry)

## Цели
- Автоматизировать доставку Supabase service key в Android/Node пайплайны через Vault.
- Зафиксировать соответствующие требования в runbooks и тест-планах.
- Провести fail-fast smoke, который гарантирует, что QA/PROD сборки не стартуют без ключа.

## Выполненные действия
- Добавлен PowerShell-скрипт `android/scripts/export-supabase-service-key.ps1`, который вытягивает `serviceKey` из `kv/selfservice/platform/supabase/<ENV>/service`, экспортирует `SUPABASE_SERVICE_KEY` и при необходимости публикует переменную в Azure DevOps/GitHub Actions.
- В `app/build.gradle.kts` уже создана задача `checkSupabaseServiceKey`; теперь документировано, что она вешается на `preBuild` и все unit-тесты, требуя ключ для всех окружений, кроме `dev`.
- Обновлены документы: `plan-secrets-config.md` (область действия, требования и отдельный раздел про новый скрипт), `plan-testing.md` (контрольный лист PSP дополнился Supabase последовательностью) и `09-docs/02-application/security/credential-inventory.md` (строка Supabase описывает скрипт + smoke-команды).

## Тесты
- `pwsh ./android/scripts/export-supabase-service-key.ps1 -Environment qa -DryRun`
  - ✔️ Dry-run подтвердил путь `kv/selfservice/platform/supabase/qa/service` и корректную экспозицию переменной.
- `./gradlew :app:checkSupabaseServiceKey "-Pkiosk.environment=qa" "-Psupabase.serviceKey=dummy"`
  - ✔️ Задача прошла успешно и зафиксировала наличие ключа. (Первая попытка без кавычек провалилась, т.к. PowerShell разрезал `-Pkiosk.environment=qa`.)

## Следующие шаги
- Привязать оба скрипта (`export-yookassa-webhook-secret.ps1`, `export-supabase-service-key.ps1`) к фактическим CI pipelines (YAML) и обновить ссылки в документации.
- Запустить полноценный Supabase smoke (`npm --prefix 03-apps/.../kiosk-shell/agent test -- --test-name-pattern supabase`) после того, как ключ будет подставляться из Vault на QA стенде.
- Продолжить ROADMAP секцию 29 — мониторинг ротации Supabase ключей и обновление Supabase storage токенов.
