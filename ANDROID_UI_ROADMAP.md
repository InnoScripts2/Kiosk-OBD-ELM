# Android UI Roadmap — Киоск самообслуживания

## Текущее состояние

✅ **Инфраструктура данных готова**:
- DTC каталог (17,958 generic + 58,217 manufacturer entries)
- `DtcDataLoader` для загрузки каталогов
- `DtcCatalogVersion` для отслеживания версий
- Gradle task для автоматической генерации
- Python тесты + Kotlin unit тесты

❌ **UI слой не реализован**:
- Нет Activities/Fragments для экранов киоска
- Нет интеграции DTC каталога с UI
- Нет навигации между экранами

## Технологический выбор UI

### Вариант 1: Jetpack Compose (Рекомендуется) ✅

**Преимущества:**
- Современный declarative UI
- Меньше boilerplate кода
- Лучше для touch интерфейсов
- Проще тестирование UI
- Compose Material 3 для современного дизайна

**Недостатки:**
- Требует Kotlin опыта
- Может быть overhead для простых экранов

### Вариант 2: XML Layouts + ViewBinding

**Преимущества:**
- Традиционный подход
- Легче для новичков
- Визуальный редактор в Android Studio

**Недостатки:**
- Больше boilerplate
- Сложнее динамические UI

**Решение**: Используем **Jetpack Compose** для нового UI

## План реализации UI

### Phase 1: Setup Compose Infrastructure

**Задачи:**
- [ ] Добавить Compose зависимости в `app/build.gradle.kts`
- [ ] Создать `ui` пакет в `app/src/main/kotlin/com/selfservice/kiosk/ui/`
- [ ] Настроить тему Material 3 для киоска
- [ ] Создать базовые компоненты (KioskButton, KioskCard, KioskTextField)

**Зависимости для добавления:**
```kotlin
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.activity.compose)
implementation(libs.androidx.navigation.compose)
debugImplementation(libs.androidx.compose.ui.tooling)
```

### Phase 2: Core Screens

#### 2.1 AttractScreen (Экран привлечения)

**Путь**: `app/src/main/kotlin/com/selfservice/kiosk/ui/attract/AttractScreen.kt`

**Функциональность:**
- Показывает логотип и слоган
- Полноэкранный gradient background
- Обработка касания → переход на Welcome
- Анимация pulse для призыва к действию

**Compose структура:**
```kotlin
@Composable
fun AttractScreen(onNavigateToWelcome: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(/* gradient */)
            .clickable { onNavigateToWelcome() }
    ) {
        Column(/* logo + text */)
    }
}
```

#### 2.2 WelcomeScreen (Приветствие и согласие)

**Путь**: `app/src/main/kotlin/com/selfservice/kiosk/ui/welcome/WelcomeScreen.kt`

**Функциональность:**
- Краткое введение (2-3 предложения)
- Checkbox с пользовательским соглашением
- Кнопка "Продолжить" (активна только при согласии)
- Scrollable текст соглашения

**Состояние:**
```kotlin
@Composable
fun WelcomeScreen(
    onNavigateToServices: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }
    
    // UI implementation
}
```

#### 2.3 ServiceSelectionScreen (Выбор услуги)

**Путь**: `app/src/main/kotlin/com/selfservice/kiosk/ui/services/ServiceSelectionScreen.kt`

**Функциональность:**
- Две карточки: Толщинометр и Диагностика
- Название, описание, цена, иконка
- Кнопка "Выбрать" на каждой карточке
- Кнопка "Назад"

**Модель данных:**
```kotlin
data class KioskService(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val benefits: List<String>,
    val price: String,
    val route: String
)
```

#### 2.4 DiagnosticsScreen (OBD Диагностика)

**Путь**: `app/src/main/kotlin/com/selfservice/kiosk/ui/diagnostics/DiagnosticsScreen.kt`

**Функциональность:**
- Выбор марки автомобиля
- Ввод контактов (телефон, email)
- Подготовка адаптера
- Прогресс сканирования (0-100%)
- **Отображение DTC кодов с расшифровкой из каталога**
- Кнопка "Сбросить ошибки" (Clear DTC)

**Интеграция с DTC каталогом:**
```kotlin
@Composable
fun DiagnosticsResultsScreen(
    dtcCodes: List<String>,
    manufacturer: String
) {
    val database = remember {
        DtcDataLoader.loadDatabase(
            LocalContext.current.assets,
            "dtc_database.json"
        )
    }
    
    LazyColumn {
        items(dtcCodes) { code ->
            val dtcEntry = database.find(manufacturer, code)
            DtcCodeCard(
                code = code,
                description = dtcEntry?.description ?: "Unknown DTC",
                severity = determineSeverity(code)
            )
        }
    }
}
```

### Phase 3: Navigation & State Management

**Задачи:**
- [ ] Настроить Compose Navigation
- [ ] Создать `KioskNavHost` с маршрутами
- [ ] Реализовать ViewModel для каждого экрана
- [ ] Добавить StateFlow для управления состоянием

**Структура навигации:**
```kotlin
@Composable
fun KioskNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "attract"
    ) {
        composable("attract") { 
            AttractScreen(
                onNavigateToWelcome = { 
                    navController.navigate("welcome") 
                }
            )
        }
        composable("welcome") { 
            WelcomeScreen(
                onNavigateToServices = { 
                    navController.navigate("services") 
                }
            )
        }
        composable("services") { 
            ServiceSelectionScreen(/* ... */) 
        }
        composable("diagnostics") { 
            DiagnosticsScreen(/* ... */) 
        }
    }
}
```

### Phase 4: OBD Integration

**Задачи:**
- [ ] Интегрировать `feature-obd-core` с UI
- [ ] Реализовать `DiagnosticsViewModel`
- [ ] Связать OBD команды с UI состояниями
- [ ] Отображать реальные DTC коды
- [ ] Реализовать Clear DTC функционал

**ViewModel пример:**
```kotlin
class DiagnosticsViewModel(
    private val obdRepository: ObdRepository,
    private val dtcDatabase: ManufacturerDtcDatabase
) : ViewModel() {
    
    private val _dtcCodes = MutableStateFlow<List<DtcCode>>(emptyList())
    val dtcCodes: StateFlow<List<DtcCode>> = _dtcCodes.asStateFlow()
    
    fun scanForDtcCodes(manufacturer: String) {
        viewModelScope.launch {
            val codes = obdRepository.readDtcCodes()
            val enriched = codes.map { code ->
                val entry = dtcDatabase.find(manufacturer, code)
                DtcCode(
                    code = code,
                    description = entry?.description ?: "Unknown",
                    severity = determineSeverity(code)
                )
            }
            _dtcCodes.value = enriched
        }
    }
    
    fun clearDtcCodes() {
        viewModelScope.launch {
            obdRepository.clearDtcCodes()
            _dtcCodes.value = emptyList()
        }
    }
}
```

### Phase 5: Testing & Polishing

**Задачи:**
- [ ] Добавить Compose UI тесты
- [ ] Добавить Screenshot тесты
- [ ] Провести accessibility audit
- [ ] Оптимизировать производительность
- [ ] Добавить error handling

**Compose тесты пример:**
```kotlin
@Test
fun welcomeScreen_continueButton_disabledWhenNotAgreed() {
    composeTestRule.setContent {
        WelcomeScreen(onNavigateToServices = {})
    }
    
    composeTestRule
        .onNodeWithText("Продолжить")
        .assertIsNotEnabled()
}
```

## Структура модулей

```
android/
├── app/
│   └── src/main/kotlin/com/selfservice/kiosk/
│       ├── ui/
│       │   ├── theme/
│       │   │   ├── Color.kt
│       │   │   ├── Theme.kt
│       │   │   └── Type.kt
│       │   ├── components/
│       │   │   ├── KioskButton.kt
│       │   │   ├── KioskCard.kt
│       │   │   └── KioskTextField.kt
│       │   ├── attract/
│       │   │   └── AttractScreen.kt
│       │   ├── welcome/
│       │   │   └── WelcomeScreen.kt
│       │   ├── services/
│       │   │   └── ServiceSelectionScreen.kt
│       │   └── diagnostics/
│       │       ├── DiagnosticsScreen.kt
│       │       ├── DiagnosticsViewModel.kt
│       │       └── DtcCodeCard.kt
│       └── navigation/
│           └── KioskNavHost.kt
├── feature-obd-core/      # DTC каталог + OBD логика
└── platform/data/         # DtcDataLoader + версионирование
```

## Дизайн система (Material 3)

### Цвета

```kotlin
val KioskPrimary = Color(0xFF0066CC)      // Основной синий
val KioskSecondary = Color(0xFF00AA66)    // Акцентный зелёный
val KioskError = Color(0xFFCC0000)        // Ошибки
val KioskWarning = Color(0xFFFF9900)      // Предупреждения
val KioskSuccess = Color(0xFF00AA00)      // Успех
```

### Типографика

Все размеры оптимизированы для touch интерфейса (≥ 44dp tap target):

```kotlin
val KioskTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
)
```

## Timeline

- **Week 1**: Setup Compose + Theme + Basic Components
- **Week 2**: Attract, Welcome, Services screens
- **Week 3**: Diagnostics screen + DTC integration
- **Week 4**: Testing + Polishing

## Зависимости в libs.versions.toml

Добавить:
```toml
[versions]
compose = "1.6.0"
composeMaterial3 = "1.2.0"
composeActivity = "1.8.2"
composeNavigation = "2.7.7"

[libraries]
androidx-compose-ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "composeMaterial3" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "compose" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview", version.ref = "compose" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "composeActivity" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "composeNavigation" }
```

## Следующий шаг

Начать с Phase 1: Setup Compose Infrastructure
