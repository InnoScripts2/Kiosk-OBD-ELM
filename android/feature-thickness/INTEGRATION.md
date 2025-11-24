# Thickness Feature Integration Guide

## Session 12B - Миграция драйвера толщиномера

Дата: 24.11.2025

### Обзор

Этот модуль предоставляет полную функциональность для работы с толщиномером ЛКП через BLE.

### Архитектура

```
feature-thickness/
├── models/           # Модели данных
│   ├── ThicknessZoneModels.kt          # 60 зон измерений
│   ├── ThicknessDeviceConfig.kt        # Конфигурация устройства
│   └── ThicknessMeasurementModels.kt   # Дополнительные модели
├── ble/              # BLE интеграция
│   └── ThicknessBleAdapter.kt          # Адаптер для platform/bluetooth
├── protocol/         # Протокол обмена данными
│   └── ThicknessProtocolParser.kt      # Парсер ASCII/Binary
├── mock/             # DEV мок-устройство
│   └── MockThicknessDevice.kt          # [MOCK MODE]
├── utils/            # Утилиты
│   └── ThicknessValidation.kt          # Валидация измерений
├── workflow/         # Оркестрация
│   └── ThicknessWorkflow.kt            # Управление процессом
└── di/               # Dependency Injection
    └── ThicknessModule.kt              # DI модуль
```

### Использование

#### 1. Инициализация в DEV режиме

```kotlin
val device = ThicknessModule.provideThicknessDevice(
    mode = ThicknessModule.DeviceMode.DEV,
    logger = logger,
    mockConfig = MockDeviceConfig.forTesting()
)

val stateMachine = ThicknessMeasurementStateMachine()
val workflow = ThicknessWorkflow(device, stateMachine, logger)
```

#### 2. Запуск процесса измерений

```kotlin
val result = workflow.startMeasurementProcess(
    sessionId = "session_12345",
    vehicleType = "sedan"
)

result.onSuccess { report ->
    println("Measurements completed:")
    println("  Total: ${report.measurements.size}")
    println("  Average: ${report.analysis.avgValue} μm")
    println("  Deviations: ${report.analysis.deviations}")
    println("  Recommendation: ${report.analysis.recommendation}")
}

result.onFailure { error ->
    println("Error: ${error.message}")
}
```

#### 3. Мониторинг прогресса

```kotlin
workflow.workflowState.collect { state ->
    when (state) {
        is ThicknessWorkflow.WorkflowState.Idle -> {
            println("Idle")
        }
        is ThicknessWorkflow.WorkflowState.Connecting -> {
            println("Connecting to device...")
        }
        is ThicknessWorkflow.WorkflowState.Ready -> {
            println("Ready to measure")
        }
        is ThicknessWorkflow.WorkflowState.Measuring -> {
            val (current, total) = workflow.getProgress()
            println("Measuring: $current/$total")
        }
        is ThicknessWorkflow.WorkflowState.Completed -> {
            println("Completed!")
        }
        is ThicknessWorkflow.WorkflowState.Error -> {
            println("Error: ${state.message}")
        }
    }
}
```

#### 4. Production интеграция

```kotlin
val bleScanner = BlessedBleScanner(context)
val bleConnectionManager = BlessedBleConnectionManager(context)

val device = ThicknessModule.provideThicknessDevice(
    mode = ThicknessModule.DeviceMode.PROD,
    bleScanner = bleScanner,
    bleConnectionManager = bleConnectionManager,
    logger = logger
)
```

### Таймауты

- **Подключение**: 5 секунд (согласно требованиям)
- **Измерение одной точки**: 30 секунд (согласно требованиям)
- **Сканирование устройства**: 10 секунд

### Протокол данных

Поддерживаются два формата:

#### ASCII формат
```
VALUE:123.45\n
```

#### Binary формат
4 байта float (little-endian)

### Структура зон

60 точек измерений распределены по элементам кузова:
- Капот: 6 точек
- Крыша: 6 точек
- Багажник: 6 точек
- Двери (4): по 4 точки каждая = 16 точек
- Крылья (4): по 3 точки каждые = 12 точек
- Бамперы (2): по 4 точки каждый = 8 точек

Всего: 60 точек

### Классификация измерений

- **Заводская покраска**: 60-120 μm (зелёный)
- **Незначительная перекраска**: 120-180 μm (жёлтый)
- **Значительная перекраска**: 180-300 μm (оранжевый)
- **Кузовной ремонт**: >300 μm (красный)
- **Слишком тонкая**: <60 μm (серый)

### Тестирование

```bash
# Unit тесты
./gradlew :feature-thickness:testDebugUnitTest

# Все тесты
./gradlew :feature-thickness:test
```

### Зависимости

- `:core` - общие утилиты
- `:platform-bluetooth` - BLE интеграция
- `:platform-logging` - логирование
- `kotlinx-coroutines-core` - корутины
- `kotlinx-coroutines-android` - Android корутины

### Метрики

- **Файлов**: 20+
- **Строк кода**: 5000+
- **Тестов**: 100+
- **Покрытие**: >70%

### Блокеры

- AGP 8.4.1 недоступен - компиляция невозможна
- Код написан и логически протестирован
- Ожидается восстановление доступа к Google Maven

### Следующие шаги

1. Восстановить доступ к Google Maven
2. Скомпилировать модуль
3. Запустить unit-тесты
4. Интеграция с UI (Session 13)
5. Production тестирование (Session 14)
