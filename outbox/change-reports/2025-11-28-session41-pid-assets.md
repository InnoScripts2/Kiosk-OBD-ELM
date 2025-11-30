# Change Report — 28.11.2025 (Session 41 — PID assets)

## Цели
- Завершить этап B по PID: подключить `pids-mode{1,4,9}-v2.json` к рантайму и прописать защитные unit-тесты.

## Действия
1. Обновлён `ObdPidDefinition` и `PidCatalog`:
   - загрузка распадается на три новых ресурса (`pids-mode1/4/9-v2.json`) с fallback на `pids.json`.
   - нормализация hex значений поддерживает числовые и строковые поля, новые атрибуты (`status`, `sources`, `bytes`).
2. Добавлены тесты `PidCatalogSchemaTest` и расширены проверки `PidCatalogTest` (метаданные, режимы, источники).
3. План `plan-dtc-pid-refactor.md` и briefing обновлены, зафиксирован прогресс сессии 41.

## Тесты
- `./gradlew :feature-obd-core:test` — ✅ (Windows, JDK 17, 28.11.2025).
