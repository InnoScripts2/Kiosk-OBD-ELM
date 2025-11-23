DTC data folder — содержит сгенерированную базу `dtc_database.json` и утилиты загрузки.

Файл генерируется скриптом `tools/dtc/build_catalog.py`, который собирает данные из `android/base/*`.

Интеграция:
 - Поместите `dtc_database.json` в `android/platform/data/src/main/assets/` (или синхронизируйте артефакт с аналогичной директорией в целевом модуле приложения).
 - В Android-коде используйте `DtcDataLoader.loadDatabase(assetManager, "dtc_database.json")`, чтобы получить `ManufacturerDtcDatabase`.
 - Для офлайн-поиска по производителям подключите `ManufacturerDtcProvider` из `feature-obd-core` и передайте экземпляр в `ObdDictionaryManager.attachManufacturerProvider`.
