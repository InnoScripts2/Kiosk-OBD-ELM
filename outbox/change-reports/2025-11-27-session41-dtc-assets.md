# Change Report — 27.11.2025 (Session 41 — DTC asset generator)

## Действия
1. Добавлен PowerShell-скрипт `android/scripts/dtc/generate-dtc-assets.ps1`, который собирает `dtc-index.json` и набор `dtc-brand-overrides/*.json` из CSV (`tmp/dtc-summary.csv`, `tmp/dtc-brand-codes.csv`). Скрипт нормализует описания, строит ссылки на источники и формирует slug для брендов.
2. Созданы новые активы: `feature-obd-core/src/main/assets/dtc-index.json` (4 919 записей) и директория `feature-obd-core/src/main/assets/dtc-brand-overrides/` с файлами `bmw.json`, `generic.json`, `lexus.json`, `manufacturer.json`, `nissan.json`, `toyota.json`. Каждый файл содержит сортированный список кодов, описание и исходный файл бренда.
3. Выполнена команда `pwsh -NoProfile -File android/scripts/dtc/generate-dtc-assets.ps1` (см. лог в терминале) — генератор подтвердил выпуск 4 919 индексов и 6 брендовых файлов.

## Выводы
- Этап B, пункт 1 (`dtc-index` + brand overrides) плана `plan-dtc-pid-refactor.md` закрыт, активы доступны в `feature-obd-core/src/main/assets` и готовы для подключения в коде.
- Скрипт можно переиспользовать при обновлении CSV: путь параметризован, директория брендов очищается перед записью, формируется ISO timestamp в JSON.
- Следующий шаг по сессии 41 — адаптировать `feature-obd-core` к новому формату и подготовить PID-словарь v2.
