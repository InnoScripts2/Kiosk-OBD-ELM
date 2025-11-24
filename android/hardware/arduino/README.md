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

## История изменений

- **24.11.2025**: Перенос из корня `android/` в `android/hardware/arduino/` (Session 15G)
- **23.11.2025**: Создание прошивки в рамках Session 09 (описание в `ARDUINO_DISPENCER_README.md`)

## Лицензия

См. корневой LICENSE файл проекта.
