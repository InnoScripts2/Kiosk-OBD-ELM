# Change Report — 27.11.2025 (Session 41 — DTC/PID Refactor)

## Цели
- Зафиксировать фактическое состояние словарей DTC/PID и старт миграции в рамках сессии 41.

## Действия
1. Выполнен аудит активов:
   - `feature-obd-core/src/main/assets/dtc-codes.json` — 2 266 записей, 293 KB.
   - `base/dtcmapping.json` — 3 760 записей (расширенные описания).
   - PID активы: Mode1 — 97 строк, Mode4 — 1, Mode9 — 4.
   - Проведена сверка текстовых доноров (BMW/Lexus/Nissan/Toyota/Generic/Manufacturer-specific) — присутствуют коды классов B/C/U, отсутствующие в приложении.
2. Подготовлен документ `09-docs/02-application/plans/plan-dtc-pid-refactor.md` с целевым форматом JSON, этапами миграции и требованиями к тестам.
3. Зафиксированы обязательные тесты и метрики (unit-тесты parity, JSON schema task, smoke diagnostics).

## Выводы
- Требуется автоматизировать объединение `dtcmapping` и текстовых источников.
- PID справочники необходимо расширить до полной схемы SAE J1979 с единицами измерения.
- Следующий шаг: подготовить скрипт выгрузки CSV и unit-тест `DtcCatalogParityTest`.

## Связанные артефакты
- План: `09-docs/02-application/plans/plan-dtc-pid-refactor.md`.
- Roadmap: `09-docs/02-application/plans/plan-80-session-roadmap.md` (строка 41, статус IN_PROGRESS).
