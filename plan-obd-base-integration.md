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
8. Реплицировать `kiosk-frontend` в `apps/` и начать полировку UI.
