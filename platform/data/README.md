# Platform Data Module

## DTC Database Integration

Данный модуль содержит интеграцию с каталогом DTC (Diagnostic Trouble Codes).

### Структура

1. **DTC data folder** — содержит сгенерированную базу `dtc_database.json`, метаданные `catalog_version.json` и утилиты загрузки.

2. **Генерация данных**
   
   Файлы генерируются скриптом `tools/dtc/build_catalog.py`, который собирает данные из `android/base/*`.
   
   Для генерации вручную:
   ```bash
   cd android
   python3 tools/dtc/build_catalog.py --version 1.0.0
   ```
   
   Или через Gradle task:
   ```bash
   ./gradlew :platform-data:generateDtcCatalog -PdtcCatalogVersion=1.0.0
   ```

3. **Версионирование**
   
   Каждая генерация создаёт файл `catalog_version.json` с метаданными:
   - Версия каталога (семантическое версионирование)
   - Дата и время сборки (ISO 8601)
   - Количество записей (generic и manufacturer)
   - Список производителей
   - Источники данных

4. **Интеграция в Android**
   
   Поместите артефакты в `android/platform/data/src/main/assets/`:
   - `dtc_database.json` — производительные коды
   - `catalog_version.json` — метаданные версии
   
   В Android-коде:
   
   ```kotlin
   // Загрузка базы данных
   val database = DtcDataLoader.loadDatabase(
       assetManager, 
       "dtc_database.json"
   )
   
   // Загрузка версии каталога
   val version = DtcCatalogVersion.load(assetManager)
   Log.i("DTC", version.toLogString())
   // Outputs: DTC Catalog v1.0.0 (built 2025-11-23T06:37:40Z): 
   //          17958 generic + 58217 manufacturer entries from 6 manufacturers
   ```

5. **Поиск кодов**
   
   Для офлайн-поиска по производителям подключите `ManufacturerDtcProvider` из `feature-obd-core` и передайте экземпляр в `ObdDictionaryManager.attachManufacturerProvider`.
   
   ```kotlin
   val toyota = database.find("Toyota", "P1500")
   // Returns: ManufacturerDtcEntry(
   //   manufacturer="Toyota",
   //   code="P1500",
   //   description="Hybrid battery voltage high",
   //   source="toyota.txt"
   // )
   
   val allMatches = database.findAll("P1500")
   // Returns list of all manufacturers with this code
   ```

## Автоматическая генерация при сборке

Gradle task `generateDtcCatalog` автоматически вызывается при сборке модуля через хук в `preBuild`.

Для отключения автоматической генерации закомментируйте в `build.gradle.kts`:
```kotlin
// tasks.named("preBuild") {
//     dependsOn("generateDtcCatalog")
// }
```

## Testing

Модуль содержит unit-тесты для:
- `DtcDataLoader` — загрузка и парсинг баз данных
- `DtcCatalogVersion` — загрузка и парсинг метаданных версии

Запуск тестов:
```bash
./gradlew :platform-data:test
```
