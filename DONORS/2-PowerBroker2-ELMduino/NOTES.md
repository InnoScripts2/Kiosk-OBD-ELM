# NOTES: PowerBroker2/ELMduino (C++/Arduino)

## Репозиторий

- **GitHub**: <https://github.com/PowerBroker2/ELMduino>
- **Stars**: 765
- **Forks**: 133
- **Последнее обновление**: Apr 2, 2025
- **Язык**: C++ (Arduino compatible)
- **Лицензия**: MIT

## Что решает

✅ Сканирование ошибок автомобиля (DTC - Diagnostic Trouble Codes)
✅ Полный OBD-II протокол парсинг
✅ 60+ PID функций для чтения данных автомобиля
✅ Обработка всех 12 OBD протоколов (SAE J1850, ISO 9141, ISO 14230, CAN и т.д.)

## Ключевые файлы для изучения

### 1. src/ELMduino.h

- **ГЛАВНЫЙ ФАЙЛ**: API спецификация (50+ методов)
- Все публичные методы для работы с ELM327
- Определения констант и error codes
- Документированные PID функции

### 2. src/ELMduino.cpp

- **ГЛАВНЫЙ ФАЙЛ**: Полная логика парсинга OBD ответов
- Методы `conditionResponse()` и `processPID()` - ядро обработки
- State machine для non-blocking режима
- Обработка буфера и таймаутов

### 3. examples/simple_rpm_example.ino

- Базовый паттерн использования библиотеки
- Инициализация адаптера
- Запрос данных RPM

### 4. examples/ (другие примеры)

- `dtc_example.ino` - сканирование DTC кодов
- `pid_queries.ino` - примеры запросов различных PIDs

## Ключевые функции для диагностики

```cpp
// ===== DTC (ОШИБКИ АВТОМОБИЛЯ) =====
void currentDTCCodes(bool isBlocking = true);  // Получить текущие коды ошибок
bool resetDTC();                               // Очистить коды ошибок

// ===== ОСНОВНЫЕ PID ЗАПРОСЫ =====
float rpm();                     // Обороты двигателя (RPM)
int32_t kph();                   // Скорость (km/h)
float engineCoolantTemp();       // Температура двигателя (°C)
float fuelLevel();               // Уровень топлива (%)
float intakeAirTemp();           // Температура впускного воздуха
float throttle();                // Положение дроссельной заслонки (%)
float oxygenSensor1();           // Данные кислородного датчика

// ===== НИЗКОУРОВНЕВЫЕ ЗАПРОСЫ =====
float processPID(uint8_t service, uint16_t pid, uint8_t numResponses, 
                 uint8_t numBytes, float scale = 1, float bias = 0);
bool sendCommand(const char* cmd);
```

## Supported OBD Commands (для Ediag)

| Команда | Назначение | Пример запроса |
|---------|-----------|---------------|
| `01` | Current Data | `010C` - RPM |
| `03` | Get DTC (Diagnostic Trouble Codes) | Получить ошибки |
| `04` | Clear DTC | Очистить ошибки |
| `07` | Get pending DTC | Ожидающие ошибки |
| `19` | Read freeze frame data | Снимок при ошибке |

## Инициализация ELM327 (для Ediag)

```cpp
// Стандартная последовательность инициализации
ATZ     // Reset адаптера
ATE0    // Echo off (не дублировать команды)
ATL0    // Line feed off
ATH0    // Headers off (для простого парсинга)
ATSP0   // Auto-detect protocol (или ATSP6 для CAN 500 кбод)
```

## Обработка ошибок

```cpp
// Error codes из библиотеки
ELM_SUCCESS          // Команда выполнена успешно
ELM_TIMEOUT          // Таймаут ожидания ответа
ELM_NO_RESPONSE      // Адаптер не отвечает
ELM_BUFFER_OVERFLOW  // Буфер переполнен
ELM_NO_DATA          // Нет данных от автомобиля
ELM_STOPPED          // Адаптер остановлен
ELM_UNABLE_TO_CONNECT // Не удалось подключиться к протоколу
```

## Специфичное для Ediag

### Режим ELM327 emulation

Ediag работает в режиме эмуляции ELM327, поэтому использует те же AT-команды:

1. **Инициализация**:
   - `ATZ` → reset
   - `ATE0` → echo off
   - `ATL0` → line feed off
   - `ATSP0` → автопоиск протокола (может занять 1-2 сек)

2. **Запрос DTC**:
   - Команда `03` (read stored DTC)
   - Формат ответа: `43 [кол-во кодов] [код1] [код2] ...`
   - Пример: `43 02 01 33 02 44` = 2 кода (P0133, P0244)

3. **Очистка DTC**:
   - Команда `14` (clear DTC - если поддерживается)
   - Ответ: `54` при успехе

4. **Запрос PID**:
   - Формат: `[Mode][PID]\r`
   - Пример: `010C\r` (запрос RPM)
   - Ответ: `41 0C 1A F8` → RPM = (0x1AF8 / 4) = 7000

### Особенности Ediag

- ⚠️ **Double responses**: некоторые Ediag адаптеры отправляют ответ дважды - нужна дедупликация
- ⚠️ **Baud rate**: стандартно 38400, но может быть 115200 - пробуй оба
- ⚠️ **Timeout**: Ediag может быть медленным при первом подключении - увеличь timeout до 5000ms
- ⚠️ **Protocol detection**: `ATSP0` может занять 1-2 сек - добавь прогресс бар

## Как использовать в Node.js проекте

1. **Парсинг OBD ответов**:
   - Копируй логику из `conditionResponse()` и `processPID()`
   - Адаптируй C++ алгоритмы в JavaScript/TypeScript

2. **DTC сканирование**:
   - Используй структуру из `currentDTCCodes()`
   - Парсинг DTC кодов: P0XXX, C0XXX, B0XXX, U0XXX

3. **State machine**:
   - Non-blocking режим из библиотеки применим в async/await Node.js
   - Паттерн: отправка команды → ожидание ответа → проверка статуса → обработка данных

4. **Error handling**:
   - Используй error codes из `ELMduino.h` как референс
   - Добавь reconnection logic при ELM_TIMEOUT

## Преимущества этого репозитория

✅ Production-ready код с 765 звездами на GitHub
✅ Правильная обработка всех режимов ELM327
✅ Полный набор PIDs - можно запрашивать любые данные
✅ Excellent error handling - меньше нужно дебажить
✅ Non-blocking архитектура - подходит для киоска (UI не зависает)
✅ Примеры для всех основных задач (DTC, PID запросы, инициализация)

## Интеграция с твоим проектом

1. Используй `ELMduino.h` как REFERENCE для логики парсинга OBD ответов
2. Парсинг функций (`conditionResponse`, `processPID`) - скопируй логику в backend
3. Обработка ошибок и состояний - применимо на любом языке
4. DTC парсинг - критично для сканирования ошибок Ediag
5. Адаптируй C++ логику в Node.js (1-в-1 транспортировка алгоритмов)

## Дополнительные ресурсы

- **docs/**: подробная документация по протоколу
- **reference/**: спецификации OBD-II протоколов
- **examples/**: практические примеры для всех основных задач
