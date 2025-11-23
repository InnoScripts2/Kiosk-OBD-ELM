# Session 26 — Vault Export Helper for YooKassa

Дата: 23.11.2025
Диапазон: Фаза E, сессия 26 (PSP безопасность / CI интеграция)

## Цели
- Автоматизировать выгрузку `YOOKASSA_WEBHOOK_SECRET` из HashiCorp Vault в переменные окружения CI перед запуском Gradle.
- Зафиксировать последовательность smoke-команд (Vault export → `checkYooKassaWebhookSecret` → unit-тесты) в планах секретов и тестирования.
- Минимизировать риск случайного логирования секрета в CI, добавив маскирование и единый скрипт.

## Выполненные действия
- Добавлен `android/scripts/export-yookassa-webhook-secret.ps1`: PowerShell 7+ скрипт, который читает `kv/selfservice/payments/<ENV>/psp:webhookSecret` через `vault kv get`, экспортирует значение в `YOOKASSA_WEBHOOK_SECRET` (или любой указанный env var), маскирует вывод для Azure DevOps/GitHub Actions и поддерживает `-DryRun` режим для локального smoke.
- `plan-secrets-config.md` расширен описанием нового скрипта, обязательной командой `pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment <ENV> -EmitPipelineVariables` перед `./gradlew :app:checkYooKassaWebhookSecret`, а также разделом с примером вызова.
- `plan-testing.md` обновлён: в PSP sandbox checklist включена последовательность "Vault export скрипт → Gradle check".
- Донорские каталоги не затрагивались (работа велась внутри `android/`).

## Тесты
- `pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment dev -DryRun` (верификация синтаксиса без обращения к Vault).

## Следующие шаги
- В CI прописать вызов скрипта до любых Gradle команд, настроить `VAULT_ADDR`/`VAULT_TOKEN` и убедиться, что секрет публикуется как скрытая переменная.
- После подключения к Vault обновить `credential-inventory.md`, указав, что Android pipeline использует новый скрипт.
- Начать интеграцию webhook telemetry / Supabase audit smoke для бутика PSP.
