# Kiosk Shell Agent

Локальный агент для управления устройствами киоска и генерации отчётов.

## Структура

- `src/services/` - основные сервисы (платежи, замки, отчёты)
- `src/devices/` - мосты к устройствам (OBD, толщиномер)
- `src/routes/` - HTTP API эндпоинты
- `src/integrations/supabase/` - интеграция с Supabase для edge-cache и телеметрии

## Переменные окружения

### Настройка

1. Скопируйте `.env.example` в `.env`:
   ```bash
   cp .env.example .env
   ```

2. Заполните реальные значения секретов из Vault или локальной конфигурации.

3. **ВАЖНО**: Файл `.env` находится в `.gitignore` и **НЕ** должен коммититься!

### Основные переменные

```env
NODE_ENV=development
APP_MODE=DEV
LOG_LEVEL=debug

# Supabase (миграция 24.11.2025 на ddaunoxyguqiejrjtwsf.supabase.co)
SUPABASE_URL=https://ddaunoxyguqiejrjtwsf.supabase.co
SUPABASE_ANON_KEY=<получить из Vault>
SUPABASE_SERVICE_ROLE_KEY=<получить из Vault>

# Postgres (Supabase)
POSTGRES_URL=<connection string>
POSTGRES_PASSWORD=<получить из Vault>

# Платежи
PAYMENT_MOCK=true
YOOKASSA_SHOP_ID=<из Vault для PROD>
YOOKASSA_SECRET_KEY=<из Vault для PROD>
YOOKASSA_WEBHOOK_SECRET=<из Vault для PROD>

# Устройства
DEVICE_MOCK_OBD=false
DEVICE_MOCK_THICKNESS=false
ARDUINO_PORT=COM3
```

### Получение секретов из Vault

Для production и QA окружений секреты извлекаются из HashiCorp Vault:

```bash
# Supabase service key
vault kv get -field=serviceKey kv/selfservice/platform/supabase/dev/service

# YooKassa credentials
vault kv get kv/selfservice/payments/dev/psp
```

**Vault пути**:
- Supabase: `kv/selfservice/platform/supabase/{env}/service`
- Payments: `kv/selfservice/payments/{env}/psp`
- Postgres: `kv/selfservice/platform/supabase/{env}/postgres`

См. полную документацию в `09-docs/02-application/security/credential-inventory.md`.

## Команды

```bash
# Установка зависимостей
npm install

# Разработка (hot reload)
npm run dev

# Сборка
npm run build

# Запуск production
npm start

# Воркёр доставки отчётов (poll Supabase)
npm run worker:reports

# Линтинг
npm run lint

# Тесты
npm test

# Smoke-тест Supabase интеграции
npm test -- --test-name-pattern supabase
```

## HTTP API

Агент поднимает HTTP/WS сервер (по умолчанию `http://localhost:7070`, порт настраивается через `NODE_AGENT_PORT`).

| Метод  | Путь                         | Назначение                                                         |
| ------ | ---------------------------- | ------------------------------------------------------------------ |
| `GET`  | `/health`                    | Сводное состояние сервисов (платежи, замки, отчёты)                |
| `GET`  | `/ui/*`                      | Выдача собранного фронтенда (`platform/ui/web/kiosk-frontend/dist`) |
| `POST` | `/payments/intent`           | Создать платёжный интент (тело: `amount`, `sessionId`)             |
| `GET`  | `/payments/:intentId/status` | Получить статус созданного интента                                 |
| `POST` | `/payments/confirm-dev`      | Подтвердить платёж в DEV (доступно только при `PAYMENT_MOCK=true`) |
| `GET`  | `/locks/status`              | Текущий статус замков толщиномера и OBD-адаптера                   |
| `POST` | `/locks/:device/open`        | Открыть слот (`device = thickness / adapter`)                      |
| `POST` | `/locks/:device/close`       | Закрыть слот устройства                                            |
| `POST` | `/reports/intake`            | Поставить отчёт (diagnostics/thickness) в очередь Supabase         |
| `PUT`  | `/reports/:id/state`         | Обновить состояние отчёта (watchdog, ретраи, ошибки)               |

Веб-сокет `/ws/obd` транслирует обновления состояния устройств и статуса контроллера каждые 5 секунд. Фронтенд автоматом подключается к этому адресу на том же хосте/порту.

### Хостинг фронтенда

- Сборка UI: `npm --prefix platform/ui/web/kiosk-frontend run build`
- Бандл кладётся в `platform/ui/web/kiosk-frontend/dist`
- Агент автоматически ищет этот путь (или `KIOSK_UI_DIST_PATH`) и отдаёт его по `/ui`
- Настроить путь/префикс можно переменными `KIOSK_UI_DIST_PATH`, `KIOSK_UI_ROUTE_PREFIX`, `KIOSK_UI_INDEX_FILE`
- Для резервного CDN запустите `pwsh scripts/powershell/publish-kiosk-frontend.ps1 -ClearBucket -UpdateAndroidString -AppendCacheBuster`, чтобы синхронизировать `dist/` с бакетом `kiosk-ui` и автоматически обновить `kiosk_url_remote`
- Supabase теперь используется только как резервный CDN, основной сценарий — локальный `/ui`

Чтобы фронтенд знал базовый URL агента, используйте параметр `?agent=http://<host>:<port>` или сохраните значение в `localStorage.AGENT_API_BASE`. По умолчанию используется `http://localhost:7070`.

## Сервисы

### PaymentService
- Создание платёжных интентов
- Генерация QR-кодов (mock в DEV)
- Обработка webhook от PSP (production)

### LockController
- Управление электронными замками
- Логирование всех операций
- Поддержка USB реле / GPIO / HTTP API

### ReportService
- Генерация HTML/PDF отчётов
- Отправка по Email (SendGrid)
- Отправка SMS (Twilio)
- Edge-cache репликация в Supabase

## Воркер доставки отчётов

- Файл входа: `src/workers/report-delivery-worker.ts`
- Запуск: `npm run worker:reports` (использует `tsx`, поддерживает hot-reload)
- Источник данных: `diagnostics_reports` / `thickness_reports` и очереди `*_report_deliveries` в Supabase
- Каналы: email (HTML отчёт) и SMS (краткое резюме)
- Повторные попытки: контролируются `REPORT_WORKER_MAX_ATTEMPTS`, между попытками статус возвращается в `queued`
- Воркёр фильтрует задания по `environment` и `KIOSK_SERIAL_NUMBER`, поэтому каждая стойка обрабатывает только свои отчёты
- Тайминги и лимиты очереди настраиваются переменными `REPORT_WORKER_*`

Обновите `.env` перед запуском воркера и убедитесь, что указаны `SUPABASE_SERVICE_ROLE_KEY`, `KIOSK_SERIAL_NUMBER` и режим (`APP_MODE`/`KIOSK_ENVIRONMENT`).

## Интеграция с Supabase

**Миграция**: 24.11.2025 - новый сервер `ddaunoxyguqiejrjtwsf.supabase.co`

### Edge-cache

Локальное кеширование отчётов с офлайн-синхронизацией:
- SQLite база: `./data/edge_cache.db`
- Автоматическая репликация в Supabase
- Retention: 30 дней

### Телеметрия

Heartbeat каждые 5 минут:
- Статус устройств (OBD, толщиномер, замки)
- Метрики платежей
- Состояние edge-cache

### Настройка

```typescript
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_ROLE_KEY
)
```

## Архитектура

```
┌─────────────┐
│  Android UI │
└──────┬──────┘
       │ HTTP/WebSocket
┌──────▼──────┐
│ Agent (TS)  │
├─────────────┤
│ Services    │ - PaymentService, LockController, ReportService
├─────────────┤
│ Devices     │ - OBD Bridge, Thickness Bridge
├─────────────┤
│ Integrations│ - Supabase, Email, SMS
└──────┬──────┘
       │ Serial/BLE/USB
┌──────▼──────┐
│ Hardware    │ - OBD адаптер, Толщиномер, Замки
└─────────────┘
```

## Родительский модуль

Этот агент является частью Android-монорепо: `android/platform-ui/web/agent/`

**Будущая миграция**: Планируется перенос из `03-apps/02-application/kiosk-shell/agent/` 
в структуру Android-монорепо согласно `android/plan-multi-language-consolidation.md`.

## Безопасность

- Все секреты хранятся в Vault, не в git
- `.env` файл в `.gitignore`
- Service role key только для backend операций
- Шифрование payment intents (AES-256-GCM)
- Аудит логи в `logs/agent/`

См. полный реестр: `09-docs/02-application/security/credential-inventory.md`

