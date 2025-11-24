# Реестр учётных данных и секретов

Документ фиксирует все секреты, ключи и учётные данные проекта для киоска самообслуживания.
Обновляется при каждом изменении секретов, ротации ключей или миграции серверов.

**Дата создания**: 24.11.2025 (UTC)  
**Версия**: 1.0  
**Ответственный**: DevOps/Security Team

## 1. Общие правила

- Все секреты хранятся в **HashiCorp Vault**
- Путь Vault: `kv/selfservice/<category>/<environment>/<resource>`
- Окружения: `dev`, `qa`, `prod`
- Реальные значения **НЕ** коммитятся в git
- Ротация критичных ключей: **каждые 90 дней** минимум
- Все операции с секретами логируются

## 2. Supabase / Backend

### 2.1 Supabase Credentials

**Миграция**: 24.11.2025 - новый сервер `ddaunoxyguqiejrjtwsf.supabase.co`

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `SUPABASE_URL` | URL проекта Supabase | Публичный, в коде | Не требуется | Низкая |
| `SUPABASE_ANON_KEY` | Публичный ключ (anon role) | Публичный, в коде | При компрометации | Средняя |
| `SUPABASE_SERVICE_ROLE_KEY` | Сервисный ключ (полный доступ) | `kv/.../supabase/{env}/service:serviceKey` | 90 дней | **Критичная** |
| `SUPABASE_JWT_SECRET` | Секрет для JWT токенов | `kv/.../supabase/{env}/service:jwtSecret` | 90 дней | **Критичная** |

**Текущие значения** (DEV окружение):
- URL: `https://ddaunoxyguqiejrjtwsf.supabase.co`
- Project Ref: `ddaunoxyguqiejrjtwsf`
- Region: AWS US East 1

**Использование**:
- Android: через `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_SERVICE_KEY`
- Node-агент: через переменные окружения `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`
- CI/CD: скрипт `android/scripts/export-supabase-service-key.ps1`

**Процедура ротации**:
1. Сгенерировать новый JWT Secret в Supabase Dashboard
2. Обновить значение в Vault для всех окружений
3. Выполнить `pwsh export-supabase-service-key.ps1 -Environment <ENV> -EmitPipelineVariables`
4. Пересобрать и развернуть приложения
5. Обновить дату ротации в этом документе

**Последняя ротация**: 24.11.2025 (миграция сервера)

### 2.2 PostgreSQL Credentials

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `POSTGRES_PASSWORD` | Пароль пользователя postgres | `kv/.../supabase/{env}/postgres:password` | 90 дней | **Критичная** |
| `POSTGRES_HOST` | Хост БД | Публичный | Не требуется | Низкая |
| `POSTGRES_URL` | Connection string (pooler) | Содержит пароль в Vault | При ротации пароля | **Критичная** |
| `POSTGRES_URL_NON_POOLING` | Direct connection | Содержит пароль в Vault | При ротации пароля | **Критичная** |

**Текущие значения** (DEV):
- Host: `db.ddaunoxyguqiejrjtwsf.supabase.co`
- Port (pooler): 6543
- Port (direct): 5432
- Database: `postgres`
- User: `postgres`

**Использование**:
- Node-агент: миграции БД, прямые запросы
- Edge-cache: репликация данных
- Аналитика: чтение через Prisma

## 3. Платежи (Payment Service Provider)

### 3.1 YooKassa

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `YOOKASSA_SHOP_ID` | ID магазина в YooKassa | `kv/.../payments/{env}/psp:shopId` | При смене договора | Средняя |
| `YOOKASSA_SECRET_KEY` | Secret key для API | `kv/.../payments/{env}/psp:secret` | 90 дней | **Критичная** |
| `YOOKASSA_WEBHOOK_SECRET` | Секрет для валидации webhook | `kv/.../payments/{env}/psp:webhookSecret` | 90 дней | **Критичная** |

**Использование**:
- Android: через `BuildConfig.YOOKASSA_*`
- Gradle: скрипт `android/scripts/export-yookassa-webhook-secret.ps1`
- Проверка: таска `:app:checkYooKassaWebhookSecret`

**Процедура ротации**:
1. Создать change request
2. Сгенерировать новый webhook secret в YooKassa Dashboard
3. Обновить Vault для целевого окружения
4. Запустить `pwsh export-yookassa-webhook-secret.ps1 -Environment <ENV>`
5. Пересобрать приложение с новым секретом
6. Smoke-тест: `./gradlew :app:checkYooKassaWebhookSecret`

**Последняя ротация**: Не выполнялась (новая инсталляция)

### 3.2 Payment Encryption Key

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `PAYMENTS_ENCRYPTION_KEY` | AES-256 ключ для шифрования PII | `kv/.../payments/{env}/encryption-key` | 90 дней | **Критичная** |

**Формат**: 32 байта hex (64 символа)

**Использование**:
- Node-агент: `PaymentIntentStore` для шифрования `.selfservice/payments/intents.json`
- Android: Edge-cache репликация

**Генерация**: `openssl rand -hex 32`

**Последняя ротация**: Не выполнялась (новая инсталляция)

## 4. Коммуникации

### 4.1 Email (SendGrid)

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `SENDGRID_API_KEY` | API ключ SendGrid | `kv/.../communications/{env}/email:apiKey` | При компрометации | Высокая |

**Использование**: Node-агент `ReportService` для отправки отчётов

### 4.2 SMS (Twilio)

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `TWILIO_ACCOUNT_SID` | Account SID | `kv/.../communications/{env}/sms:accountSid` | Не требуется | Низкая |
| `TWILIO_AUTH_TOKEN` | Auth Token | `kv/.../communications/{env}/sms:authToken` | 180 дней | Высокая |

**Использование**: Node-агент для SMS-отчётов и оповещений

### 4.3 WhatsApp (Green API)

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `GREEN_API_INSTANCE_ID` | Instance ID | `kv/.../communications/{env}/whatsapp:instanceId` | Не требуется | Низкая |
| `GREEN_API_TOKEN` | API Token | `kv/.../communications/{env}/whatsapp:token` | 90 дней | Высокая |

**Использование**: Будущая интеграция для WhatsApp уведомлений

## 5. Мониторинг

### 5.1 Grafana

| Параметр | Назначение | Vault Path | Ротация | Критичность |
|----------|-----------|------------|---------|-------------|
| `GRAFANA_API_KEY` | API ключ для метрик | `kv/.../monitoring/{env}/grafana:apiKey` | 180 дней | Средняя |

**Использование**: Будущая интеграция (placeholder)

## 6. Устройства

### 6.1 Arduino (Lock Control)

**Параметры**: `ARDUINO_PORT`, `ARDUINO_BAUD`

**Безопасность**: Физический доступ к устройству контролируется; секретов не требуется.

## 7. Процедуры безопасности

### 7.1 Немедленная ротация при компрометации

Если секрет скомпрометирован:
1. **Немедленно** сгенерировать новое значение
2. Обновить Vault для всех затронутых окружений
3. Развернуть обновление на всех киосках
4. Провести аудит использования старого секрета
5. Задокументировать инцидент

### 7.2 Регулярная ротация (каждые 90 дней)

Автоматическое напоминание в ops-backlog за 10 дней до истечения срока.

**Критичные секреты** (90 дней):
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_JWT_SECRET`
- `POSTGRES_PASSWORD`
- `YOOKASSA_SECRET_KEY`
- `YOOKASSA_WEBHOOK_SECRET`
- `PAYMENTS_ENCRYPTION_KEY`
- `GREEN_API_TOKEN`

**Средние секреты** (180 дней):
- `TWILIO_AUTH_TOKEN`
- `GRAFANA_API_KEY`

**При компрометации**:
- `SENDGRID_API_KEY`
- `SUPABASE_ANON_KEY`

### 7.3 Аудит доступа

Логи доступа к Vault хранятся минимум **365 дней**.

Проверка прав доступа: **ежеквартально**.

## 8. Контакты и эскалация

**Security Team**: security@example.com  
**DevOps Team**: devops@example.com  
**Incident Response**: incident@example.com (24/7)

## История изменений

| Дата | Версия | Изменение | Автор |
|------|--------|-----------|-------|
| 24.11.2025 | 1.0 | Создание документа, миграция Supabase сервера | BKG Agent |

---

**Следующая плановая ревизия**: 24.02.2026 (через 90 дней)
