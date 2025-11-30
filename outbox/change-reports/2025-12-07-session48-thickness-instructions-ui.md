# Change Report — 07.12.2025 (Session 48 — Thickness Instructions screen)

## Цели

- Закрыть пробел между подготовкой устройства и самим процессом измерений, приведя инструкционный экран к фронтенд-макету с сеткой 8dp.
- Дать клиенту понятный порядок действий (meta-чипы, пошаговые карточки, рекомендации DO/DON'T) без отхода от дизайн-системы KioskTokens.
- Покрыть вспомогательную логику unit-тестами, чтобы дальнейшие Supabase/агентные изменения не ломали UX.

## Действия

1. `app/src/main/kotlin/com/selfservice/kiosk/ui/screens/ThicknessInstructionsScreen.kt`
   - Новый Compose-экран с meta-рядом (устройство, точки, время, статус), двумя рядами панелей (шаги, техника удержания, do/don't, зоны) и actions «Назад»/«Начать измерения».
   - Добавлены генераторы данных `buildInstructionMeta/Steps/Tips/ZoneFocus`, цветовые схемы для предупреждений и списки подсказок.
2. `app/src/test/kotlin/com/selfservice/kiosk/ui/screens/ThicknessInstructionsScreenTest.kt`
   - Юнит-тесты проверяют содержимое meta-данных, порядок шагов, наличие Do/Dont и подстановку общего количества точек.

## Команды

- `./gradlew :app:testDebugUnitTest :platform-ui:test`

## Результаты

- Gradle SUCCESS (244 задачи, 21 выполнено). Kotlin IC несколько раз откатился на полную компиляцию из-за блокировки `dirty-sources.txt` в Windows FS; сборка продолжилась автоматически. AGP 8.4.1 всё ещё предупреждает о compileSdk 35 (см. лог, подавлять не стали).
- Экран инструкций использует ту же систему токенов, что Payment/Prep/Measurement/Results, что закрывает все клиентские шаги в ветке Thickness до перехода к отчётам и Supabase.

## Следующие шаги

- Привязать экран к навигации устройств (после DevicePrep → Instructions → Measurements) и интегрировать состояния ViewModel.
- После полного паритета UI перейти к Supabase-синхронизации и админ-панели (Windows EXE + Android APK).
