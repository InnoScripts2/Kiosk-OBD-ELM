# DTC Catalog Builder

Скрипт `build_catalog.py` собирает единый справочник OBD-II кодов из каталога `android/base` и генерирует три артефакта:

1. `feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc.json` — общий SAE/ISO каталог.
2. `platform/data/src/main/assets/dtc_database.json` — производительные описания.
3. `platform/data/src/main/assets/catalog_version.json` — метаданные версии каталога.

## Использование

### Прямой вызов Python-скрипта

```powershell
cd android
python .\tools\dtc\build_catalog.py
```

Параметры:
- `--base` — путь к каталогу с исходниками (по умолчанию `android/base`).
- `--generic-out` — путь для файла `dtc.json`.
- `--manufacturer-out` — путь для файла `dtc_database.json`.
- `--version-out` — путь для файла `catalog_version.json`.
- `--version` — версия каталога (по умолчанию `1.0.0`).
- `--verbose`, `-v` — включить детальное логирование.

### Использование через Gradle

```bash
cd android
./gradlew :platform-data:generateDtcCatalog
```

Или с указанием версии:

```bash
./gradlew :platform-data:generateDtcCatalog -PdtcCatalogVersion=1.1.0
```

Gradle задача автоматически вызывается при сборке модуля `platform-data` (хук в `preBuild`).

## Тестирование

Установите зависимости:

```bash
cd android/tools/dtc
pip install -r requirements.txt
```

Запустите тесты:

```bash
pytest test_build_catalog.py -v
```

Или с покрытием:

```bash
pytest test_build_catalog.py -v --cov=build_catalog --cov-report=term
```

## Версионирование

Метаданные версии каталога включают:
- **version** — семантическая версия каталога (например, `1.0.0`)
- **build_timestamp** — дата и время сборки в формате ISO 8601
- **generic_entries** — количество общих SAE/ISO записей
- **manufacturer_entries** — количество производительных записей
- **manufacturers** — список производителей в каталоге
- **sources** — информация об источниках данных

Пример загрузки версии в коде:

```kotlin
import com.selfservice.platform.data.DtcCatalogVersion

val version = DtcCatalogVersion.load(assetManager)
Log.i("DTC", version.toLogString())
// Outputs: DTC Catalog v1.0.0 (built 2025-11-23T06:37:40Z): 17958 generic + 58217 manufacturer entries from 6 manufacturers
```

## Процесс работы

Скрипт автоматически нормализует пробелы, дедуплицирует описания и добавляет альтернативные формулировки в поле `notes`.

1. Парсит `base/OBDII.DTC-main/DTC.cs` для получения системных описаний SAE/ISO
2. Загружает `base/dtcmapping.json` для обратной совместимости
3. Объединяет данные, добавляя альтернативные описания в поле `notes`
4. Обрабатывает текстовые справочники производителей из каталогов `All * OBD2 Codes List` и `Generic *`
5. Генерирует три JSON-файла с данными и метаданными

## Структура выходных файлов

### dtc.json (Generic Catalog)

```json
[
  {
    "code": "P0001",
    "system": "powertrain",
    "label": "Fuel Volume Regulator A Control Circuit/Open",
    "notes": "Alt: Fuel Volume Regulator Control Circuit / Open"
  }
]
```

### dtc_database.json (Manufacturer Catalog)

```json
{
  "Toyota": [
    {
      "code": "P1500",
      "description": "Hybrid battery voltage high",
      "source": "toyota.txt"
    }
  ]
}
```

### catalog_version.json (Version Metadata)

```json
{
  "version": "1.0.0",
  "build_timestamp": "2025-11-23T06:37:40.248683+00:00",
  "generic_entries": 17958,
  "manufacturer_entries": 58217,
  "manufacturers": ["BMW", "Lexus", "Nissan", "Toyota"],
  "sources": {
    "csharp_catalog": "base/OBDII.DTC-main/DTC.cs",
    "dtc_mapping": "base/dtcmapping.json",
    "manufacturer_directories": [...]
  }
}
```
