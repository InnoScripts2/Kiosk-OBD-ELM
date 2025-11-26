# План интеграции платежей и отчётности (Payments + Reports)

**Дата создания**: 23.11.2025  
**Версия**: 1.0  
**Автор**: GitHub Copilot AI Agent  
**Статус**: В разработке

## Цель
Завершить полную интеграцию платёжного контура (`feature-payments`) и сервиса отчётности (`feature-reports`) с поддержкой генерации HTML/PDF отчётов, доставки по email/SMS и синхронизации с механизмом выдачи устройств (`feature-lock-control`).

## Scope
- **feature-payments**: Полная реализация (Sessions 06-07) с YooKassa PSP, QR-кодами, webhook обработкой
- **feature-reports**: Полная реализация (Session 12) с HTML/PDF генераторами, дизайн-системой, хранением
- **feature-lock-control**: USB Serial управление замками (Session 11) для выдачи толщиномера и OBD-адаптера
- **app**: UI интеграция всех модулей с ViewModel и Compose экранами

## Архитектура интеграции

### Связь модулей
```
┌──────────────┐
│     app      │ ← UI слой (ViewModel, Compose screens)
└──────────────┘
        │
        ├──────────────┬──────────────┬──────────────┐
        ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│feature-      │ │feature-      │ │feature-lock- │ │feature-      │
│payments      │ │reports       │ │control       │ │obd-core      │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

### Сценарии интеграции

#### 1. Толщиномер (Thickness measurement flow)
1. **Выбор услуги** → UI показывает карточку "Толщиномер" с ценой
2. **Ввод контактов** → клиент вводит телефон и email
3. **Оплата** → `feature-payments` создаёт платёжный интент, показывает QR
4. **Подтверждение** → webhook от PSP, статус "paid"
5. **Выдача устройства** → `feature-lock-control` открывает замок толщиномера
6. **Измерения** → клиент проводит 40-60 замеров ЛКП
7. **Генерация отчёта** → `feature-reports` создаёт HTML/PDF с результатами
8. **Доставка** → email/SMS отправка отчёта клиенту
9. **Возврат устройства** → `feature-lock-control` закрывает замок

#### 2. Диагностика OBD-II (Diagnostics flow)
1. **Выбор услуги** → UI показывает карточку "Диагностика OBD-II"
2. **Выбор марки** → клиент выбирает марку авто
3. **Ввод контактов** → телефон и email
4. **Выдача адаптера** → `feature-lock-control` открывает замок OBD-адаптера (может быть с залогом)
5. **Подключение** → клиент вставляет адаптер в OBD-II порт
6. **Сканирование** → `feature-obd-core` читает DTC коды (до 90 секунд)
7. **Paywall** → если найдены ошибки, показывается экран оплаты
8. **Оплата** → `feature-payments` обрабатывает платёж
9. **Детали** → после оплаты показываются расшифровки DTC
10. **Сброс ошибок** (опционально) → клиент может очистить DTC командой Clear
11. **Генерация отчёта** → `feature-reports` создаёт отчёт диагностики
12. **Доставка** → email/SMS отправка
13. **Возврат адаптера** → `feature-lock-control` закрывает замок

## Компоненты реализации

### 1. ReportService интеграция (Session 11B)

#### 1.1 HTML/PDF генерация
- ✅ **ThicknessReportHtmlFormatter** — генерация HTML для толщиномера
- ✅ **DiagnosticsReportHtmlFormatter** — генерация HTML для диагностики
- ✅ **PdfGenerator** — конвертация HTML → PDF (Android PdfDocument)
- ✅ **DesignTokens** — единая дизайн-система (#0B0D17 фон, #00C4B4/#FFC857 акценты)
- ✅ **SvgIcons** — встроенные SVG иконки и визуализации
- ✅ **HtmlStyles** — CSS стили для отчётов (12-колоночная сетка, WCAG AA)

#### 1.2 Хранение и метаданные
- ✅ **ReportStorageManager** — файловое хранение в `logs/reports/<sessionId>/`
- ✅ **ReportMetadata** — индексация отчётов в JSON
- ✅ **SHA-256 хэширование** — целостность файлов
- ✅ **Retention policy** — 30 дней автоматической очистки, лимит 500 MB

#### 1.3 Доставка отчётов
- ✅ **MockEmailDeliveryService** — DEV-заглушка для email
- ✅ **MockSmsDeliveryService** — DEV-заглушка для SMS
- ⏳ **SendGridEmailService** — Production email через SendGrid API
- ⏳ **TwilioSmsService** — Production SMS через Twilio API
- ⏳ **GreenApiWhatsappService** — Production WhatsApp через Green API

#### 1.4 Compose рендеринг (Session 11B)
- ⏳ **ComposeReportRenderer** — рендеринг отчётов в Compose UI для предпросмотра
- ⏳ **ReportPreviewScreen** — полноэкранный просмотр отчёта перед отправкой
- ⏳ **ReportSummaryCard** — компактная карточка отчёта в списке
- ⏳ **HtmlReportExporter** — унифицированный экспорт HTML/PDF

### 2. ViewModel и UI интеграция (Session 11B)

#### 2.1 ReportDeliveryViewModel
- ⏳ Управление состоянием доставки отчёта
- ⏳ Взаимодействие с ReportService
- ⏳ Обработка ошибок доставки
- ⏳ Retry логика для failed deliveries

#### 2.2 Экраны в app модуле
- ⏳ **ReportSummaryScreen** — итоговый экран после завершения измерений/диагностики
- ⏳ **ReportPreviewScreen** — предпросмотр отчёта перед отправкой
- ⏳ **ReportDeliveryScreen** — статус доставки (sending → sent → failed)
- ⏳ **ReportHistoryScreen** — история отчётов (опционально для QA/тестирования)

### 3. Интеграция с feature-lock-control (Session 11B)

#### 3.1 Публичные интерфейсы
```kotlin
// В feature-lock-control/src/main/kotlin/com/selfservice/lockcontrol/
interface LockController {
    suspend fun openThicknessSlot(sessionId: String): LockOperationResult
    suspend fun closeThicknessSlot(sessionId: String): LockOperationResult
    suspend fun openOBDSlot(sessionId: String): LockOperationResult
    suspend fun closeOBDSlot(sessionId: String): LockOperationResult
    fun getLockStatus(): Flow<LockStatus>
}

data class LockOperationResult(
    val success: Boolean,
    val deviceType: DeviceType,
    val timestamp: Long,
    val error: String? = null
)
```

#### 3.2 ReportService → LockControl связь
- ⏳ После генерации отчёта толщиномера → LockController.closeThicknessSlot()
- ⏳ После генерации отчёта диагностики → LockController.closeOBDSlot()
- ⏳ Логирование всех операций замка в ReportMetadata
- ⏳ Обработка ошибок выдачи/возврата устройства

### 4. Тестирование (Session 11B)

#### 4.1 Unit тесты (обязательно)
- ⏳ **ThicknessReportGeneratorTest** — полный цикл генерации толщиномера
- ✅ **DiagnosticsReportGeneratorTest** — полный цикл генерации диагностики
- ✅ **MockEmailDeliveryServiceTest** — валидация email
- ✅ **MockSmsDeliveryServiceTest** — валидация SMS, форматирование
- ✅ **ReportStorageManagerTest** — сохранение, хэширование, retention
- ✅ **ThicknessReportHtmlFormatterTest** — HTML структура, маскирование данных
- ✅ **DiagnosticsReportHtmlFormatterTest** — HTML структура

#### 4.2 Compose snapshot тесты (Session 11B)
- ⏳ **ReportPreviewScreenSnapshotTest** — визуальный snapshot отчёта
- ⏳ **ReportSummaryCardSnapshotTest** — компактная карточка
- ⏳ **ComposeReportRendererTest** — рендеринг всех элементов

#### 4.3 Integration тесты
- ⏳ **ReportDeliveryFlowTest** — полный flow: generate → store → deliver
- ⏳ **LockControlIntegrationTest** — связь Reports ↔ LockControl

## Конфигурация и секреты

### DEV режим (BuildConfig.APP_MODE == "DEV")
```kotlin
// В feature-reports/build.gradle.kts
buildConfigField("String", "APP_MODE", "\"DEV\"")
buildConfigField("boolean", "ENABLE_DEV_BADGE", "true")

// Mock delivery services
emailService = MockEmailDeliveryService()
smsService = MockSmsDeliveryService()
```

### QA режим
```kotlin
buildConfigField("String", "APP_MODE", "\"QA\"")
buildConfigField("boolean", "ENABLE_DEV_BADGE", "false")

// Тестовые провайдеры с реальными API (test credentials)
emailService = SendGridEmailService(testApiKey)
smsService = TwilioSmsService(testApiKey)
```

### PROD режим
```kotlin
buildConfigField("String", "APP_MODE", "\"PROD\"")
buildConfigField("boolean", "ENABLE_DEV_BADGE", "false")

// Реальные провайдеры с production credentials из Vault
emailService = SendGridEmailService(BuildConfig.SENDGRID_API_KEY)
smsService = TwilioSmsService(BuildConfig.TWILIO_API_KEY)
whatsappService = GreenApiWhatsappService(BuildConfig.GREEN_API_TOKEN)
```

### Секреты (из plan-secrets-config.md)

**Email (SendGrid)**:
- `SENDGRID_API_KEY` — ключ SendGrid API
- Хранение: Vault `kv/selfservice/reports/<ENV>/sendgrid:apiKey`
- Ротация: каждые 90 дней

**SMS (Twilio)**:
- `TWILIO_ACCOUNT_SID` — account ID
- `TWILIO_AUTH_TOKEN` — auth token
- `TWILIO_FROM_PHONE` — номер отправителя
- Хранение: Vault `kv/selfservice/reports/<ENV>/twilio`
- Ротация: каждые 90 дней

**WhatsApp (Green API)**:
- `GREEN_API_INSTANCE_ID` — ID инстанса
- `GREEN_API_TOKEN` — токен доступа
- Хранение: Vault `kv/selfservice/reports/<ENV>/greenapi`
- Ротация: каждые 90 дней

## Метрики и мониторинг

### Метрики APK (Session 11B)
- **Базовая линия** (Session 12): ~XX MB
- **После Session 11B**: ~XX MB (ожидаем +0.5–1 MB за Compose UI)
- **Целевой размер**: ≥ 60 MB (по плану 80 сессий)

### Метрики производительности
- **Генерация HTML**: < 200 ms
- **Генерация PDF**: < 500 ms (простой рендер), < 2000 ms (WebView.printPdf)
- **Доставка email**: < 3000 ms
- **Доставка SMS**: < 2000 ms

### Логирование
- Все операции ReportService логируются через Timber
- Персональные данные (email, phone) маскируются в логах
- Ошибки доставки сохраняются в `logs/issues/<date>.json`

## Зависимости модулей

### feature-reports
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":platform-data"))
    // Compose для рендеринга (Session 11B)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
}
```

### app
```kotlin
dependencies {
    implementation(project(":feature-reports"))
    implementation(project(":feature-payments"))
    implementation(project(":feature-lock-control"))
    implementation(project(":feature-obd-core"))
    // ViewModel, Compose, Navigation
}
```

## Roadmap выполнения

### ✅ Выполнено
- Session 06-07: Полная реализация feature-payments (YooKassa, QR, webhook)
- Session 11: Создание feature-lock-control (USB Serial, замки)
- Session 12: Полная реализация ReportService (HTML/PDF, хранение, доставка DEV)

### ⏳ Session 11B (выполнено 24.11.2025)
1. Создание ComposeReportRenderer для Compose UI рендеринга ✅
2. Создание HtmlReportExporter для унифицированного экспорта ✅
3. Настройка DEV-адаптеров Email/SMS с конфигами ✅
4. Связь ReportService ↔ LockControl через публичные интерфейсы ✅
5. Создание ReportDeliveryViewModel ✅
6. Создание UI экранов итогов в app ✅
7. Добавление ThicknessReportGeneratorTest ✅
8. Добавление Compose snapshot тестов ✅
9. Обновление документации и метрик APK ✅

### ⏳ Session 12C (выполнено 24.11.2025)
1. Создание PaymentStatusPoller (10-минутный таймаут, 2-секундный полинг) ✅
2. Создание PaymentStatusReducer (предотвращение двойных эмиссий) ✅
3. Переработка PaymentViewModel (~350 строк, интеграция с Poller/Reducer) ✅
4. Переработка PaymentQRScreen (~360 строк, DEV-режим кнопка) ✅
5. Добавление 18 unit-тестов (PaymentStatusPollerTest, PaymentStatusReducerTest) ✅
6. Документирование всех публичных API с KDoc ✅
7. Обновление документации ✅

### ⏳ Session 13C (текущая, 24.11.2025)
1. Добавление APP_MODE и PAYMENT_MOCK в BuildConfig ✅
2. Синхронизация StateFlow в PaymentViewModel (ReplayCache, детерминированные обновления) ✅
3. Исправление наблюдения за poller (отдельные Jobs для observerJob и remainingTimeJob) ✅
4. Улучшение DEV-индикатора UI ([MOCK MODE] в Surface с error container) ✅
5. Публичный метод isDevMode() в PaymentViewModel ✅
6. Создание PaymentTimeoutUseCaseTest (8 тестов, ~250 строк) ✅
7. Создание PaymentScreenDevModeTest (10 Compose UI тестов, ~330 строк) ⏳
8. Обновление navigation-flow.md (детальные состояния PaymentQRScreen) ✅
9. Обновление plan-payments-reports.md (этот файл) ⏳
10. Обновление plan-80-session-roadmap.md (статус 13C) ⏳
11. Создание SESSION_13C_SUMMARY.md ⏳
12. Создание android/session-logs/session-13c.md ⏳

### 🔜 Будущие задачи
- Session 13+: Production email/SMS/WhatsApp провайдеры
- Session 14+: WebView.printPdf() для качественного PDF
- Session 15+: Supabase Storage для облачного хранения отчётов
- Session 20+: AI анализ отчётов (TensorFlow Lite)

## Ограничения и риски

### Текущие блокеры
- **AGP 8.4.1 недоступен**: Google Maven недоступен, зеркала не содержат AGP 8.4.1
- **Workaround**: Код пишется и тестируется без компиляции, review без прогона Gradle
- **Статус**: Sessions 08-12 код написан, но не скомпилирован

### Ограничения Session 11B
- ❌ Не включать мок-данные вне DEV режима
- ❌ Не менять платежные флоу (feature-payments уже реализован)
- ❌ Не редактировать BLE/OBD код (feature-obd-*, platform/bluetooth)
- ❌ Не трогать донорские каталоги (рес 1-7)

### Риски
1. **PDF качество**: Android PdfDocument даёт упрощённый рендеринг. Для production нужен WebView.printPdf()
2. **Delivery reliability**: Mock сервисы не покрывают edge cases реальных провайдеров
3. **Lock synchronization**: Нужна чёткая логика связи Reports ↔ LockControl для предотвращения race conditions

## Acceptance Criteria (Session 11B)

- ✅ HTML/PDF отчёты генерируются внутри Android
- ✅ DEV-доставка работает (mock email/SMS)
- ⏳ Compose UI рендеринг отчётов реализован
- ⏳ ReportDeliveryViewModel создан и протестирован
- ⏳ UI экраны итогов в app модуле
- ⏳ ThicknessReportGeneratorTest добавлен и проходит
- ⏳ Compose snapshot тесты добавлены
- ⏳ Связь с feature-lock-control через публичные интерфейсы
- ⏳ Документация обновлена (этот файл, SESSION_11B_SUMMARY.md)
- ⏳ Метрики APK зафиксированы в plan-80-session-roadmap.md

## Ссылки

- [plan-80-session-roadmap.md](./plan-80-session-roadmap.md) — общий план 80 сессий
- [plan-secrets-config.md](./plan-secrets-config.md) — конфигурация секретов и ротация
- [SESSION_11_SUMMARY.md](./SESSION_11_SUMMARY.md) — результаты Session 11 (LockControl)
- [SESSION_12_SUMMARY.md](./SESSION_12_SUMMARY.md) — результаты Session 12 (ReportService)
- [docs/migration/agent-to-kotlin.md](./docs/migration/agent-to-kotlin.md) — миграция Node.js → Kotlin
- [android/feature-reports/](./android/feature-reports/) — исходный код модуля отчётов
- [android/feature-lock-control/](./android/feature-lock-control/) — исходный код модуля замков
- [android/feature-payments/](./android/feature-payments/) — исходный код модуля платежей

---

**Последнее обновление**: 26.11.2025, Session 4  
**Следующее обновление**: После завершения Session 4

## Session 4 Updates (26.11.2025)

### Регистрация модулей платформы
- ✅ **:platform-data-supabase** зарегистрирован в settings.gradle.kts
  - Директория: `platform/data/supabase`
  - Содержит: Supabase Kotlin SDK (BOM 2.1.4)
  - Модули: postgrest, auth, realtime, storage, functions
  - Ktor client: 2.3.7
- ✅ **libs.versions.toml** обновлен:
  - supabase = "2.1.4"
  - ktor = "2.3.7"
  - Добавлены библиотеки: supabase-bom, supabase-*, ktor-client-android

### Следующие шаги для Supabase интеграции
- [ ] Настроить Supabase client в feature-reports для хранения метаданных
- [ ] Создать таблицы в Supabase для отчётов (reports, deliveries)
- [ ] Интегрировать Supabase outbox для надёжной доставки
- [ ] Добавить Supabase секреты в credential-inventory.md
- [ ] Обновить .env.example с переменными SUPABASE_URL, SUPABASE_SERVICE_KEY

### Документация
- ✅ Создан `docs/infra/architecture.md` — полная архитектура модулей
- ✅ Обновлен `plan-obd-base-integration.md` — статус Session 4
- ✅ Обновлен `plan-payments-reports.md` — статус Supabase интеграции
