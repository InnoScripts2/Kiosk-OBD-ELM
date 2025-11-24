# План ротации секретов платежного контура

## Область действия
- `PAYMENTS_ENCRYPTION_KEY` — 32-байтовый ключ AES-256-GCM, используемый `PaymentIntentStore` для шифрования PII (meta/contact) в файле `.selfservice/payments/intents.json` и при репликации в edge-cache.
- `YOOKASSA_SHOP_ID` / `YOOKASSA_SECRET_KEY` — реквизиты боевого PSP YooKassa, выдаваемые по договору и прошитые в BuildConfig через Gradle-параметры `payments.yookassa.shopId`/`payments.yookassa.secretKey`.
- `YOOKASSA_WEBHOOK_SECRET` — общий ключ подписи вебхуков PSP, которым проверяется заголовок `X-YooKassa-Signature-Sha256` (HMAC-SHA256) перед обработкой оплаты. Gradle читает значение из параметра `payments.yookassa.webhookSecret` и, если он пуст, автоматически использует переменные окружения `YOOKASSA_WEBHOOK_SECRET` или `PAYMENTS_YOOKASSA_WEBHOOK_SECRET`, которые наполняет CI после чтения из Vault (см. скрипт `android/scripts/export-yookassa-webhook-secret.ps1`).
- `YOOKASSA_RETURN_URL`, `YOOKASSA_API_URL`, `YOOKASSA_PAYMENT_METHOD`, `YOOKASSA_CONFIRMATION_TYPE` — небезопасные параметры конфигурации, задаются через Gradle (`payments.yookassa.*`) и должны храниться отдельно от исходников.
- `SUPABASE_SERVICE_KEY` — service role ключ Supabase, используемый Android-приложением (`BuildConfig.SUPABASE_SERVICE_KEY`) для выгрузки аудит-логов и телеметрии. Значение подхватывается из Gradle-параметра `supabase.serviceKey` либо переменных окружения `SUPABASE_SERVICE_KEY` и должно поступать только из Vault.
- Потребители: Node-агент (`03-apps/02-application/kiosk-shell/agent`), сервисные пакеты `@selfservice/payments`, диагностические smoke-стенды (DEV).
- Хранилище секретов: HashiCorp Vault (`kv/selfservice/payments/<ENV>/encryption-key`). Ключи **не** распространяются через `.env` и локальные файлы, доступ предоставляется только сервисным аккаунтам CI/CD и операторам с ролью `payments-secrets`.

## Требования
1. Ключ `PAYMENTS_ENCRYPTION_KEY` должен храниться и распространяться исключительно через Vault. Допускается временная загрузка в переменную окружения процесса (`PAYMENTS_ENCRYPTION_KEY`) во время деплоя, но запрещено коммитить/кешировать значение в git, CI-логах и системах мониторинга.
2. Пара `YOOKASSA_SHOP_ID`/`YOOKASSA_SECRET_KEY` хранится в Vault по пути `kv/selfservice/payments/<ENV>/psp` c полями `shopId`/`secret`. При сборке Android-приложения значения считываются в Gradle-параметры `payments.yookassa.shopId` и `payments.yookassa.secretKey` и попадают в BuildConfig только в зашифрованной среде CI (артефакт APK подписывается сразу после сборки).
3. Небезопасные параметры (`payments.yookassa.returnUrl`, `payments.yookassa.apiBaseUrl`, `payments.yookassa.paymentMethodType`, `payments.yookassa.confirmationType`, `payments.yookassa.receiptVatCode`, `payments.yookassa.taxSystemCode`) задаются в файле `gradle.properties` CI/CD pipeline и не коммитятся.
4. `YOOKASSA_WEBHOOK_SECRET` хранится в Vault рядом с PSP-реквизитами (`kv/selfservice/payments/<ENV>/psp:webhookSecret`) и передаётся в Android-сборку через Gradle-параметр `payments.yookassa.webhookSecret`. При отсутствии Gradle использует переменные окружения `YOOKASSA_WEBHOOK_SECRET`/`PAYMENTS_YOOKASSA_WEBHOOK_SECRET`, поэтому pipeline обязан вызвать `pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment <ENV> -EmitPipelineVariables` перед запуском `./gradlew`. Без секрета приложение принудительно отклоняет вебхуки (`YooKassaPaymentGateway` возвращает `null`). Секрет нельзя переиспользовать между окружениями.
5. `SUPABASE_SERVICE_KEY` хранится в Vault по пути `kv/selfservice/platform/supabase/<ENV>/service:serviceKey`. Gradle считывает значение из параметра `supabase.serviceKey` или переменных окружения `SUPABASE_SERVICE_KEY`/`SUPABASE_SERVICE_TOKEN`. На QA/PROD сборка обязана проваливаться, если ключ не найден: перед `./gradlew` нужно вызвать `pwsh ./android/scripts/export-supabase-service-key.ps1 -Environment <ENV> -EmitPipelineVariables`, а затем `./gradlew :app:checkSupabaseServiceKey -Pkiosk.environment=<ENV>`. Ключи уникальны для каждого окружения и не должны попадать в git/логи.
2. Формат ключа — base64 или hex, приводимый к 32 байтам. При генерации использовать `openssl rand -hex 32` или эквивалентные средства Vault (`vault write -field=value sys/tools/random/32`).
3. Ротация — не реже одного раза в 90 дней либо немедленно при компрометации/подозрении. Для DEV допускается ручная ротация раз в спринт, но QA/PROD обязательно через утверждённый change request.
4. Файл стора (`PAYMENTS_STORE_PATH`, по умолчанию `.selfservice/payments/intents.json`) обязан располагаться на шифрованном разделе с ACL, закрывающим запись для любых пользователей кроме `kiosk-agent` и операторов `payments-secrets`.
5. Журнал аудита оплат (`.selfservice/payments/audit.ndjson`), формируемый `PaymentModule`, хранится на том же разделе и наследует ACL стора. Журнал содержит события `intent_created/status_checked/intent_manual_confirmed/webhook_handled` и служит источником для последующей выгрузки в Supabase (`payments_audit`); доступ к файлу ограничен группой `payments-audit` и операторам `payments-secrets`.

## Процедура ротации
1. **Подготовка**
   - Создать change request в системе изменений с указанием окружений и плановой даты.
   - Сгенерировать новый ключ (`NEW_KEY`) и записать в Vault по пути `kv/selfservice/payments/<ENV>/encryption-key` в поле `current` сохранив старое значение в `previous`.
   - Обновить `credential-inventory.md`, указав дату генерации и ответственного.
2. **Раскатка**
   - В CI/CD pipeline подтянуть секрет из Vault и выставить `PAYMENTS_ENCRYPTION_KEY=<NEW_KEY>` для соответствующего окружения.
   - Перед запуском Android-сборки выполнить `pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment <ENV> -EmitPipelineVariables`, что скачает `webhookSecret` из Vault, экспортирует `YOOKASSA_WEBHOOK_SECRET`/`PAYMENTS_YOOKASSA_WEBHOOK_SECRET` и зафиксирует секрет как скрытую переменную пайплайна. Сразу после — `./gradlew :app:checkYooKassaWebhookSecret -Pkiosk.environment=<ENV> -Ppayments.gateway=yookassa`, который завершает процесс с ошибкой, если секрет не подхватился.
   - Перезапустить Node-агент (`kiosk-shell/agent`) и зависимые воркеры (`DiagnosticsReportDeliveryRunner`, edge-cache sync) с новым ключом.
   - Выполнить smoke: `npm --prefix 04-packages/02-application/modules/service/payments/core test` (проверка шифрования стора + идемпотенции), `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --test-name-pattern payments` и REST-запрос `GET /payments/metrics` на целевом хосте.
   - Для Android-приложения дополнительно обновить секреты PSP: задать `payments.yookassa.shopId`, `payments.yookassa.secretKey`, `payments.yookassa.returnUrl` (и при необходимости `payments.yookassa.apiBaseUrl`) в защищённом `gradle.properties` CI и зафиксировать версию билда, куда вошла ротация.
3. **Верификация**
   - Проверить, что новые intents создаются/читаются без ошибок (`payment-module.test.ts` в пакете `@selfservice/payments`).
   - Убедиться, что файла `.selfservice/payments/intents.json` перезаписан и метрики `store.byStatus` доступны.
   - Зафиксировать успешную ротацию в runbook, указав timestamp и ссылку на change request.
4. **Rollback (при необходимости)**
   - В Vault вернуть старое значение (`previous` → `current`).
   - Перезапустить сервисы, повторить smoke и остановить расследование причин.
   - Для PSP при необходимости откатить `payments.yookassa.*` значения в gradle-параметрах и пересобрать APK.

## Конфигурация PSP YooKassa

- Все значения читаются через Gradle-параметры (пример для `~/.gradle/gradle.properties` CI):

```
payments.gateway=yookassa
payments.yookassa.shopId=XXXXXX
payments.yookassa.secretKey=live_xxx
payments.yookassa.returnUrl=https://kiosk.example.com/payments/return
payments.yookassa.paymentMethodType=sbp
payments.yookassa.confirmationType=redirect
payments.yookassa.receiptVatCode=2
payments.yookassa.taxSystemCode=6
payments.yookassa.issueReceipt=true
payments.yookassa.requireCustomerForReceipt=false
payments.yookassa.webhookSecret=whsec_xxx
```

- При отсутствии значений Android-приложение автоматически переключается на `DevPaymentGateway`, что недопустимо для QA/PROD. Перед выкладкой выполняем последовательность: `./gradlew :app:checkYooKassaWebhookSecret -Pkiosk.environment=<ENV> -Ppayments.gateway=yookassa` (фиксируем секрет), затем `./gradlew :feature-payments:testDebugUnitTest --tests "*YooKassaPaymentGatewayTest*"` и `./gradlew :app:testDebugUnitTest --tests "*PaymentGatewayResolverTest*"`.

## Конфигурация Supabase

### Миграция сервера (24.11.2025)

**Новый сервер**: `https://ddaunoxyguqiejrjtwsf.supabase.co`

**Дата миграции**: 24 ноября 2025 (UTC)

**Ответственный**: BKG Agent (автоматизированная миграция)

**Vault пути**:
- DEV: `kv/selfservice/platform/supabase/dev/service`
- QA: `kv/selfservice/platform/supabase/qa/service`
- PROD: `kv/selfservice/platform/supabase/prod/service`

**Параметры Gradle** (добавлены в `android/gradle.properties`):
```
supabase.url=https://ddaunoxyguqiejrjtwsf.supabase.co
supabase.serviceKey=<получать из Vault>
```

**Postgres соединение**:
- Host: `db.ddaunoxyguqiejrjtwsf.supabase.co`
- Database: `postgres`
- User: `postgres`
- Pooler: `aws-1-us-east-1.pooler.supabase.com:6543`
- Direct: `aws-1-us-east-1.pooler.supabase.com:5432`

**Безопасность**:
- Service Role Key хранится только в Vault
- Anon Key может быть в публичных конфигах
- JWT Secret только в Vault и secure environment variables
- Postgres Password только в Vault

**Процедура для CI/CD**:
1. Вызвать `pwsh ./android/scripts/export-supabase-service-key.ps1 -Environment <ENV> -EmitPipelineVariables`
2. Запустить `./gradlew :app:checkSupabaseServiceKey -Pkiosk.environment=<ENV>`
3. При успехе продолжить сборку `./gradlew lint test assembleDebug`

**Node-агент**:
- Создан `.env.example` в `03-apps/02-application/kiosk-shell/agent/`
- Переменные: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`
- Postgres URLs: `POSTGRES_URL`, `POSTGRES_PRISMA_URL`, `POSTGRES_URL_NON_POOLING`

### CI-скрипт экспорта секрета

- Скрипт `android/scripts/export-yookassa-webhook-secret.ps1` оборачивает `vault kv get kv/selfservice/payments/<ENV>/psp -field=webhookSecret`, заполняет переменные окружения `YOOKASSA_WEBHOOK_SECRET`/`PAYMENTS_YOOKASSA_WEBHOOK_SECRET`, а при флаге `-EmitPipelineVariables` — публикует секрет для Azure DevOps (`##vso[task.setvariable]`) и GitHub Actions (`GITHUB_ENV`, `::add-mask::`).
- Пример вызова в CI:

```
pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 `
   -Environment qa `
   -VaultPathTemplate "kv/selfservice/payments/{env}/psp" `
   -SecretField webhookSecret `
   -EnvVariable YOOKASSA_WEBHOOK_SECRET `
   -EmitPipelineVariables

./gradlew :app:checkYooKassaWebhookSecret `
   -Pkiosk.environment=qa `
   -Ppayments.gateway=yookassa
```

- Скрипт не пишет секрет в файлы, использует только стандартный ввод/вывод CI и требует заранее настроенных `VAULT_ADDR`/`VAULT_TOKEN`.

### CI-скрипт экспорта Supabase service key

- Скрипт `android/scripts/export-supabase-service-key.ps1` читает `serviceKey` из `kv/selfservice/platform/supabase/<ENV>/service`, экспортирует его в `SUPABASE_SERVICE_KEY` (имя кастомизируется) и при `-EmitPipelineVariables` публикует переменную в Azure DevOps / GitHub Actions, одновременно маскируя значение. Dry-run режим позволяет проверить конфигурацию без обращения в Vault.
- Рекомендуемая последовательность для QA/PROD:

```
pwsh ./android/scripts/export-supabase-service-key.ps1 `
   -Environment qa `
   -EmitPipelineVariables

./gradlew :app:checkSupabaseServiceKey `
   -Pkiosk.environment=qa
```

- Команда `checkSupabaseServiceKey` блокирует `preBuild` и все unit-тесты, если ключ отсутствует, гарантируя, что BuildConfig не содержит пустого `SUPABASE_SERVICE_KEY`. После успешной проверки запускаем `./gradlew :app:testDebugUnitTest --tests "*SupabasePaymentAuditSinkTest*"` и smoke Supabase очередей (`npm --prefix 03-apps/.../kiosk-shell/agent test -- --test-name-pattern supabase`).

## Контроль и мониторинг
- Автоматическое напоминание о ротации — задача в `ops`-backlog каждые 80 дней.
- Alerts: `/monitoring/alerts` проверяет `payments.store.pendingOlderThanMs`; при росте выше 6 часов флаг `manualRotationRequired` добавляется в алерт.
- Все операции ротации должны быть задокументированы в `09-docs/02-application/security/credential-inventory.md` (раздел «Платёжные ключи»).
