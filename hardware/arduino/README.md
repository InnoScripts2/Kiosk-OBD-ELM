# Аппаратные компоненты Arduino

Модуль содержит прошивки для управления аппаратными компонентами киоска (замки, реле, датчики).

## Родительский модуль
`android/` — корень Android-монорепозитория

## Файлы

### dispencer.ino / dispenser.ino
Прошивка для управления механизмом выдачи устройств (толщиномер, OBD-адаптер).

**Функциональность**:
- Управление замками через Serial (9600 baud)
- Команды: `OPEN_THICKNESS`, `OPEN_OBD`, `CLOSE_*`, `STATUS`, `PING`
- Безопасность: watchdog (60s), auto-close (10s), таймауты (5s)
- Обратная связь через датчики положения

**Подключение**:
- Arduino Uno/Mega подключается к компьютеру киоска через USB
- Serial порт: 9600 baud, 8N1
- Управляется через `LockController` из Node-агента (`03-apps/02-application/kiosk-shell/agent`)

## Сборка

### Через Arduino IDE
1. Открыть файл `dispenser.ino` в Arduino IDE
2. Выбрать плату: Tools → Board → Arduino Uno (или Mega)
3. Выбрать порт: Tools → Port → (ваш COM-порт)
4. Нажать Upload

### Через Arduino CLI (из Gradle)
```bash
# Компиляция
arduino-cli compile --fqbn arduino:avr:uno android/hardware/arduino/dispenser.ino

# Прошивка
arduino-cli upload -p /dev/ttyUSB0 --fqbn arduino:avr:uno android/hardware/arduino/dispenser.ino
```

## Gradle интеграция

В будущих версиях планируется добавить Gradle-таску для автоматической компиляции и прошивки:

```kotlin
// android/build.gradle.kts
tasks.register<Exec>("compileArduino") {
    commandLine("arduino-cli", "compile", 
        "--fqbn", "arduino:avr:uno",
        "hardware/arduino/dispenser.ino")
}
```

## Схема подключения

Детальная схема пинов и подключения реле/замков — см. `ARDUINO_DISPENCER_README.md` в корне `android/`.

## Протокол связи

**Формат команды**: `<COMMAND>\n`  
**Формат ответа**: `OK:<status>` или `ERROR:<message>`

**Примеры**:
```
> OPEN_THICKNESS
< OK:opened

> STATUS
< OK:thickness=open,obd=closed

> CLOSE_THICKNESS
< OK:closed
```

## Тестирование

Для тестирования без физического оборудования используйте mock LockController в Node-агенте (флаг `DEVICE_MOCK_LOCK=true`).

## 5. CI интеграция

### Gradle-таска compileArduino

Создана Gradle-таска для автоматизации сборки прошивок из корня репозитория:

```bash
# Dry-run режим (по умолчанию, безопасно для локальной работы)
./gradlew compileArduino

# Реальная компиляция (требует arduino-cli в PATH)
./gradlew compileArduino -ParduinoDryRun=false

# Настройка FQBN для других плат (например, Arduino Mega)
./gradlew compileArduino -ParduinoDryRun=false -Pfqbn=arduino:avr:mega

# Указание пути к arduino-cli (если не в PATH)
./gradlew compileArduino -ParduinoDryRun=false -ParduinoCliPath=/usr/local/bin/arduino-cli

# Компиляция другого sketch
./gradlew compileArduino -ParduinoDryRun=false -PsketchPath=hardware/arduino/dispencer.ino
```

### GitHub Actions Workflow

Создан workflow `.github/workflows/hardware-arduino.yml` для еженедельной проверки компилируемости прошивок:

- **Расписание**: каждую пятницу в 10:00 UTC
- **Ручной запуск**: через workflow_dispatch с параметрами (dry-run, fqbn)
- **Шаги**:
  1. Checkout кода
  2. Установка Arduino CLI
  3. Установка платформ и библиотек (arduino:avr)
  4. Компиляция всех sketch в `hardware/arduino/`
  5. Upload артефактов (hex файлы, build logs)
  6. Генерация job summary с результатами

### Параметры таски

| Параметр          | Описание                                          | По умолчанию                  |
| ----------------- | ------------------------------------------------- | ----------------------------- |
| `arduinoDryRun`   | Режим dry-run (true) или реальная компиляция (false) | `true`                        |
| `arduinoCliPath`  | Путь к исполняемому файлу arduino-cli             | `arduino-cli` (из PATH)       |
| `fqbn`            | Fully Qualified Board Name                        | `arduino:avr:uno`             |
| `sketchPath`      | Путь к sketch относительно корня репозитория      | `hardware/arduino/dispenser.ino` |

### Требования

1. **Локально**: Установить [Arduino CLI](https://arduino.github.io/arduino-cli/)
2. **CI**: Workflow устанавливает arduino-cli автоматически
3. **Платформы**: `arduino:avr` (устанавливается через `arduino-cli core install`)
4. **Библиотеки**: Пока нет внешних зависимостей (все встроенные)

### Логирование

Все операции компиляции логируются:
- **Dry-run**: Выводится список параметров без реальной компиляции
- **Реальная**: Полный вывод arduino-cli в stdout
- **Ошибки**: Передаются через exit code (≠ 0)

Лог-файлы сохраняются в `hardware/arduino/build/` (локально) или загружаются как artifacts (CI).

## История изменений

- **24.11.2025**: Перенос из корня `android/` в `android/hardware/arduino/` (Session 15G)
- **23.11.2025**: Создание прошивки в рамках Session 09 (описание в `ARDUINO_DISPENCER_README.md`)

## Лицензия

См. корневой LICENSE файл проекта.
