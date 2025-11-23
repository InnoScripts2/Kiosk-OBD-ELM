# Kiosk Shell Agent

Локальный агент для управления устройствами киоска и генерации отчётов.

## Структура

- `src/services/` - основные сервисы (платежи, замки, отчёты)
- `src/devices/` - мосты к устройствам (OBD, толщиномер)
- `src/routes/` - HTTP API эндпоинты

## Переменные окружения

Конфигурируются в `.env` в корне проекта:

```env
NODE_ENV=development
APP_MODE=DEV
PAYMENT_MOCK=true
DEVICE_MOCK_OBD=false
DEVICE_MOCK_THICKNESS=false
```

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

# Линтинг
npm run lint

# Тесты
npm test
```

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
└──────┬──────┘
       │ Serial/BLE/USB
┌──────▼──────┐
│ Hardware    │ - OBD адаптер, Толщиномер, Замки
└─────────────┘
```
