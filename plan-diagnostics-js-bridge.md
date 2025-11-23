# API диагностического JS-моста

Документ фиксирует контракт между WebView-клиентом киоска и нативным Android-приложением для управления OBD-II диагностикой и генерацией отчётов. API повторяет envelope-подход платёжного моста, чтобы веб-клиент мог отправлять асинхронные запросы и получать ответы через `CustomEvent`.

## Транспорт и событие

- Нативный слой экспонирует объект `window.KioskDiagnostics` с методом `postMessage(string)`.
- JS-клиент публикует API `window.KioskDiagnosticsClient`, который инкапсулирует envelope и возвращает `Promise`.
- Каждое сообщение — JSON вида:

```json
{
  "requestId": "optional-string",
  "action": "run_report | get_last_report | cancel_report | preview_snapshot | dictionary_lookup",
  "payload": { ... }
}
```

- Ответы приходят через `window.dispatchEvent(new CustomEvent('kiosk-diagnostics', { detail }))`, где `detail` всегда содержит `type = "diagnostics_response"`, `ok`, `action`, `requestId` и либо `data`, либо `error`.

## Доступные действия

### 1. `run_report`

Запускает полный цикл диагностики через PassThru, рассчитывает метрики, формирует HTML/PDF отчёт и возвращает его вместе с техническим срезом.

**Payload** (все поля опциональны):

| Поле                          | Тип     | Описание                                                                                                   |
| ----------------------------- | ------- | ---------------------------------------------------------------------------------------------------------- |
| `vehicle.make`                | string  | Марка автомобиля (обязательна для включения блока `vehicle`).                                              |
| `vehicle.model`               | string  | Модель.                                                                                                    |
| `vehicle.year`                | number  | Год выпуска.                                                                                               |
| `vehicle.vin`                 | string  | VIN.                                                                                                       |
| `customer.phone`              | string  | Телефон клиента.                                                                                           |
| `customer.email`              | string  | Email клиента.                                                                                             |
| `request.readTroubleCodes`    | boolean | Включить чтение DTC (по умолчанию `true`).                                                                 |
| `request.clearTroubleCodes`   | boolean | Выполнить очистку DTC (по умолчанию `false`).                                                              |
| `request.responseTimeoutMs`   | number  | Таймаут ожидания ответа транспорта.                                                                        |
| `request.vehicleManufacturer` | string  | Маркер производителя для словарей.                                                                         |
| `request.pidRequests[]`       | array   | Дополнительные PID-запросы `{ "mode": "0x01", "pid": "0x0C", "timeoutMs": 1500 }`.                         |
| `connection.forceReconnect`   | boolean | Принудить переподключение адаптера перед запуском.                                                         |
| `connection.adapter`          | object  | Снимок выбранного адаптера `{ "address": "AA:BB", "name": "PassThru", "rssi": -60, "protocol": "J2534" }`. |

**Успешный ответ (`data`)**:

| Поле                    | Тип    | Описание                                                                                                                                                                      |
| ----------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sessionId`             | string | Идентификатор диагностической сессии (совпадает с отчётом).                                                                                                                   |
| `generatedAtMillis`     | number | Таймстемп генерации отчёта.                                                                                                                                                   |
| `report.html`           | string | HTML-версия отчёта.                                                                                                                                                           |
| `report.pdfBase64`      | string | PDF в Base64 для отправки клиенту.                                                                                                                                            |
| `report.pdfBytesLength` | number | Размер PDF в байтах.                                                                                                                                                          |
| `vehicle` / `customer`  | object | Подтверждённые метаданные.                                                                                                                                                    |
| `snapshot`              | object | Диагностические метрики (timestamp + массив `metrics[]` с полями `id`, `pidKey`, `label`, `status`, `value`, `unit`, `category`, `advice`, `sourceTimestampMillis`, `trend`). |
| `recommendations[]`     | array  | Блок рекомендаций (`metricId`, `pidKey`, `title`, `message`, `severity`, `priority`, `threshold`).                                                                            |
| `diagnostics`           | object | Сырые результаты PassThru (см. ниже).                                                                                                                                         |

`metrics[].pidKey` соответствует SAE-ключу вида `01:0C`, помогающему сопоставлять выборки PID и рекомендации. `metrics[].trend` содержит идентификатор метрики и массив точек `{ "timestampMillis": number, "value": number }`, отражающих последние измерения, `category` транслирует тип (electrical/thermal/...), а `sourceTimestampMillis` фиксирует момент, когда значение метрики было получено от адаптера.

`diagnostics` включает (все поля находятся на одном уровне объекта, без дополнительного вложенного блока `diagnostics`):
- `durationMs`
- `clearPerformed`
- `pidSampleCount`
- `sessionState` (state + детали устройства/пререквизитов)
- `pidSamples[]` — последняя выборка PID-значений:
  - `mode`, `pid`, `pidKey`
  - `description`
  - `expectedUnit`, `unit`
  - `value`, `valid`, `timestampMillis`
  - `rawResponse`
  - `thresholds` (`warningLow`, `criticalLow`, `warningHigh`, `criticalHigh`)
- `troubleCodes[]` (`code`, `label`, `system`, `notes`)
- `failures[]` (`stage`, `detail`)

### 2. `get_last_report`

Возвращает последний успешно завершённый результат `run_report`, если он ещё хранится в памяти приложения.

**Payload**: пустой объект `{}`.

**Ответ**: идентичен структуре `run_report`, но без нового запуска диагностики. Ошибка `not_ready`, если отчёт отсутствует.

### 3. `cancel_report`

Прерывает активный запуск `run_report`. Используется, когда оператор отменяет диагностику на веб-стране.

**Payload**: пустой объект `{}`.

**Успешный ответ (`data`)**:

| Поле        | Тип  | Описание                                     |
| ----------- | ---- | -------------------------------------------- |
| `cancelled` | bool | Всегда `true`, подтверждает факт отмены run. |

Если диагностика не выполняется, возвращается ошибка `not_running`.

### 4. `preview_snapshot`

Формирует быстрый снимок PID-значений без запуска полного PassThru workflow. Используется, чтобы показать оператору живые данные адаптера.

**Payload** (все поля опциональны):

| Поле             | Тип    | Описание                                                                               |
| ---------------- | ------ | -------------------------------------------------------------------------------------- |
| `protocol`       | string | Протокол OBD (`iso_15765_4_can_11_500`, `j1850_pwm`, и т.д.).                          |
| `pidSelectors[]` | array  | Набор `{ "mode": "0x01", "pid": "0x0C" }`, определяющий какие PID необходимо опросить. |

**Успешный ответ (`data`)**:

| Поле                         | Тип    | Описание                                                                                                                                                                      |
| ---------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `snapshot.generatedAtMillis` | number | Таймстемп формирования предпросмотра.                                                                                                                                         |
| `snapshot.protocol`          | string | Подтверждённый протокол адаптера.                                                                                                                                             |
| `snapshot.samples[]`         | array  | Массив значений PID; каждая запись содержит `mode`, `pid`, `pidKey`, `description`, `expectedUnit`, `unit`, `value`, `valid`, `rawResponse`, `timestampMillis`, `thresholds`. |

Поле `thresholds` повторяет структуру из блока рекомендаций и показывает допустимые диапазоны для данного PID.

### 5. `dictionary_lookup`

Позволяет веб-клиенту получать описания PID и DTC из локальных словарей, а также проверять наличие фирменных словарей. Запрос не запускает диагностический Workflow и выполняется полностью в памяти.

**Payload** (хотя бы один из блоков `pids`, `dtcs` или флаг `includeManufacturers` должен быть передан):

| Поле                   | Тип             | Описание                                                                                                                                                                              |
| ---------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pids[]`               | array           | Набор PID для поиска. Элементом может быть строка `"01:0C"` либо объект `{ "mode": "0x01", "pid": "0x0C" }`. Дополнительно поддерживается поле `key` с уже нормализованным значением. |
| `dtcs[]`               | array           | Диагностические коды. Каждый элемент — строка `"P0420"` или объект `{ "code": "P0420" }`.                                                                                             |
| `manufacturer`         | string          | Опциональный идентификатор производителя. При наличии Webster попытается вернуть фирменное описание DTC, если стандартного нет.                                                       |
| `includeManufacturers` | boolean (false) | Если `true`, ответ дополнительно содержит полный список производителей, доступных в оффлайн-каталоге.                                                                                 |

**Успешный ответ (`data`)**:

| Поле     | Тип    | Описание                                                                                                                                                         |
| -------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pids[]` | array  | Расшифрованные PID. Каждый элемент содержит `mode`, `pid`, `pidKey`, `label`, `unit`, `min`, `max`, `notes`, `formula`, `conversion`, `pollIntervalMs`, `found`. |
| `dtcs[]` | array  | Расшифрованные коды. Поля: `code`, `label`, `system`, `notes`, `found`, `source` (`standard`/`manufacturer`).                                                    |
| `meta`   | object | Содержит `pidRevision`, `dtcRevision`, `manufacturerCount`, `manufacturer` (если был в запросе) и опционально `manufacturers[]`.                                 |

`pidRevision` и `dtcRevision` включают `source`, `versionLabel`, `refreshedAtMillis`, `entryCount`, что позволяет UI понимать актуальность словарей.

#### Ошибки для `dictionary_lookup`

| Код                | Причина                                                                              |
| ------------------ | ------------------------------------------------------------------------------------ |
| `invalid_argument` | Некорректная структура JSON, отсутствуют целевые массивы и не запрошены метаданные.  |
| `internal_error`   | Любые другие сбои при обращении к словарям (например, повреждённые данные на диске). |

## Ошибки и коды

| Код                   | Сценарий                                                                        |
| --------------------- | ------------------------------------------------------------------------------- |
| `invalid_request`     | JSON поврежден, отсутствует `action` или required payload.                      |
| `unknown_action`      | Неподдерживаемое значение `action`.                                             |
| `busy`                | Параллельный запуск диагностики запрещён.                                       |
| `not_ready`           | Запрошенный артефакт отсутствует (например, `get_last_report`).                 |
| `invalid_argument`    | Ошибка валидации входных данных.                                                |
| `failed_precondition` | Состояние системы не готово (нет подключения OBD, workflow не инициализирован). |
| `not_running`         | Попытка отменить диагностику, когда запущенного цикла нет.                      |
| `internal_error`      | Любая другая необработанная ошибка.                                             |

Каждая ошибка возвращает структуру:

```json
{
  "type": "diagnostics_response",
  "ok": false,
  "action": "run_report",
  "requestId": "...",
  "error": {
    "code": "busy",
    "message": "Diagnostics session is already running"
  }
}
```

## Поведение JS-клиента

- Таймаут по умолчанию — 120 секунд; переопределяется параметром `options.timeoutMs` в `KioskDiagnosticsClient.runReport(payload, { timeoutMs })`.
- Pending-запросы хранятся в `Map` и снимаются как только прилетает событие с совпадающим `requestId`.
- Клиент экспортирует методы:
  - `runReport(payload?, options?)`
  - `getLastReport(options?)`
  - `cancelReport(options?)`
  - `previewSnapshot(payload?, options?)`
  - `lookupDictionary(payload?, options?)`
- При отсутствии нативного объекта `KioskDiagnostics` методы отклоняют промис с ошибкой `Native diagnostics bridge is not available`.

Документ служит эталоном для реализации Kotlin-моста и JS-клиента, а также для валидации e2e-тестов веб-приложения.
