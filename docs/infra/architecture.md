# Архитектура Android Монорепозитория

**Дата создания**: 26.11.2025  
**Версия**: 1.0  
**Статус**: Session 4 — Регистрация донорских модулей

## Обзор

Проект организован как Android-монорепозиторий с модульной архитектурой. Все модули находятся в папке `android/` и управляются через Gradle.

## Структура модулей

### Основные модули

#### `:app`
Главное Android-приложение. Точка входа киоска самообслуживания.
- **Namespace**: `com.selfservice.kiosk`
- **Тип**: Android Application
- **Зависимости**: Все feature-* и platform-* модули

#### `:core`
Общие утилиты, конфигурации и базовые классы для всех модулей.
- **Namespace**: `com.selfservice.core`
- **Тип**: Android Library
- **Экспортирует**: Общие утилиты, менеджер окружений, базовые интерфейсы

### Feature модули

#### `:feature-obd-core`
Ядро OBD-II диагностики.
- **Namespace**: `com.selfservice.feature.obd.core`
- **Функции**: Протоколы ELM327, ISO-TP, UDS, менеджер соединений, PID/DTC каталоги
- **Зависимости**: `:core`, `:platform-bluetooth`

#### `:feature-obd-diagnostics`
Логика диагностики и анализа ошибок.
- **Namespace**: `com.selfservice.feature.obd.diagnostics`
- **Функции**: Чтение DTC, расшифровка, рекомендации
- **Зависимости**: `:feature-obd-core`

#### `:feature-obd-elm-port`
Портированные ELM327 команды и парсеры.
- **Namespace**: `com.selfservice.feature.obd.elmport`
- **Функции**: ElmCommandCodec, PidFormulaEvaluator
- **Зависимости**: `:feature-obd-core`

#### `:feature-obd-ui`
UI компоненты для диагностики.
- **Namespace**: `com.selfservice.feature.obd.ui`
- **Функции**: Экраны выбора марки авто, сканирования, результатов
- **Зависимости**: `:feature-obd-core`, `:app`

#### `:feature-thickness`
Модуль толщиномера ЛКП.
- **Namespace**: `com.selfservice.feature.thickness`
- **Функции**: BLE-драйвер толщиномера, сетка 40-60 точек замеров, state machine
- **Зависимости**: `:core`, `:platform-bluetooth`

#### `:feature-payments`
Интеграция платёжных систем.
- **Namespace**: `com.selfservice.feature.payments`
- **Функции**: YooKassa PSP, QR-коды, webhook обработка, payment intents
- **Зависимости**: `:core`, `:platform-data`
- **Статус**: Полная реализация (Sessions 06-07, 11B, 12C)

#### `:feature-payment`
Альтернативная реализация платежей (legacy).
- **Namespace**: `com.selfservice.feature.payment`
- **Статус**: Устаревший, заменён на `:feature-payments`

#### `:feature-reports`
Генерация и отправка отчётов.
- **Namespace**: `com.selfservice.feature.reports`
- **Функции**: HTML/PDF генераторы, email/SMS доставка, хранение отчётов
- **Зависимости**: `:core`, `:platform-data`
- **Статус**: Полная реализация (Session 12, 19)

#### `:feature-kiosk-mode`
Управление режимом киоска и Device Owner.
- **Namespace**: `com.selfservice.feature.kioskmode`
- **Функции**: Device Admin, расписание рестартов, whitelist приложений
- **Донор**: рес 2 (Kiosk-Launcher)

#### `:feature-lock-control`
Управление замками выдачи устройств.
- **Namespace**: `com.selfservice.feature.lockcontrol`
- **Функции**: USB Serial управление Arduino dispencer, логирование событий
- **Статус**: Реализация Session 11

### Platform модули

#### `:platform-bluetooth`
BLE-стек и Bluetooth-утилиты.
- **Namespace**: `com.selfservice.platform.bluetooth`
- **Функции**: BleConnectionManager, BleSessionStateMachine, интеграция blessed-kotlin
- **Источники**: рес 6 (blessed-kotlin), рес 4 (Kable)
- **Зависимости**: `:feature-obd-core`

#### `:platform-bluetooth-kable`
Kable multiplatform BLE stack (Android wrapper).
- **Namespace**: `com.selfservice.platform.bluetooth.kable`
- **Директория**: `platform/bluetooth/kable-core`
- **Статус**: **Добавлен Session 4** — Android library wrapper над Kable multiplatform
- **Библиотека**: kable-core 0.27.1

#### `:platform-bluetooth-reaktive`
Reaktive reactive extensions для BLE.
- **Namespace**: `com.selfservice.platform.bluetooth.reaktive`
- **Директория**: `platform/bluetooth/reaktive`
- **Статус**: **Добавлен Session 4** — Android library wrapper над Reaktive
- **Библиотека**: reaktive 1.3.0, reaktive-coroutines-interop 1.3.0

#### `:platform-data`
Слой данных и сериализации.
- **Namespace**: `com.selfservice.platform.data`
- **Функции**: Room база данных, kotlinx-serialization, migration управление
- **Содержит**: serialization-formats (JSON, CBOR, protobuf и др.)

#### `:platform-data-supabase`
Supabase Kotlin SDK integration.
- **Namespace**: `com.selfservice.platform.data.supabase`
- **Директория**: `platform/data/supabase`
- **Статус**: **Добавлен Session 4** — Supabase BOM 2.1.4, postgrest, auth, realtime, storage, functions
- **Библиотеки**: 
  - supabase-bom 2.1.4
  - supabase-postgrest, supabase-auth, supabase-realtime, supabase-storage, supabase-functions
  - ktor-client-android 2.3.7

#### `:platform-logging`
Централизованное логирование и телеметрия.
- **Namespace**: `com.selfservice.platform.logging`
- **Функции**: DiagnosticsLogService, Supabase outbox, retention координатор

#### `:platform-background`
Фоновые задачи (WorkManager).
- **Namespace**: `com.selfservice.platform.background`
- **Функции**: LogCleanupWorker, SessionTimeoutWorker, LockMonitorWorker, HeartbeatWorker

#### `:platform-ui`
UI-компоненты и дизайн-система.
- **Namespace**: `com.selfservice.platform.ui`
- **Функции**: Jetpack Compose экраны, навигация, ViewModels, state management
- **Содержит**: web/ (Node.js агент), flowext/ (FlowExt library)

#### `:platform-ui-flowext`
FlowExt multiplatform reactive extensions.
- **Namespace**: `com.selfservice.platform.ui.flowext`
- **Директория**: `platform/ui/flowext`
- **Статус**: **Добавлен Session 4** — Kotlin Multiplatform библиотека для Flow операторов
- **Библиотека**: FlowExt 1.0.0
- **Источники**: Multiplatform проект с jvm, js, wasm, native targets

## Зависимости между модулями

```
┌──────────────────────────────────────────────────┐
│                     :app                         │
└──────────────────────────────────────────────────┘
         │
         ├─────────────┬─────────────┬──────────────┬────────────────┬──────────────┐
         ↓             ↓             ↓              ↓                ↓              ↓
  ┌──────────┐  ┌──────────┐  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
  │ feature- │  │ feature- │  │ feature- │   │ feature- │   │ feature- │   │ feature- │
  │ obd-ui   │  │thickness │  │ payments │   │ reports  │   │kiosk-mode│   │   lock-  │
  │          │  │          │  │          │   │          │   │          │   │  control │
  └──────────┘  └──────────┘  └──────────┘   └──────────┘   └──────────┘   └──────────┘
         │             │             │              │
         ↓             ↓             ↓              ↓
  ┌──────────────────────────────────────────────────┐
  │           feature-obd-core                       │
  │   (diagnostics, elm-port)                        │
  └──────────────────────────────────────────────────┘
         │
         ├──────────────────┬────────────────┬────────────────┬─────────────┐
         ↓                  ↓                ↓                ↓             ↓
  ┌──────────┐       ┌──────────┐    ┌──────────┐    ┌──────────┐  ┌──────────┐
  │platform- │       │platform- │    │platform- │    │platform- │  │platform- │
  │bluetooth │       │  data    │    │ logging  │    │background│  │   ui     │
  └──────────┘       └──────────┘    └──────────┘    └──────────┘  └──────────┘
         │                  │
         ├──────────┬───────┼────────────┐
         ↓          ↓       ↓            ↓
   ┌──────────┐┌──────────┐┌──────────┐ ┌──────────┐
   │bluetooth-││bluetooth-││  data-   │ │   ui-    │
   │  kable   ││ reaktive ││ supabase │ │ flowext  │
   └──────────┘└──────────┘└──────────┘ └──────────┘
                              (Session 4)
```

## Донорские модули (рес 1–рес 7)

### Статус интеграции

| Донор | Каталог | Целевые модули | Статус Session 4 |
|-------|---------|----------------|-------------------|
| рес 1 | QRCode-Kotlin | `platform/camera` | Не начато |
| рес 2 | Kiosk-Launcher | `feature-kiosk-mode`, `app` | Модуль зарегистрирован, требуется интеграция |
| рес 3 | KasirPraktis | `feature-payments`, `platform-payments` | Модуль зарегистрирован |
| рес 4 | Kable | `platform-bluetooth-kable` | **✅ Модуль создан**, Android wrapper, build.gradle.kts |
| рес 5 | Compose Multiplatform | `app`, `platform-ui` | Не начато |
| рес 6 | blessed-kotlin | `platform-bluetooth` | ✅ Интегрирован (Session 06-07) |
| рес 7 | Android OBD Library | `feature-obd-core`, `feature-obd-diagnostics` | ✅ Интегрирован (Session 11) |

### Политика интеграции (Session 4)

- Донорские каталоги **не редактируются** напрямую
- Код переносится в `android/` модули через robocopy/git mv
- После переноса обновляется статус в `session-05-archive-plan.ps1`
- Тесты переносятся вместе с кодом
- Метрики APK фиксируются после каждой интеграции

## Каталог зависимостей (libs.versions.toml)

### Версии (Session 4 updates)

```toml
[versions]
androidGradlePlugin = "8.4.1"
kotlin = "1.9.24"
coroutines = "1.9.0"

# Session 4: Supabase
supabase = "2.1.4"
ktor = "2.3.7"

# Session 4: BLE/Reactive
kable = "0.27.1"
reaktive = "1.3.0"
flowext = "1.0.0"
```

### Библиотеки (Session 4 additions)

```toml
# Supabase
supabase-bom = { module = "io.github.jan-tennert.supabase:bom", version.ref = "supabase" }
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt" }
supabase-auth = { module = "io.github.jan-tennert.supabase:gotrue-kt" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt" }
supabase-storage = { module = "io.github.jan-tennert.supabase:storage-kt" }
supabase-functions = { module = "io.github.jan-tennert.supabase:functions-kt" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }

# BLE/Reactive
kable-core = { module = "com.juul.kable:core", version.ref = "kable" }
reaktive-core = { module = "com.badoo.reaktive:reaktive", version.ref = "reaktive" }
reaktive-coroutines-interop = { module = "com.badoo.reaktive:coroutines-interop", version.ref = "reaktive" }
flowext = { module = "io.github.hoc081098:FlowExt", version.ref = "flowext" }
```

## Конфигурация окружения

### APP_MODE
- `DEV`: Кнопки "Пропустить", моки устройств/платежей
- `QA`: Тестовые PSP, реальные устройства
- `PROD`: Без кнопок "Пропустить", боевые конфигурации

### Платформенные сервисы

#### Supabase (Session 4)
- **URL**: `https://ddaunoxyguqiejrjtwsf.supabase.co`
- **Project Ref**: `ddaunoxyguqiejrjtwsf`
- **Region**: AWS US East 1
- **Конфигурация**: `09-docs/02-application/security/credential-inventory.md`
- **Миграции**: `05-integrations/02-application/supabase/migrations/`

#### Arduino Dispencer
- **Порт**: COM3 (Windows) / /dev/ttyACM0 (Linux)
- **Baud Rate**: 9600
- **Команды**: OPEN_THICKNESS, OPEN_OBD, CLOSE_*, STATUS, PING

## Тестирование

### Unit тесты
- Расположение: `src/test/` в каждом модуле
- Инструменты: JUnit4, MockK, Robolectric
- Команда: `./gradlew :module:testDebugUnitTest`

### Instrumented тесты
- Расположение: `src/androidTest/` в app
- Инструменты: Espresso, Compose UI Test
- Команда: `./gradlew :app:connectedDebugAndroidTest`

### Целевое покрытие
- Unit тесты: ≥70% для feature/platform
- Instrumented: ≥40% для UI flows

## Сборка

### Команды
```bash
cd android
./gradlew clean
./gradlew lint detekt
./gradlew test
./gradlew assembleDebug
```

### Ожидаемые метрики (Session 10+)
- APK размер: ≥60 MB (с офлайн данными, моделями, нативными библиотеками)
- Lint: 0 errors
- Тесты: 100+ unit, 20+ instrumented

### Известные блокеры (Session 4)
⚠️ **AGP 8.4.1 блокер**: Google Maven недоступен (dl.google.com заблокирован)
- Зеркала: maven.aliyun.com/repository/google настроены
- Статус: Требуется whitelist или прямой доступ к maven
- Стратегия: GitHub Actions с hosted runners (см. `.github/workflows/android-build.yml`)

## Roadmap

### Session 4 (Текущая)
- ✅ Регистрация модулей kable, reaktive, flowext, supabase
- ✅ Обновление libs.versions.toml
- ⏳ Интеграция feature-kiosk-mode
- ⏳ Интеграция DI для feature-payments
- ⏳ Документация

### Следующие сессии
- Session 5-10: Интеграция рес 1, рес 2, рес 3, рес 5
- Session 11-20: BLE/OBD полировка, тесты, UI screens
- Session 21-30: Платежи, отчёты, edge cache, offline sync

## Ссылки
- План 80 сессий: `plan-80-session-roadmap.md`
- Инструкции: `.github/instructions/instructions.instructions.md`
- Планы: `plan-obd-base-integration.md`, `plan-payments-reports.md`
- Секреты: `09-docs/02-application/security/credential-inventory.md`
