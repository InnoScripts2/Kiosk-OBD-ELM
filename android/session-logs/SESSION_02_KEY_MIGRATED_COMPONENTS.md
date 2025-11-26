# Сессия 2: Ключевые перенесённые компоненты

## OBD-II Команды (рес 4 → feature-obd-core)

### AT Commands
- `ATCommand.kt` - Базовый класс AT команд
- `at/Actions.kt` - AT команды действий
- `at/Info.kt` - Информационные AT команды
- `at/Mutations.kt` - Мутации конфигурации

### Control Commands
- `control/AvailableCommands.kt` - Список доступных команд
- `control/Control.kt` - Контрольные команды
- `control/MIL.kt` - Malfunction Indicator Lamp
- `control/Monitor.kt` - Мониторинг систем
- `control/TroubleCodes.kt` - DTC коды ошибок

### Engine & Sensors
- `engine/Engine.kt` - Команды двигателя (RPM, нагрузка)
- `fuel/Fuel.kt` - Топливная система
- `fuel/Ratio.kt` - Air/fuel ratio
- `pressure/Pressure.kt` - Давление (boost, барометр, топливо)
- `temperature/Temperature.kt` - Температуры (охлаждающая жидкость, впуск)
- `egr/Egr.kt` - EGR система

### Connection Layer
- `connection/ObdDeviceConnection.kt` - Абстракция подключения к OBD устройству

### Core Types
- `ObdCommand.kt` - Базовый интерфейс команд
- `Response.kt` - Типы ответов
- `Enums.kt` - Перечисления
- `Exceptions.kt` - Исключения OBD
- `ParserFunctions.kt` - Парсеры ответов
- `RegexUtils.kt` - Регулярные выражения для парсинга

## Reactive Programming (рес 6 → platform/bluetooth/reaktive)

### Core Reaktive
- Reaktive library - 855 файлов
- Observable, Single, Maybe, Completable паттерны
- Reaktive-testing инструменты
- RxJava2/3 interop
- Coroutines interop

### Модули
- `reaktive-annotations/` - Аннотации
- `reaktive-testing/` - Тестовые утилиты
- `rxjava2-interop/` - Совместимость с RxJava 2
- `rxjava3-interop/` - Совместимость с RxJava 3
- `coroutines-interop/` - Интеграция с coroutines

## Flow Extensions (рес 5 → platform/ui/flowext)

FlowExt - 127 файлов утилит для Kotlin Flow:
- Buffering операторы
- Windowing
- Rate limiting
- Testing helpers

## Serialization (рес 3 → platform/data/serialization)

kotlinx-serialization-json - 1146 файлов:
- JSON, Protobuf, CBOR форматы
- Core serialization framework
- Platform-specific реализации (JVM, JS, Native)

## Supabase SDK (рес 7 → platform/data/supabase)

Supabase Kotlin SDK - 808 файлов:

### Модули
- `Auth/` - Аутентификация и авторизация
- `Postgrest/` - PostgreSQL REST API
- `Realtime/` - WebSocket realtime subscriptions
- `Storage/` - File storage
- `Functions/` - Edge functions

### Сериализаторы
- `serializers/Jackson/` - Jackson интеграция
- `serializers/Moshi/` - Moshi интеграция

### Дополнительно
- `ComposeAuth/` - Jetpack Compose интеграция аутентификации
- `ImageLoader/` - Загрузка изображений
- Sample приложения (Android, desktop, iOS, web)

## CarDash Components (рес 2 → feature-kiosk-mode)

### Services
- `CarDashDataCollectorService.kt` - Сбор данных
- `CarDashCarAppService.kt` - Android Auto сервис
- `CarDashSession.kt` - Управление сессией

### OBD Services
- `OBDService.kt` - Базовый OBD сервис
- `OBDServiceWithDiagnostics.kt` - С диагностикой
- `ObdConnectionService.kt` - Управление подключением
- `BluetoothManager.kt` - BLE менеджер

### UI Metrics (60 компонентов)
- Speed, RPM, Engine Load
- Fuel Level, Fuel Pressure, Fuel Usage
- Coolant Temperature, Intake Air Temperature
- Throttle Position, Baro Pressure
- Sequential Metric Poller
- Metric Grid & Factory

### Utils
- `OBDLogger.kt` - Логирование OBD
- `MockDataGenerator.kt` - Генератор моковых данных (DEV)
- `MockDiagnosticGenerator.kt` - Генератор диагностики (DEV)

## Итого

**Всего компонентов:** 3143 файла  
**Модулей затронуто:** 7  
**Линий кода:** ~500,000+ (оценка)

### Технологический стек
- Kotlin/JVM
- Kotlin Multiplatform
- Android SDK
- Jetpack Compose
- Reactive Programming (Reaktive, RxJava, Flow)
- Networking (Ktor, OkHttp)
- Serialization (kotlinx.serialization)
- Testing (JUnit, Mockito, Reaktive-testing)
