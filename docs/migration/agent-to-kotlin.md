# Миграция Node.js/TypeScript агента в Kotlin/Android

Дата: 23.11.2025
Версия: 1.0
Статус: В работе (Session 11)

## Обзор

Данный документ описывает миграцию функциональности Node.js/TypeScript агента из `03-apps/02-application/kiosk-shell/agent` в Android монорепозиторий `android/`. Цель: устранить зависимость от внешнего Node.js сервиса и интегрировать всю бизнес-логику непосредственно в Android-приложение.

## 1. Текущая архитектура Node.js Agent

### Структура каталогов
```
03-apps/02-application/kiosk-shell/agent/
├── src/
│   ├── services/
│   │   ├── ArduinoAdapter.ts (313 строк)
│   │   ├── LockController.ts (275 строк)
│   │   ├── PaymentService.ts (112 строк)
│   │   └── ReportService.ts (148 строк)
│   └── ...
├── logs/
│   ├── sessions/
│   └── issues/
├── package.json
└── tsconfig.json
```

### Зависимости Node.js модуля
- **serialport@12.0.0**: Serial-коммуникация с Arduino
- **@serialport/parser-readline@12.0.0**: Парсинг строк из Serial
- **jest@29.x**: Тестирование
- **typescript@5.x**: Компиляция TypeScript

## 2. Инвентаризация функциональности

### 2.1 ArduinoAdapter (Serial Communication)

#### Публичные API
```typescript
interface ArduinoConfig {
  port: string;              // /dev/ttyUSB0 (Linux), COM3 (Windows)
  baudRate: number;          // 9600
  commandTimeout: number;    // 10000ms
  reconnectDelay: number;    // 5000ms
  heartbeatInterval: number; // 30000ms
}

type ArduinoCommand = 
  | 'OPEN_THICKNESS'
  | 'OPEN_OBD'
  | 'CLOSE_THICKNESS'
  | 'CLOSE_OBD'
  | 'STATUS'
  | 'PING';

interface ArduinoResponse {
  type: 'OK' | 'ERROR' | 'STATUS' | 'PONG' | 'LOG';
  action?: string;
  device?: string;
  status?: {
    thickness: 'OPEN' | 'CLOSED';
    obd: 'OPEN' | 'CLOSED';
  };
  error?: string;
  raw: string;
}

class ArduinoAdapter extends EventEmitter {
  async connect(): Promise<void>
  async disconnect(): Promise<void>
  async sendCommand(command: ArduinoCommand): Promise<ArduinoResponse>
  isConnected(): boolean
}
```

#### Протокол Serial
- **Baud Rate**: 9600
- **Delimiter**: `\n` (newline)
- **Команды** (отправляем):
  - `OPEN_THICKNESS\n`
  - `OPEN_OBD\n`
  - `CLOSE_THICKNESS\n`
  - `CLOSE_OBD\n`
  - `STATUS\n`
  - `PING\n`

- **Ответы** (получаем):
  - `OK:OPENED:THICKNESS\n`
  - `OK:CLOSED:OBD\n`
  - `ERROR:TIMEOUT:THICKNESS\n`
  - `STATUS:THICKNESS=CLOSED,OBD=OPEN\n`
  - `PONG\n`
  - `READY:DISPENCER:v1.0\n`
  - `[LOG] <timestamp> <action> <device>\n`

#### События (EventEmitter)
- `connected`: Подключение установлено
- `disconnected`: Подключение потеряно
- `error`: Ошибка Serial порта
- `response`: Получен ответ от Arduino

#### Логика повторных попыток
- Heartbeat каждые 30 секунд (команда `PING`)
- Auto-reconnect при потере соединения (задержка 5 секунд)
- Command timeout 10 секунд (если нет ответа)

#### Конфигурация из .env
```bash
ARDUINO_PORT=/dev/ttyUSB0
ARDUINO_BAUD=9600
ARDUINO_COMMAND_TIMEOUT=10000
ARDUINO_RECONNECT_DELAY=5000
ARDUINO_HEARTBEAT_INTERVAL=30000
```

### 2.2 LockController (Lock Management)

#### Публичные API
```typescript
type DeviceType = 'thickness' | 'adapter';

interface LockStatus {
  thickness: 'open' | 'closed';
  adapter: 'open' | 'closed';
  error?: string;
  connected: boolean;
}

interface LockControllerConfig {
  arduino?: ArduinoConfig;
  mockMode?: boolean;
  logDir?: string;
}

class LockController {
  async initialize(): Promise<void>
  async openSlot(deviceType: DeviceType): Promise<void>
  async closeSlot(deviceType: DeviceType): Promise<void>
  async getStatus(): Promise<LockStatus>
  async shutdown(): Promise<void>
}
```

#### Логика работы
- **Mock mode** (DEV): Имитация открытия/закрытия с задержкой 500ms
- **Production mode**: Работа через ArduinoAdapter
- **Retry**: 3 попытки с задержкой 1000ms * attempt
- **Логирование**: Все операции записываются в `logs/sessions/lock-operations-<date>.json`

#### Формат логов
```json
{
  "timestamp": "2025-11-23T13:48:16.651Z",
  "action": "open",
  "deviceType": "adapter",
  "success": true,
  "status": "open",
  "sessionId": "session_1763905696652"
}
```

#### Конфигурация из .env
```bash
NODE_ENV=development     # Определяет mockMode (если != production)
APP_MODE=DEV            # Дополнительный флаг режима
```

### 2.3 PaymentService (Payment Processing)

#### Публичные API
```typescript
interface PaymentIntent {
  intentId: string;
  amount: number;
  sessionId: string;
  status: 'pending' | 'confirmed' | 'failed';
  qrCode?: string;
  createdAt: Date;
}

interface PaymentConfirmation {
  intentId: string;
  confirmed: boolean;
  timestamp: Date;
}

class PaymentService {
  async createPaymentIntent(amount: number, sessionId: string): Promise<PaymentIntent>
  async getStatus(intentId: string): Promise<PaymentIntent | null>
  async confirmPayment(intentId: string): Promise<PaymentConfirmation>
  async handleWebhook(payload: Record<string, unknown>): Promise<void>
}
```

#### Логика работы
- **Mock mode** (DEV):
  - Генерация base64 QR-кода из JSON payload
  - Ручное подтверждение через `confirmPayment()`
  - Имитация задержки обработки (1 секунда)
- **Production mode** (будущее):
  - Интеграция с Yandex.Kassa / Stripe
  - Реальная генерация QR от PSP
  - Обработка webhook от провайдера

#### Формат QR payload (mock)
```json
{
  "type": "payment",
  "intentId": "intent_1763905701152_ip0yx2diu",
  "amount": 350,
  "currency": "RUB"
}
```

#### Хранение
- В памяти: `Map<intentId, PaymentIntent>`
- Отсутствие персистентности (в будущем: Room/SQLite)

#### Конфигурация из .env
```bash
PAYMENT_MOCK=true    # Включает mock режим
APP_MODE=DEV         # Определяет доступность ручного подтверждения
```

### 2.4 ReportService (Report Generation)

#### Публичные API
```typescript
interface ThicknessReport {
  sessionId: string;
  timestamp: Date;
  vehicleType: string;
  measurements: Array<{
    zone: string;
    value: number;
    status: string;
  }>;
  analysis: {
    avgValue: number;
    deviations: number;
    recommendation: string;
  };
}

interface DiagnosticsReport {
  sessionId: string;
  timestamp: Date;
  vehicleBrand: string;
  dtcCodes: Array<{
    code: string;
    description: string;
    status: 'info' | 'warning' | 'critical';
  }>;
  clearedCount?: number;
}

class ReportService {
  toHTML(data: ThicknessReport | DiagnosticsReport): string
  async sendEmail(email: string, reportHTML: string, sessionId: string): Promise<{ success: boolean; messageId?: string }>
  async sendSMS(phone: string, summary: string): Promise<{ success: boolean }>
}
```

#### Логика работы
- **Генерация HTML**:
  - Встроенные CSS стили
  - Таблицы с результатами
  - Цветовая дифференциация (зелёный/оранжевый/красный)
- **Отправка** (DEV):
  - Email/SMS не отправляются
  - Логирование содержимого в консоль
- **Отправка** (Production, будущее):
  - Email через SendGrid/SMTP
  - SMS через Twilio/SMSAero

#### Форматы отчётов
**Толщиномер**:
- Таблица замеров по зонам
- Анализ: среднее значение, отклонения, рекомендация

**Диагностика OBD**:
- Таблица DTC кодов
- Статусы: info/warning/critical
- Количество сброшенных ошибок

#### Конфигурация из .env
```bash
APP_MODE=DEV    # Определяет режим отправки (DEV = no send)
```

## 3. Зависимости между компонентами

```
LockController
  ├─> ArduinoAdapter (Serial communication)
  └─> FileSystem (logging to logs/sessions/)

PaymentService
  └─> (independent, в памяти)

ReportService
  └─> (independent, генерация HTML)
```

## 4. Интеграция packages/*

### 4.1 packages/device-obd
- **Назначение**: Интерфейсы и типы для OBD-II устройств
- **Целевой модуль Android**: `feature-obd-core`
- **Ключевые файлы**: (не реализовано в Session 08-10)

### 4.2 packages/device-thickness
- **Назначение**: Интерфейсы и типы для толщиномера
- **Целевой модуль Android**: `feature-thickness`
- **Ключевые файлы**: (не реализовано в Session 08-10)

### 4.3 packages/report
- **Структура** (Session 10):
  ```
  packages/report/
  ├── src/
  │   ├── types/index.ts
  │   ├── templates/
  │   │   ├── thickness-template.ts
  │   │   └── diagnostics-template.ts
  │   ├── renderers/
  │   │   └── pdf-renderer.ts
  │   ├── components/
  │   │   └── svg-icons.ts
  │   ├── styles/
  │   │   ├── design-tokens.ts
  │   │   └── base.ts
  │   ├── utils/
  │   │   ├── validators.ts
  │   │   └── formatters.ts
  │   ├── report-generator.ts
  │   └── index.ts
  ├── tests/ (45 тестов, 100% passed)
  └── package.json
  ```
- **Целевой модуль Android**: `feature-reports`
- **Особенности**:
  - Симметричный дизайн (#0B0D17 фон, #00C4B4 толщиномер, #FFC857 OBD)
  - Сетка 12 колонок, WCAG AA контраст
  - Генерация HTML/PDF (Puppeteer/mock)
  - Валидация данных
  - Маскирование VIN/email/телефона
  - Тепловая карта SVG, круговая диаграмма DTC

### 4.4 packages/payment-mock
- **Назначение**: Mock платёжного провайдера
- **Целевой модуль Android**: `feature-payments`
- **Ключевые файлы**: (не реализовано в Session 08-10)

## 5. План миграции в Android

### Фаза 1: Создание модулей (Session 11, этап 2)
- [x] `feature-lock-control`: Управление замками через UsbManager/Serial
- [x] `feature-payments`: Обновление для платёжных намерений (модуль уже существует)
- [x] `feature-reports`: Обновление для генерации отчётов (модуль уже существует)
- [ ] `feature-agent-bridge`: Оркестрация сервисов, логирование

### Фаза 2: Перенос ArduinoAdapter/LockController (Session 11, этап 3)
**Целевой модуль**: `android/feature-lock-control`

**API Android** (Kotlin):
```kotlin
// UsbSerialAdapter.kt
interface UsbSerialAdapter {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendCommand(command: ArduinoCommand): ArduinoResponse
    fun isConnected(): Boolean
}

// ArduinoCommand.kt
enum class ArduinoCommand {
    OPEN_THICKNESS,
    OPEN_OBD,
    CLOSE_THICKNESS,
    CLOSE_OBD,
    STATUS,
    PING
}

// LockController.kt
interface LockController {
    suspend fun initialize()
    suspend fun openSlot(deviceType: DeviceType)
    suspend fun closeSlot(deviceType: DeviceType)
    suspend fun getStatus(): LockStatus
    suspend fun shutdown()
}
```

**Технологии**:
- `UsbManager` (Android SDK)
- `usb-serial-for-android` библиотека (если нужно)
- Kotlin Coroutines + Flow для событий
- WorkManager для heartbeat
- Room для логирования (или File API)

**Тесты**:
- Unit-тесты с Mock UsbDevice
- Instrumented тесты (если возможно)

### Фаза 3: Перенос PaymentService (Session 11, этап 4)
**Целевой модуль**: `android/feature-payments`

**API Android** (Kotlin):
```kotlin
// PaymentService.kt
interface PaymentService {
    suspend fun createPaymentIntent(amount: Int, sessionId: String): PaymentIntent
    suspend fun getStatus(intentId: String): PaymentIntent?
    suspend fun confirmPayment(intentId: String): PaymentConfirmation
    suspend fun handleWebhook(payload: Map<String, Any>)
}

// PaymentIntent.kt
data class PaymentIntent(
    val intentId: String,
    val amount: Int,
    val sessionId: String,
    val status: PaymentStatus,
    val qrCode: String?,
    val createdAt: Instant
)

enum class PaymentStatus {
    PENDING, CONFIRMED, FAILED
}
```

**Технологии**:
- Room для хранения PaymentIntent
- WorkManager для эмуляции webhook (DEV)
- QR-генерация через библиотеку (zxing или packages/рес 1)
- BuildConfig для PAYMENT_MOCK флага

**Тесты**:
- Unit-тесты для бизнес-логики
- Instrumented тесты для Room DAO

### Фаза 4: Перенос ReportService (Session 11, этап 5)
**Целевой модуль**: `android/feature-reports`

**API Android** (Kotlin):
```kotlin
// ReportService.kt
interface ReportService {
    fun toHTML(data: Report): String
    suspend fun sendEmail(email: String, reportHTML: String, sessionId: String): SendResult
    suspend fun sendSMS(phone: String, summary: String): SendResult
}

// Report.kt
sealed class Report {
    data class Thickness(
        val sessionId: String,
        val timestamp: Instant,
        val vehicleType: String,
        val measurements: List<Measurement>,
        val analysis: Analysis
    ) : Report()
    
    data class Diagnostics(
        val sessionId: String,
        val timestamp: Instant,
        val vehicleBrand: String,
        val dtcCodes: List<DtcCode>,
        val clearedCount: Int?
    ) : Report()
}
```

**Технологии**:
- `PdfDocument` (Android SDK) или библиотека (iTextPDF, PdfBox)
- HTML-шаблоны через string resources или assets
- Email/SMS через интеграции (SendGrid/Twilio SDK)
- File API для локального хранения

**Тесты**:
- Unit-тесты для HTML-генерации
- Snapshot-тесты для PDF (сравнение байтов)
- Mock тесты для email/SMS

### Фаза 5: Интеграция device bridges (Session 11, этап 6)
**Модули**: `feature-obd-core`, `feature-thickness`

**Задачи**:
- Перенести логику из `packages/device-obd` в `feature-obd-core`
- Перенести логику из `packages/device-thickness` в `feature-thickness`
- Обновить EventFlow для real-time данных
- Синхронизировать состояния с ViewModels

### Фаза 6: Логирование (Session 11, этап 7)
**Модуль**: `platform-logging` (уже существует)

**API Android** (Kotlin):
```kotlin
// KioskLogger.kt
object KioskLogger {
    fun logSession(event: SessionEvent)
    fun logIssue(issue: Issue)
    fun exportLogs(dateRange: ClosedRange<LocalDate>): File
}

// SessionEvent.kt
data class SessionEvent(
    val timestamp: Instant,
    val sessionId: String,
    val action: String,
    val deviceType: String?,
    val success: Boolean,
    val error: String?
)
```

**Технологии**:
- Timber (уже подключен в dependencies)
- File appender для записи в `logs/sessions/`
- WorkManager для автоматической очистки (30 дней)

### Фаза 7: UI и конфигурация (Session 11, этап 8)
**Модуль**: `app`

**Задачи**:
- Обновить ViewModels для работы с Kotlin-сервисами
- Убрать HTTP-запросы к Node.js агенту
- Добавить BuildConfig флаги (DEV/QA/PROD)
- Обновить документацию

**BuildConfig переменные**:
```kotlin
// build.gradle.kts
buildTypes {
    debug {
        buildConfigField("Boolean", "PAYMENT_MOCK", "true")
        buildConfigField("Boolean", "DEVICE_MOCK_OBD", "false")
        buildConfigField("Boolean", "DEVICE_MOCK_THICKNESS", "false")
        buildConfigField("String", "ARDUINO_PORT", "\"/dev/ttyUSB0\"")
        buildConfigField("Int", "ARDUINO_BAUD", "9600")
    }
    release {
        buildConfigField("Boolean", "PAYMENT_MOCK", "false")
        // ...
    }
}
```

## 6. Риски и ограничения

### 6.1 Serial коммуникация на Android
**Проблема**: Android не имеет встроенной поддержки Serial (UART) без root
**Решения**:
- USB-to-Serial адаптеры (FT232, CH340) через `UsbManager`
- Библиотека `usb-serial-for-android` (open-source)
- Bluetooth-to-Serial адаптеры (как альтернатива)

### 6.2 Генерация PDF на Android
**Проблема**: Нет встроенной библиотеки для сложного PDF (таблицы, стили)
**Решения**:
- `PdfDocument` (базовый, требует ручной вёрстки)
- iTextPDF (коммерческая лицензия для Android)
- HTML → WebView → PDF (через `WebView.createPrintDocumentAdapter()`)

### 6.3 Background задачи
**Проблема**: Android ограничивает background работу (Doze mode)
**Решения**:
- WorkManager для периодических задач
- Foreground Service для critical операций (heartbeat)
- Exempt from battery optimization (для киоска)

### 6.4 Тестирование Serial/USB
**Проблема**: Нет реального устройства для CI/CD
**Решения**:
- Mock UsbDevice в unit-тестах
- Instrumented тесты с эмулятором (ограничено)
- Manual тестирование на реальном устройстве

## 7. Критерии успеха миграции

- [ ] Все 4 сервиса перенесены в Kotlin
- [ ] Unit-тесты покрытие ≥70% для бизнес-логики
- [ ] APK собирается без ошибок (`./gradlew assembleDebug`)
- [ ] UI работает без HTTP-запросов к Node.js
- [ ] Логирование работает (файлы в `logs/`)
- [ ] Mock режим работает (DEV)
- [ ] Документация обновлена

## 8. Метрики

| Метрика | Текущее (Node.js) | Целевое (Kotlin) |
|---------|------------------|------------------|
| Файлов кода | 4 сервиса | 15-20 файлов |
| Строк кода | ~850 строк | ~2500-3000 строк |
| Тестов | 32 теста | 50+ тестов |
| Зависимости | serialport, jest | usb-serial-for-android, Timber, Room |
| Размер APK | 0 MB (не входит) | +2-3 MB |

## 9. Следующие шаги

1. Создать новые модули Gradle
2. Настроить DI (Hilt)
3. Реализовать UsbSerialAdapter
4. Реализовать LockController
5. Реализовать PaymentService
6. Реализовать ReportService
7. Интегрировать с UI
8. Написать тесты
9. Обновить документацию
10. Собрать APK и провести тестирование

## Приложения

### A. Ссылки на исходники
- Node.js Agent: `03-apps/02-application/kiosk-shell/agent/src/services/`
- Packages: `packages/device-obd`, `packages/device-thickness`, `packages/report`, `packages/payment-mock`
- Android модули: `android/feature-*`, `android/platform-*`

### B. Ссылки на тесты
- Node.js тесты: `03-apps/02-application/kiosk-shell/agent/src/services/*.test.ts` (26/32 passed)
- Android тесты: `android/*/src/test/` (не созданы)

### C. Ссылки на документацию
- Инструкции проекта: `.github/instructions/instructions.instructions.md`
- Arduino README: `android/ARDUINO_DISPENCER_README.md`
- Navigation flow: `docs/navigation-flow.md`
- Reporting guidelines: `docs/reporting-guidelines.md`

---

## Статус миграции

**Обновлено**: 24.11.2025 (Session 13B)

### Завершено ✅
- **Session 11 (23.11.2025)**: `feature-lock-control` (11 файлов, ~1450 строк, 32 теста)
  - UsbSerialAdapterImpl через usb-serial-for-android:3.7.3
  - Протокол Arduino: OPEN_*/CLOSE_*/STATUS/PING
  - MockUsbSerialAdapter для DEV-режима
- **Session 12 (23.11.2025)**: `feature-reports` (30 файлов, ~12,000 строк, 42 теста)
  - HTML/PDF генераторы (симметричный дизайн)
  - ReportStorageManager с SHA-256 checksums
  - Mock email/SMS delivery сервисы
  - Интеграция с Supabase (Session 19-21)
- **Session 12B (24.11.2025)**: `feature-thickness` базовая миграция (22 файла, ~4800 строк, 123 теста)
  - Модели данных (60 зон измерений)
  - BLE адаптер с platform/bluetooth интеграцией
  - Protocol parser (ASCII/Binary)
  - Mock устройство [MOCK MODE]
  - State machine с 60 точками
  - Workflow оркестрация
- **Session 13B (24.11.2025)**: `feature-thickness` completion (5 файлов, ~1530 строк, 47 тестов)
  - 6 специализированных exception классов
  - Comprehensive BLE adapter tests (Mock через Mockito)
  - UI API интерфейсы (7 interfaces)
  - API implementations (Controller, Observer, Validator)
  - Exponential backoff reconnect strategy
  - Полное тестовое покрытие (~75%)

### В работе 🚧
- **PaymentService**: Обнаружен существующий модуль `feature-payments` (Session 06-07), требуется аудит и обновление
- **OBD адаптер**: Session 10B-11B завершён, требуется UI интеграция
- **UI integration**: экраны Session 09-10 частично готовы (58%), требуется доработка

### Не начато ⏳
- Полная интеграция DI/Hilt для всех модулей
- Замена моков production сервисами (SendGrid, Twilio, WebView.printPdf)
- Автоматизация деплоя и мониторинга

---

## Текущие блокеры

**Обновлено**: 23.11.2025 (Session 1G)

### AGP 8.4.1 недоступен
- **Статус**: Критический, открыт с Session 08
- **Воздействие**: Блокирует компиляцию всех Android модулей (`./gradlew :app:assembleDebug` fails)
- **Обход**: Code review без сборки APK, тестирование Node.js компонентов отдельно
- **Попытки решения**:
  - maven.aliyun.com добавлен в settings.gradle.kts — не помогло
  - JitPack для сторонних библиотек — не применимо для AGP
  - Downgrade до AGP 8.3.x — конфликты с Kotlin 1.9.x и Compose
- **Детали**: См. `logs/issues/2025-11-23-agp-blocker.json`
- **Резолюция**: Ожидание whitelist dl.google.com или полный локальный Maven mirror

### Maven зеркала неполные
- **Статус**: Высокий, связан с AGP blocker
- **Воздействие**: Некоторые зависимости (Compose, Hilt) недоступны
- **Обход**: Использование JitPack, manual downloads (не рекомендуется)
- **Резолюция**: Обновление китайских зеркал или прямой доступ к Maven Central

---

## История изменений

| Дата       | Версия | Изменения                                               |
|------------|--------|---------------------------------------------------------|
| 24.11.2025 | 1.2    | Session 13B: обновлён статус миграции feature-thickness (12B + 13B) |
| 23.11.2025 | 1.1    | Session 1G: добавлены секции "Статус миграции" и "Текущие блокеры" |
| 23.11.2025 | 1.0    | Session 11: создание документа миграции                   |

---

**Актуально на**: 24.11.2025  
**Следующее обновление**: после Session 14 (UI ViewModels интеграция)
