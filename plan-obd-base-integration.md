# План подключения базы OBD-протоколов и кодов ошибок

## Цели
- Консолидировать все исходники из `android/base` (dtcmapping, каталоги производителей, библиотека `OBDII.DTC`, шпаргалки протоколов) в единую воспроизводимую базу данных.
- Встроить сформированную базу в Android-стек (`platform/data`, `feature-obd-core`, runtime PassThru) без ручного копирования.
- Синхронизировать новый фронтенд-дизайн киоска с кодовой базой, обеспечить единый источник truth для UI.

## Области работ
1. **Data Ingestion Engine**
   - [ ] Создать Python-утилиту `tools/dtc/build_catalog.py`, которая:
     - парсит `base/OBDII.DTC-main/DTC.cs` для системных описаний SAE/ISO;
     - агрегирует `dtcmapping.json` для валидации и обратной совместимости;
     - разбирает текстовые справочники производителей (`All * OBD2 Codes List`, `Generic ...`).
   - [ ] Определить формат выходного артефакта (`dtc_catalog.json`, `manufacturer_catalog.json`).
   - [ ] Добавить unit-тесты на парсинг (минимум по одному сэмплу на систему/производителя).
2. **Android Integration**
   - [ ] Расширить `platform/data` так, чтобы `./gradlew :platform:data:generateDtcCatalog` вызывал новую утилиту.
   - [ ] Обновить `DtcCatalog` и `ManufacturerDtcProvider`, чтобы они потребляли новые артефакты.
   - [ ] Добавить телеметрию о версии каталога в `PassThruDiagnosticsRuntime` и UI.
3. **Frontend Synchronization**
   - [ ] Скопировать свежий дизайн из `android/kiosk-frontend` в моно-репозиторий (`apps/kiosk-frontend`).
   - [ ] Настроить единый pipeline (pnpm + Vite) с линтами/Playwright.
   - [ ] Привести UI к финальному виду: тема, типографика, responsive-сетки, dev-mode панель.
4. **Quality & Release**
   - [ ] Автоматические проверки (Gradle unit+instrumented, Playwright smoke, Python lint/tests).
   - [ ] Документация: README в `tools/dtc`, обновлённый `platform/data/README.md`, onboarding-гайд для фронтенда.
   - [ ] Release checklist (версия каталога, обновление app assets, changelog для киоска).

## Вехи
| Неделя | Результат                                                                                  |
| ------ | ------------------------------------------------------------------------------------------ |
| W1     | Утилита парсинга + тестовые артефакты (`dtc_catalog.json`, `manufacturer_catalog.json`).   |
| W2     | Android билд подтягивает новые каталоги, PassThru runtime показывает расширенные описания. |
| W3     | Обновлённый фронтенд в `apps/kiosk-frontend`, настроены e2e тесты.                         |
| W4     | Интеграция завершена, документация и релизные артефакты готовы.                            |

## Риски и меры
- **Неструктурированный текст**: использовать эвристики + fallback в «raw» описания, добавлять логи.
- **Размер артефактов**: включить дедупликацию, gzip на Gradle таске, lazy-индексацию.
- **Фронтенд зависимостей**: фиксировать версии в `package-lock.json`, добавить renovate job.

## Статус интеграции BLE/OBD компонентов (Session 6)

### Перенесённые компоненты
1. **blessed-kotlin** (рес 6) → `android/platform/bluetooth/blessed/`
   - BluetoothCentralManager (1,266 строк) - менеджер BLE-соединений
   - BluetoothPeripheral (2,084 строк) - представление устройства
   - BluetoothBytesParser (118 строк) - парсинг байтовых данных
   - BluetoothBytesBuilder (192 строк) - построение байтовых пакетов
   - Вспомогательные классы: GattStatus, ConnectionState, ScanMode и др.
   - **Всего**: 25 основных файлов, ~5,800 строк кода

2. **Kable** (рес 4) → `android/platform/bluetooth/kable-core/`
   - Multiplatform BLE stack (jvmMain, androidMain, commonMain)
   - BtleplugPeripheral, BtleplugScanner - JVM-специфичная реализация
   - Flow-based API для корутин
   - **Всего**: 207 файлов, ~18,000 строк кода (скопированы, ожидают адаптации)

### Созданные интеграционные слои
1. **BleConnectionManager** - обёртка над blessed с Flow API
   - Управление сканированием и подключением
   - StateFlow для реактивного UI
   - Список обнаруженных устройств

2. **ObdBleAdapter** - специализированный адаптер для OBD-II
   - Отправка команд через BLE характеристики
   - Парсинг OBD ответов
   - Интеграция с feature-obd-core (планируется)

3. **Unit-тесты** - базовое покрытие моделей данных
   - BleConnectionStateTest
   - BleDeviceTest
   - ObdDataTest

### Следующие шаги
1. ~~Реализовать утилиту `build_catalog.py` + минимальные тесты.~~
2. ~~Настроить Gradle-таску генерации данных и обновить assets.~~
3. ~~Интегрировать BleConnectionManager с ObdConnectionManager в feature-obd-core~~
4. ~~Адаптировать Kable компоненты для Android (выбрать androidMain/jvmMain)~~
5. ~~Добавить DI (Hilt/Koin) для BLE менеджеров~~
6. **[ЗАВЕРШЕНО Session 07 - 23.11.2025]** Создан интеграционный слой:
   - ✅ BlessedBleScanner (103 строки) - нативная реализация на blessed-kotlin с Flow API
   - ✅ BlessedBleScannerAdapter (43 строки) - мост между platform и feature-obd-core
   - ✅ Обновлены зависимости: platform-bluetooth → feature-obd-core (api dependency)
   - ✅ Добавлены unit-тесты для BleScannerIntegration (6 тестов, 95 строк)
   - ✅ blessed-kotlin интегрирован через sourceSets в build.gradle.kts
   - ✅ Исправлена передача rssi через BleDevice (rssi теперь в device, не в result)
   - ✅ Локализация DTC/PID проверена и подтверждена (все файлы на русском)
   - **Квота**: Вариант А (6/100 файлов, ~300/30,000 строк)
7. **[TODO Session 08+]** DI интеграция и тестирование:
   - Добавить Koin/Hilt модули для BlessedBleScannerAdapter
   - Интегрировать с ObdConnectionManager
   - E2E тесты для BLE сканирования
8. **[Session 4 - 26.11.2025]** Регистрация платформенных модулей:
   - ✅ Добавлен :platform-bluetooth-kable (platform/bluetooth/kable-core)
   - ✅ Добавлен :platform-bluetooth-reaktive (platform/bluetooth/reaktive)
   - ✅ Созданы build.gradle.kts для kable и reaktive (минимальные Android library wrappers)
   - ✅ Обновлен libs.versions.toml: kable 0.27.1, reaktive 1.3.0
   - ⏳ TODO: Интеграция kable/reaktive API в feature-obd-core
   - ⏳ TODO: Замена существующего BLE кода на unified API
9. Реплицировать `kiosk-frontend` в `apps/` и начать полировку UI.

## Статус интеграции Thickness компонентов (Session 12B + 13B)

### Миграция толщиномера Node → Kotlin

**Session 12B (24.11.2025)**: Базовая миграция (22 файла, ~4800 строк, 123 теста)
- ✅ **models/** - Модели данных
  - ThicknessZoneModels.kt - 60 зон измерений (hood, roof, trunk, doors, fenders, bumpers)
  - ThicknessDeviceConfig.kt - таймауты (connect=5s, measure=30s, scan=10s)
  - ThicknessMeasurementModels.kt - CSV/JSON export, statistics, heatmap
  
- ✅ **ble/** - BLE интеграция с platform/bluetooth
  - ThicknessBleAdapter.kt - интеграция через BlessedBleScanner + BleConnectionManager
  - Таймауты: connection=5s, measurement=30s per point, scan=10s
  - Reconnect с exponential backoff (2s → 4s → 8s → 16s max)
  
- ✅ **protocol/** - Протокол обмена данными
  - ThicknessProtocolParser.kt - ASCII/Binary парсинг, auto-detection
  - Валидация диапазона 0-2000μm
  
- ✅ **mock/** - DEV мок-устройство
  - MockThicknessDevice.kt - явная пометка [MOCK MODE]
  - Реалистичные данные (60% factory, 25% repaint, 10% major, 5% bodywork)
  
- ✅ **workflow/** - Оркестрация процесса
  - ThicknessWorkflow.kt - полный цикл измерений
  - State machine: Idle → Connecting → Ready → Measuring → Completed → Error
  
- ✅ **utils/** - Утилиты
  - ThicknessValidation.kt - валидация измерений
  - ThicknessFormatter.kt - HTML генератор, локализация
  
- ✅ **di/** - Dependency Injection
  - ThicknessModule.kt - Hilt-ready, DEV/QA/PROD modes

**Session 13B (24.11.2025)**: Completion (5 файлов, ~1530 строк, 47 тестов)
- ✅ **exceptions/** - Специализированные исключения
  - ThicknessConnectionException - ошибки BLE подключения (timeout, refused, disconnected)
  - MeasurementTimeoutException - таймауты измерений с зональным контекстом
  - ThicknessProtocolException - ошибки парсинга данных
  - ThicknessValueOutOfRangeException - значения вне диапазона (negative, infinite, NaN)
  - MaxReconnectAttemptsExceededException - превышение лимита переподключений
  - BleStackException - ошибки BLE-стека
  
- ✅ **api/** - UI API интерфейсы
  - ThicknessController - управление процессом измерений (StateFlow API)
  - ThicknessProgressObserver - наблюдение за прогрессом
  - ThicknessConfiguration - конфигурирование
  - ThicknessReconnectStrategy - стратегия переподключения (exponential backoff)
  - ThicknessMeasurementValidator - валидация измерений
  - ThicknessDataExporter - экспорт CSV/JSON/HTML/PDF
  - ThicknessMeasurementCache - кэширование результатов
  
- ✅ **tests/** - Comprehensive тестирование
  - ThicknessExceptionsTest.kt - 27 тестов для exceptions
  - ThicknessBleAdapterTest.kt - 20 тестов с Mock BLE (Mockito)

### Итоговые метрики

| Категория | Файлов | Строк | Тестов |
|-----------|--------|-------|--------|
| Session 12B | 22 | ~4800 | 123 |
| Session 13B | 5 | ~1530 | 47 |
| **Итого** | **27** | **~6330** | **170** |

### Интеграция с platform/bluetooth

```
platform/bluetooth/           # Session 11C
  ├── BlessedBleScanner      # Нативный сканер BLE устройств
  ├── BleConnectionManager   # Менеджер подключений
  └── BlessedBleConnectionManager # Реализация на blessed-kotlin

feature-thickness/            # Session 12B + 13B
  ├── ble/
  │   └── ThicknessBleAdapter ← использует platform/bluetooth API
  ├── exceptions/             # Специализированные exceptions
  ├── api/                    # UI-facing интерфейсы
  ├── models/                 # Модели данных
  ├── protocol/               # Протокол обмена
  ├── mock/                   # DEV мок
  ├── workflow/               # Оркестрация
  └── di/                     # DI модуль
```

### Следующие шаги

1. ~~Базовая миграция толщиномера~~ ✅ Session 12B
2. ~~Exception classes и comprehensive tests~~ ✅ Session 13B
3. ~~UI API интерфейсы~~ ✅ Session 13B
4. Session 14: Интеграция с ViewModels
5. Session 15+: UI экраны для толщиномера
