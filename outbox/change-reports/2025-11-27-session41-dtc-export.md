# Change Report — 27.11.2025 (Session 41 — DTC CSV Export)

## Действия
1. Добавлен скрипт `tools/dtc/export-dtc-summary.ps1`, конвертирующий `base/dtcmapping.json` и `feature-obd-core/src/main/assets/dtc-codes.json` в объединённую таблицу с флагами источников.
2. Скрипт запускается без параметров и сохраняет CSV в `tmp/dtc-summary.csv`, создавая каталог автоматически.
3. Выполнен запуск: `pwsh -ExecutionPolicy Bypass -File tools/dtc/export-dtc-summary.ps1`.
4. Получены метрики:
   - Всего кодов: 4 919.
   - Только в базе: 2 650.
   - Только в приложении: 1 159.
   - Пересечение: 1 107.
   - Классы: P — 2 445, B — 1 237, C — 615, U — 622.

## Выводы
- В `dtc-codes.json` отсутствует >2 600 кодов; большая часть относится к производственным классам B/C/U.
- CSV станет основой для генерации нового `dtc-index.json` и брендов.
- Следующий шаг: выделить уникальные коды из текстовых доноров и объединить с отчётом.

## Артефакты
- `tools/dtc/export-dtc-summary.ps1` — новый инструмент миграции.
- `tmp/dtc-summary.csv` — CSV с объединённым справочником.
- План: `09-docs/02-application/plans/plan-dtc-pid-refactor.md` обновлён (этап А, задача 1 закрыта).
