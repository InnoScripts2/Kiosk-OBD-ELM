# NOTES: begaz/OBDII (Dart/Flutter)

## Репозиторий

- **GitHub**: <https://github.com/begaz/OBDII>
- **Stars**: 63
- **Последнее обновление**: Feb 7, 2024
- **Язык**: Dart/Flutter
- **Лицензия**: Open Source

## Что решает

✅ Обнаружение OBD адаптера (Bluetooth поиск)
✅ Подключение к Bluetooth SPP адаптеру
✅ Фильтрация устройств по имени

## Ключевые файлы для изучения

### 1. lib/services/bluetooth_service.dart

- Полная реализация `Bluetooth.startScan()` и `Bluetooth.getNearbyDevices`
- Фильтрация устройств по имени (поиск "OBDII", "Ediag")
- Получение MAC адреса и RSSI сигнала
- Обработка ошибок сканирования и таймауты

### 2. lib/models/obd_device.dart

- Data class для OBD устройства с MAC адресом
- Структура данных для хранения информации об устройстве

### 3. lib/screens/device_selection_screen.dart

- UI паттерны (Flutter, но логика применима)
- Workflow обнаружения и выбора устройства

## Ключевые функции

```dart
// Получить все найденные устройства
List<BluetoothDevice> devices = await obd2.getNearbyDevices;

// Получить спаренные устройства
List<BluetoothDevice> paired = await obd2.getPairedDevices;

// Подключиться к устройству
obd2.getConnection(device, onSuccess, onError);

// Фильтр по имени (для Ediag)
devices.where((d) => d.name.contains("OBDII")).toList();
```

## Как использовать в Node.js проекте

1. **Bluetooth Discovery Logic**:
   - Копируй логику сканирования из `bluetooth_service.dart`
   - Адаптируй в Node.js используя `@abandonware/noble` или `bluetooth-serial-port`

2. **Device Filtering**:
   - Используй паттерн фильтрации по имени устройства
   - Добавь поддержку "OBDII", "Ediag", "Ediag Plus" (case-insensitive)

3. **Connection Management**:
   - Методы `getConnection()` с обработкой успеха/ошибки
   - Автоматическое переподключение при разрыве связи

## Специфичные моменты для Ediag

- **Имена устройств**: "OBDII", "Ediag", "Ediag Plus" (case-insensitive)
- **Bluetooth Profile**: Serial Port Profile (SPP)
- **Connection timeout**: 2000-5000ms
- **Baud rate**: 38400 или 115200 (пробовать оба)

## Преимущества этого репозитория

✅ Чистая реализация Bluetooth discovery workflow
✅ Правильная обработка таймаутов и переподключений
✅ Примеры работы с именами устройств (критично для Ediag)
✅ UI паттерны для отображения найденных устройств

## Интеграция с твоим проектом

1. Скопируй структуру `services/` в backend как модуль discovery
2. Адаптируй код из Dart в Node.js (логика одинакова, синтаксис отличается)
3. Обрати внимание на обработку BT SPP (Serial Port Profile) - это то, что использует Ediag
4. Используй примеры UI workflow для фронтенда
