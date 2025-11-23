# Интеграция донорских проектов

Документация о переносе кода из донорских проектов (рес 1-7) в Android-монорепозиторий.

## Правила интеграции

1. **Только полные версии**: копировать код точь-в-точь с адаптацией пакетов или полностью воссоздавать с улучшениями. Никаких упрощений.
2. **Только чтение доноров**: каталоги рес 1-7 не редактируются, только читаются как источник.
3. **Минимальный объём**: ≥2 компонента или ≥5 файлов ≥400 строк + тесты за сессию.
4. **Все зависимости сразу**: при переносе добавляются все необходимые библиотеки, тесты и документация.
5. **Никаких моков**: производственные устройства и платежи не имитируются в production-коде.

## Выполненные этапы

### Этап 1: рес 7 (Android OBD) → feature-obd-core ✅

**Донор:** `рес 7/рес 7/obd` (Apache License 2.0)  
**Источник:** github.com/pnuema/android-obd-library

**Скопировано:**
- `enums/ObdModes.kt` — режимы OBD (MODE_01..MODE_0A)
- `enums/ObdProtocols.kt` — протоколы OBD (SAE J1850, ISO 15765-4 CAN и т.д.)
- `models/DTC.kt`, `models/DTCS.kt` — модели кодов неисправностей
- `models/PID.kt`, `models/PIDS.kt` — модели параметров идентификации
- `utils/PIDUtils.kt` — утилиты работы с PID (чтение из JSON, кэш)
- `utils/DTCUtils.kt` — утилиты работы с DTC (чтение из JSON, кэш)
- `utils/FileUtils.kt` — утилиты чтения файлов из assets
- `assets/dtc-codes.json` (285KB) — словарь DTC кодов
- `assets/pids-mode1.json` (22KB) — словарь PID для режима 01
- `assets/pids-mode4.json` (123B) — словарь PID для режима 04
- `assets/pids-mode9.json` (496B) — словарь PID для режима 09

**Адаптации:**
- Пакет изменён с `com.pnuema.android.obd` на `com.selfservice.obd.core`
- Удалена зависимость от `ObdLibrary` singleton, utils работают через `AssetManager`
- Удалена зависимость от `PersistentStorage`, кэш реализован через `SparseArray` в памяти
- Комментарии переведены на русский язык
- Добавлены методы `clearCache()` для тестирования
- Добавлен plugin `kotlin-serialization` в `build.gradle.kts`
- Модуль зарегистрирован в `settings.gradle.kts`

**Тесты:**
- `ObdModesTest.kt` — тесты режимов OBD
- `ObdProtocolsTest.kt` — тесты протоколов
- `DTCTest.kt` — тесты модели DTC
- `PIDTest.kt` — тесты модели PID

**Итого:** 8 файлов кода + 4 теста + 4 JSON файла = 16 файлов, ~700 строк кода

---

### Этап 2: рес 6 (blessed-kotlin) → platform/bluetooth ✅

**Донор:** `рес 6/рес 6/blessed` (MIT License)  
**Источник:** github.com/weliem/blessed-android

**Скопировано (ПОЛНЫЕ версии):**
- `ByteArrayExtensions.kt` (393 строки) — расширения ByteArray для BLE:
  - Чтение/запись Int8, UInt8, Int16, UInt16, Int24, UInt24, Int32, UInt32, Int48, UInt48, Int64, UInt64
  - Чтение/запись SFloat, Float (IEEE 11073 форматы)
  - Чтение/запись DateTime
  - Преобразование в hex-строки
  - Конвертация числовых типов в ByteArray
  - Merge массивов
  - UUID из 16-битных строк
- `BluetoothBytesParser.kt` (133 строки) — парсер для последовательного чтения BLE данных:
  - Поддержка всех типов из ByteArrayExtensions
  - Автоматический сдвиг offset
  - Поддержка Big Endian и Little Endian
  - Чтение строк с null-termination

**Адаптации:**
- Пакет изменён с `com.welie.blessed` на `com.selfservice.platform.bluetooth`
- Сохранена полная функциональность без упрощений
- Все 393 строки ByteArrayExtensions скопированы без изменений
- Все 133 строки BluetoothBytesParser скопированы без изменений

**Тесты:**
- `ByteArrayExtensionsTest.kt` — тесты всех функций расширений
- `BluetoothBytesParserTest.kt` — тесты парсера

**Итого:** 2 файла кода + 2 теста = 4 файла, ~826 строк донорского кода

---

## Запланированные этапы

### Этап 3: рес 4 (kable) → platform/bluetooth

**Донор:** `рес 4/рес 4/kable-core` (Apache License 2.0)  
**Планируется:**
- Интеграция kable BLE интерфейсов (Peripheral, Central, Characteristic)
- Flow-based API для BLE операций
- Поддержка multiplatform (Android/JVM)

### Этап 4: рес 2 (Kiosk-Launcher) → feature-kiosk-mode + app

**Донор:** `рес 2/рес 2/app` (MIT License предполагается)  
**Планируется:**
- `MyDeviceAdminReceiver.kt` → `feature-kiosk-mode`
- `KioskAccessibilityService.kt` → `feature-kiosk-mode`
- `BootReceiver.kt` → `feature-kiosk-mode`
- `RestartScheduler` логика → `app`
- Whitelists/intent-фильтры → `app/AndroidManifest.xml`

### Этап 5: рес 3 (KasirPraktis) → feature-payments + platform/ui

**Донор:** `рес 3/рес 3/app` (Лицензия неизвестна)  
**Планируется:**
- POS UI компоненты → `platform/ui`
- Витрина услуг → `app`
- QR-код интеграция → `feature-payments`

### Этап 6: рес 1 (qrcode-kotlin) → platform/ui + feature-payment

**Донор:** `рес 1/рес 1/src/commonMain` (Apache License 2.0)  
**Планируется:**
- `QRCode.kt`, `QRCodeBuilder.kt` → `platform/ui`
- Shape functions → `platform/ui`
- Render интерфейсы → `platform/ui`
- Интеграция в `feature-payment` для генерации платёжных QR

### Этап 7: рес 5 (Compose Multiplatform) → platform/ui + app

**Донор:** `рес 5/рес 5/examples` (Apache License 2.0)  
**Планируется:**
- UI компоненты (Button, Card, Divider) → `platform/ui`
- Adaptive layouts → `app`
- Best practices паттерны → документация

---

## Статус модулей

| Модуль | Статус | Донор | Файлов | Строк кода |
|--------|--------|-------|--------|------------|
| `feature-obd-core` | ✅ Готов | рес 7 | 16 | ~700 |
| `platform/bluetooth` | ✅ Готов | рес 6 | 4 | ~826 |
| `feature-kiosk-mode` | 🔄 Планируется | рес 2 | - | - |
| `platform/ui` | 🔄 Планируется | рес 1,3,5 | - | - |
| `feature-payment` | 🔄 Планируется | рес 1,3 | - | - |

**Легенда:** ✅ Готов | 🔄 Планируется | ⚠️ Частично

---

## Команды копирования

### Копирование файлов из донора (Windows)
```powershell
robocopy "путь\к\рес N\рес N\источник" "путь\к\android\целевой_модуль" /E /COPY:DAT /XD .git build .gradle node_modules /XF *.iml *.bat *.sh *.cmd
```

### Копирование файлов из донора (Linux/Mac)
```bash
rsync -av --exclude='.git' --exclude='build' --exclude='.gradle' --exclude='node_modules' \
  "путь/к/рес N/рес N/источник/" "путь/к/android/целевой_модуль/"
```

---

## Лицензии и атрибуции

Все донорские проекты имеют открытые лицензии:
- **рес 7 (obd)**: Apache License 2.0
- **рес 6 (blessed)**: MIT License
- **рес 4 (kable)**: Apache License 2.0
- **рес 2 (Kiosk-Launcher)**: Лицензия уточняется
- **рес 3 (KasirPraktis)**: Лицензия уточняется
- **рес 1 (qrcode-kotlin)**: Apache License 2.0
- **рес 5 (Compose Multiplatform)**: Apache License 2.0

Все файлы сохраняют оригинальные copyright notices и лицензионные заголовки.

---

## Ссылки

- [Основной план интеграции](../../../plan-80-session-roadmap.md)
- [Архитектура проекта](../../docs-unified/architecture/README.md)
- [Скрипт архивации](../../android/scripts/session-05-archive-plan.ps1)
