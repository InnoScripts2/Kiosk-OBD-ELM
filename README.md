# Kiosk-OBD-ELM — Автосервис самообслуживания

**Версия**: 2.0  
**Дата**: 23.11.2025 (Session 1G)  
**Статус**: В активной разработке

## Обзор

Терминальный киоск самообслуживания для автодиагностики и оценки состояния лакокрасочного покрытия. Основные услуги:

1. **Толщинометрия ЛКП**: Измерение толщины лакокрасочного покрытия через BLE толщиномер (60 точек кузова)
2. **Диагностика OBD-II**: Сканирование систем автомобиля через ELM327-совместимый адаптер

## Архитектура

### Технологический стек

- **Android**: Kotlin, Jetpack Compose, Coroutines, Hilt/Dagger DI
- **Backend Agent**: Node.js, TypeScript, Express
- **Устройства**: BLE (blessed-kotlin), USB Serial (Arduino), OBD-II (ELM327)
- **Платежи**: YooKassa API, AES-256 шифрование intents
- **Отчёты**: HTML/PDF генераторы, Email/SMS delivery
- **Хранилище**: SQLite (локально), Supabase (облачная синхронизация)

### Структура монорепозитория

```
Kiosk-OBD-ELM/
├── android/                            # Android монорепо (Gradle Kotlin DSL)
│   ├── app/                            # Основное приложение
│   ├── core/                           # Общие утилиты
│   ├── feature-obd-core/               # OBD протокол (ELM327, ISO-TP)
│   ├── feature-obd-elm-port/           # ELM327 портированный код
│   ├── feature-obd-ui/                 # OBD UI компоненты
│   ├── feature-obd-diagnostics/        # Диагностические модули
│   ├── feature-thickness/              # Толщиномер BLE
│   ├── feature-payments/               # Платёжный шлюз
│   ├── feature-reports/                # Генерация отчётов
│   ├── feature-lock-control/           # Управление выдачей устройств
│   ├── platform/                       # Платформенные сервисы
│   │   ├── bluetooth/                  # BLE layer (blessed-kotlin)
│   │   ├── background/                 # WorkManager, фоновые задачи
│   │   └── ui/                         # UI kit, Material3
│   ├── scripts/                        # Утилиты и tooling
│   └── session-logs/                   # История сессий разработки
├── 03-apps/02-application/
│   └── kiosk-shell/agent/              # Node.js/TS локальный агент
│       ├── src/services/               # Arduino, Lock, Payment, Report
│       └── src/integrations/           # Supabase, Prometheus, email/SMS
├── packages/                           # Переиспользуемые NPM пакеты
│   ├── device-obd/                     # OBD драйвер (TS)
│   ├── device-thickness/               # Толщиномер драйвер (TS)
│   ├── report/                         # Генераторы отчётов (TS)
│   └── payment-mock/                   # Mock платёжного шлюза
├── docs/                               # Документация
│   ├── navigation-flow.md              # Схема UI навигации
│   ├── migration/                      # Миграция Node.js → Kotlin
│   └── reporting-guidelines.md         # Стандарты отчётов
├── logs/                               # Логи сессий и проблем
│   ├── sessions/                       # JSON логи сессий с checksums
│   ├── issues/                         # JSON логи проблем
│   └── README.md                       # Формат и процедуры
├── .env.example                        # Шаблон переменных окружения
├── plan-80-session-roadmap.md          # План 80 сессий разработки
├── plan-maintenance-backlog.md         # Список задач категории G
└── plan-testing.md                     # Чек-листы тестирования
```

## Быстрый старт

### Предварительные требования

- **Node.js**: >= 18.x, npm >= 9.x
- **Java**: JDK 17 (для Android)
- **Android SDK**: API 34+, Build Tools 34.0.0
- **Gradle**: 8.7 (wrapper включён)
- **Git**: >= 2.30

### 1. Клонирование репозитория

```bash
git clone https://github.com/InnoScripts2/Kiosk-OBD-ELM.git
cd Kiosk-OBD-ELM
```

### 2. Настройка переменных окружения

```bash
# Копировать шаблон
cp .env.example .env

# Отредактировать .env (минимум для DEV):
# APP_MODE=DEV
# PAYMENT_MOCK=true
# DEVICE_MOCK_OBD=true
# DEVICE_MOCK_THICKNESS=true
# EMAIL_PROVIDER=MOCK
# SMS_PROVIDER=MOCK
```

### 3. Установка зависимостей

#### Node.js Agent

```bash
# Установить зависимости агента
npm --prefix 03-apps/02-application/kiosk-shell/agent install

# Установить зависимости пакетов (если нужны)
npm --prefix packages/device-obd install
npm --prefix packages/device-thickness install
npm --prefix packages/report install
```

#### Android (когда AGP 8.4.1 станет доступен)

```bash
cd android
./gradlew clean build
```

**Текущий блокер**: AGP 8.4.1 недоступен через Google Maven и зеркала. См. `logs/issues/2025-11-23-agp-blocker.json`.

### 4. Запуск локального агента

```bash
# DEV-режим
npm --prefix 03-apps/02-application/kiosk-shell/agent start

# Агент запускается на http://localhost:3000
# Доступные эндпоинты:
# - GET  /api/status          — статус агента
# - POST /api/arduino/command — команды Arduino
# - POST /api/lock/open       — выдача устройств
# - POST /api/payments/intent — создание платежа
# - POST /api/reports/generate — генерация отчёта
```

### 5. Запуск Android приложения (когда доступно)

```bash
cd android

# Сборка debug APK
./gradlew :app:assembleDebug

# Установка на эмулятор/устройство
./gradlew :app:installDebug

# Запуск приложения
adb shell am start -n com.selfservice.kiosk/.MainActivity
```

## Разработка

### Запуск тестов

#### Node.js

```bash
# Все тесты
npm --prefix 03-apps/02-application/kiosk-shell/agent test

# С coverage
npm --prefix 03-apps/02-application/kiosk-shell/agent test -- --coverage

# Линтинг
npm --prefix 03-apps/02-application/kiosk-shell/agent run lint

# Исправление lint ошибок
npm --prefix 03-apps/02-application/kiosk-shell/agent run lint:fix
```

#### Android (когда AGP 8.4.1 доступен)

```bash
cd android

# Unit-тесты
./gradlew test

# Unit-тесты конкретного модуля
./gradlew :feature-obd-core:testDebugUnitTest

# Instrumented тесты (требуется эмулятор/устройство)
./gradlew connectedDebugAndroidTest

# Линтинг
./gradlew lint

# Detekt (статический анализ)
./gradlew detekt
```

### DEV-режим и моки

**DEV-режим** включается через `APP_MODE=DEV` в `.env`. В этом режиме:

- ✅ Кнопка **"Пропустить"** доступна на всех экранах (правый верхний угол)
- ✅ **Mock устройства**: толщиномер генерирует случайные значения 100-150 мкм, OBD возвращает 2-3 тестовых DTC
- ✅ **Mock платежи**: имитация QR-оплаты через кнопку "Имитация оплаты"
- ✅ **Mock отчёты**: email/SMS не отправляются, сохраняются локально в `logs/reports/`
- ✅ **Детальное логирование**: `LOG_LEVEL=DEBUG`

**Важно**: В production (`APP_MODE=PROD`) все моки и кнопка "Пропустить" полностью отключены (не компилируются).

### Переменные окружения

Полный список переменных см. в `.env.example`. Ключевые:

| Переменная              | Описание                                              | DEV значение | PROD значение                      |
| ----------------------- | ----------------------------------------------------- | ------------ | ---------------------------------- |
| `APP_MODE`              | Режим приложения (DEV/QA/PROD)                        | `DEV`        | `PROD`                             |
| `PAYMENT_MOCK`          | Имитация платежей                                     | `true`       | `false`                            |
| `DEVICE_MOCK_OBD`       | Mock OBD-адаптера                                     | `true`       | `false`                            |
| `DEVICE_MOCK_THICKNESS` | Mock толщиномера                                      | `true`       | `false`                            |
| `ARDUINO_PORT`          | COM-порт Arduino (Windows: COM3, Linux: /dev/ttyACM0) | `COM3`       | `/dev/ttyACM0`                     |
| `YOOKASSA_SHOP_ID`      | YooKassa Shop ID                                      | -            | `your_shop_id_here`                |
| `YOOKASSA_SECRET_KEY`   | YooKassa Secret Key                                   | -            | `your_secret_key_here`             |
| `SUPABASE_URL`          | Supabase Project URL                                  | -            | `https://your_project.supabase.co` |
| `EMAIL_PROVIDER`        | Email провайдер (MOCK/SENDGRID/SMTP)                  | `MOCK`       | `SENDGRID`                         |
| `SMS_PROVIDER`          | SMS провайдер (MOCK/TWILIO/SMSAERO)                   | `MOCK`       | `TWILIO`                           |

### Supabase UI и офлайн режим

- **Основной интерфейс** теперь ожидается в Supabase Storage: загрузите собранный фронтенд в бакет `kiosk-ui` и опубликуйте `index.html` по адресу `https://ddaunoxyguqiejrjtwsf.supabase.co/storage/v1/object/public/kiosk-ui/index.html`.
- **Fallback**: если Supabase недоступен, WebView автоматически переключится на локальный файл `android_asset/html/offline.html`, отображающий инструкции оператору.
- **Жест оператора** (три одновременных касания) по-прежнему позволяет вручную задать иной URL и перезапустить загрузку.
- **Диагностика WebView**: используйте расширение **LogcatWin, ADB interface for VSCode** (или `adb logcat`) для поиска записей `Switching kiosk UI URL...` и HTTP ошибок при отладке.
- **Настройка Supabase**: подробный гайд и команды CLI находятся в `09-docs/02-application/plans/supabase-setup.md`.

## Текущий статус (Session 1G, 23.11.2025)

### Завершено ✅

- **Session 01–10**: Архитектурное ядро, BLE/OBD фундамент
- **Session 10B**: blessed-kotlin API исправления, Timber интеграция
- **Session 10C**: Memory leaks исправления (scope.cancel())
- **Session 11**: Миграция Node.js → Kotlin (feature-lock-control)
- **Session 12**: Полная реализация ReportService на Kotlin
- **Session 1G**: Документация, логи с checksums, .env.example

### В работе 🚧

- **Session 11B**: BLE/OBD интеграция (ожидание AGP 8.4.1)
- **Session 2G**: UI полировка и локализация
- **Sessions 20–23**: Толщиномер workflow, платёжный шлюз, MDM/OTA

### Блокеры ⚠️

1. **AGP 8.4.1 недоступен** (критический)
   - **Воздействие**: Блокирует компиляцию всех Android модулей
   - **Обход**: Code review без сборки APK, тестирование Node.js компонентов
   - **Детали**: `logs/issues/2025-11-23-agp-blocker.json`

2. **Maven зеркала неполные** (высокий)
   - **Воздействие**: Некоторые зависимости недоступны
   - **Обход**: JitPack для сторонних библиотек

3. **Отсутствие тестовых устройств** (средний)
   - **Воздействие**: Невозможность field-тестов
   - **Обход**: DEV-моки
   - **Резолюция**: Session 60+ (приобретение оборудования)

## Документация

- **Навигация UI**: `docs/navigation-flow.md`
- **Миграция агента**: `docs/migration/agent-to-kotlin.md`
- **Reporting guidelines**: `docs/reporting-guidelines.md`
- **BLE/OBD интеграция**: `android/BLE_OBD_INTEGRATION_GUIDE.md`
- **План 80 сессий**: `plan-80-session-roadmap.md`
- **План тестирования**: `plan-testing.md`
- **Maintenance backlog**: `plan-maintenance-backlog.md`
- **Структура логов**: `logs/README.md`

## Контрибьютинг

Проект разрабатывается исключительно через GitHub Copilot по поручениям владельца. Внешние Pull Requests не принимаются.

## Лицензия

UNLICENSED - Проприетарное ПО InnoScripts2

## Контакты

- **GitHub**: https://github.com/InnoScripts2/Kiosk-OBD-ELM
- **Issues**: https://github.com/InnoScripts2/Kiosk-OBD-ELM/issues

---

**Обновлено**: 23.11.2025 (Session 1G)  
**Готовность проекта**: 60% (Session 11 → Session 12 → Session 1G)

