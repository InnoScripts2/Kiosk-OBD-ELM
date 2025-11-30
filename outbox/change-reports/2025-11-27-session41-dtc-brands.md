# Change Report — 27.11.2025 (Session 41 — Brand-specific DTC extraction)

## Действия
1. Разработан скрипт `tools/dtc/extract-dtc-brand-codes.ps1`, который парсит текстовые доноры (`base/All BMW…`, `All Lexus…`, `All Toyota…`, `All Nissan…`, `Generic OBD2…`, `All Manufacturer-specific…`).
2. Скрипт ищет шаблоны `[PBCU][0-9A-F]{4}` в `.txt` файлах, устраняет дубликаты внутри бренда и формирует CSV `tmp/dtc-brand-codes.csv` с колонками `Code, Class, Brand, Description, SourceFile`.
3. Запуск команды: `pwsh -ExecutionPolicy Bypass -File tools/dtc/extract-dtc-brand-codes.ps1`.
4. Полученная статистика:
   - BMW: 8 191 кодов (B 1 556 / C 1 381 / P 3 837 / U 1 417).
   - Lexus: 8 192 (B 1 599 / C 1 380 / P 3 788 / U 1 425).
   - Toyota: 8 359 (B 1 645 / C 1 481 / P 3 780 / U 1 453).
   - Nissan: 8 168 (B 1 750 / C 1 503 / P 3 448 / U 1 467).
   - Generic: 11 209 (B 1 630 / C 1 441 / P 6 367 / U 1 771).
   - Manufacturer-specific: 5 431 (B 2 182 / C 653 / P 2 081 / U 515).

## Выводы
- Все брендовые доноры успешно индексированы, Stage A задачи 2–3 закрыты.
- CSV станет базой для построения `dtc-brand-overrides/*.json` и проверки наличия кодов классов B/C/U в UI и отчётах.
- Следующий шаг: добавить unit-тест parity (проверка покрытий P0001–P0999 и ключевых брендов).
