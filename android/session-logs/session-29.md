# Session 29 — OBD PID Parity & Catalog Guardrails

Дата: 23.11.2025
Диапазон: Фаза B → Part 2 (ingest AndroidOBD PIDs)

## Цели
- Зафиксировать, что донорский каталог AndroidOBD (modes 01/04/09) точно соответствует каноническому `PidCatalog`/`PidConversions`.
- Добавить адаптер между форматом `AndroidOBD-main` (`PID.json`) и нашим `ObdPidDefinition`, включая нормализацию единиц измерения.
- Расширить тестовый контур `feature-obd-elm-port` fail-fast проверками перед дальнейшим ingest-ом OBD transport.

## Выполненные действия
- Создан `ElmPidDefinitionAdapter` + unit-тест, который нормализует hex (`0xNN`), диапазоны и маппит донорские единицы (`%`, `kPa (absolute)`, `psi (gauge)`, `°C`) в канонические названия (`percent`, `kPa`, `psi_gauge`, `degC`), чтобы downstream-модули не знали о различиях форматов.
- Добавлен `ElmPidCatalogParityTest`:
  - Проверяет наличие всех PID из `pids-mode1/4/9.json` в `PidCatalog`.
  - Для mode 01 сравнивает формулы, вычисляя семантическую эквивалентность через `PidFormulaEvaluator` (равенство даже при разном порядке множителей/скобок).
  - Игнорирует только битовые таблицы поддерживаемых PID (0x00/0x20/0x40/0x60/0x80), которых нет в каноне.
- Тестовый фреймворк обогащён вспомогательными функциями (RPN нормализация, генерация payload-ов) — пригодится при добавлении новых донорских каталогов.
- Обновлён `plan-component-migration.md`: зафиксирован прогресс по пункту «Перенос ядра ELM» и ссылка на parity-тест как обязательный гейт для будущих ingest-ов.

## Тесты
- `./gradlew :feature-obd-elm-port:testDebugUnitTest`
  - ✔️ Выполнено, покрывает `ElmPidDefinitionAdapterTest`, `ElmPidCatalogParityTest` и существующие smoke parity-тесты формул.

## Следующие шаги
- Использовать адаптер для генерации промежуточных «reference» PID snapshot-ов перед интеграцией ISO-TP/USB транспортов.
- Расширить parity-тесты под Mode 09 VIN/CAL ID (сравнить label/length) и добавить аналогичные проверки для DTC каталога (`dtc-codes.json`).
- После валидации подключить донорские `ObdInitSequence` и транспортные драйверы в `feature-obd-core` (сессия 30).