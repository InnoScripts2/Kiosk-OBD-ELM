# DTC Catalog Builder

Скрипт `build_catalog.py` собирает единый справочник OBD-II кодов из каталога `android/base` и генерирует два артефакта:

1. `feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc.json` — общий SAE/ISO каталог.
2. `platform/data/src/main/assets/dtc_database.json` — производительные описания.

## Использование
```powershell
cd android
python .\tools\dtc\build_catalog.py
```

Параметры:
- `--base` — путь к каталогу с исходниками (по умолчанию `android/base`).
- `--generic-out` — путь для файла `dtc.json`.
- `--manufacturer-out` — путь для файла `dtc_database.json`.

Скрипт автоматически нормализует пробелы, дедуплицирует описания и добавляет альтернативные формулировки в поле `notes`.
