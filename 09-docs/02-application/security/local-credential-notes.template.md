# Шаблон локальной фиксации секретов

Этот файл предназначен только для создания **локальной** копии `local-credential-notes.local.md`,
куда операторы заносят фактические значения Supabase, платежных и прочих ключей.

1. Скопируйте файл:
   ```bash
   cp 09-docs/02-application/security/local-credential-notes.template.md \
      09-docs/02-application/security/local-credential-notes.local.md
   ```
2. Добавьте реальные значения ключей в секции ниже.
3. Файл `local-credential-notes.local.md` попадает в `.gitignore`, поэтому **не** коммитьте его.
4. Для безопасного обмена используйте Vault или защищённый канал вне репозитория.

## Supabase

| Ключ                        | Значение (заполнить локально) | Источник                                               |
| --------------------------- | ----------------------------- | ------------------------------------------------------ |
| `SUPABASE_URL`              |                               | Публичный                                              |
| `SUPABASE_ANON_KEY`         |                               | Supabase → Project Settings                            |
| `SUPABASE_SERVICE_ROLE_KEY` |                               | Vault `kv/selfservice/platform/supabase/{env}/service` |
| `SUPABASE_JWT_SECRET`       |                               | Vault                                                  |
| `POSTGRES_URL`              |                               | Supabase Dashboard → Database                          |

## YooKassa

| Ключ                      | Значение (локально) | Источник |
| ------------------------- | ------------------- | -------- |
| `YOOKASSA_SHOP_ID`        |                     | PSP      |
| `YOOKASSA_SECRET_KEY`     |                     | PSP      |
| `YOOKASSA_WEBHOOK_SECRET` |                     | Vault    |

## Прочие интеграции

Добавляйте дополнительные таблицы по мере необходимости (Email, SMS, Prometheus и т. д.).

> ⚠️ **Напоминание**: этот файл служит только локальным блокнотом. Все рабочие конфигурации
> считываются из переменных окружения, `.env.local` и Vault, а не из репозитория.
