# Session 27 — Credential Inventory Rollup

Дата: 23.11.2025
Диапазон: Фаза E, сессия 27 (секреты / наблюдаемость за пайплайном)

## Цели
- Зафиксировать единый реестр секретов согласно требованию плана ("credential-inventory.md").
- Привязать новый Vault-скрипт `export-yookassa-webhook-secret.ps1` к документации, чтобы CI-пайплайны имели формальный источник.
- Очертить ответственных и smoke-команды для Supabase, PSP и MDM доменов.

## Выполненные действия
- Создан каталог `09-docs/02-application/security/` и документ `credential-inventory.md` с таблицами по Payments, Supabase и MDM: указаны Vault пути, потребители, механизмы инъекции (`BuildConfig`, PowerShell-скрипт, CI env vars) и ротация/смоке.
- Раздел "CI/CD переменные" описывает обязательную последовательность `export-yookassa-webhook-secret.ps1` → `:app:checkYooKassaWebhookSecret` перед Gradle задачами.
- Добавлен блок "Ответственные" с группами `payments-secrets`, `telemetry-core`, `device-ops`.
- Донорские каталоги не затрагивались; металоги и APK метрики без изменений.

## Тесты
- Документальная правка (smoke не требовался).

## Следующие шаги
- После интеграции скрипта в CI обновить фактические пайплайны и приложить ссылки на Job конфигурации.
- Расширить раздел Supabase по мере появления дополнительных ключей (storage signed URLs, Edge Functions JWT).
- Продолжить PSP telemetry/e2e-smoke (запланировано на сессии 28–29).
