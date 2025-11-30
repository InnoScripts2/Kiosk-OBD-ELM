# Feature Thickness Module

Модуль для работы с толщиномером ЛКП через BLE.

## Структура

### Models
- `ThicknessZoneModels.kt` - модели зон измерений (60 точек)
- `ThicknessDeviceConfig.kt` - конфигурация устройства
- `ThicknessMeasurementModels.kt` - дополнительные модели данных

### BLE Integration
- `ThicknessBleAdapter.kt` - адаптер для работы с platform/bluetooth
- `ThicknessProtocolParser.kt` - парсер ASCII/Binary протокола

### Mock
- `MockThicknessDevice.kt` - DEV мок-устройство [MOCK MODE]

### Utils
- `ThicknessValidation.kt` - валидация измерений

### Workflow
- `ThicknessWorkflow.kt` - оркестрация процесса измерений

### DI
- `ThicknessModule.kt` - Dependency Injection модуль

## Использование

### DEV режим
```kotlin
val device = ThicknessModule.provideThicknessDevice(
    mode = ThicknessModule.DeviceMode.DEV,
    logger = logger
)
```

### Production режим
```kotlin
val device = ThicknessModule.provideThicknessDevice(
    mode = ThicknessModule.DeviceMode.PROD,
    bleScanner = bleScanner,
    bleConnectionManager = bleConnectionManager,
    logger = logger
)
```

## Тестирование

```bash
./gradlew :feature-thickness:testDebugUnitTest
```

## Блокеры

- AGP 8.4.1 недоступен - компиляция невозможна
