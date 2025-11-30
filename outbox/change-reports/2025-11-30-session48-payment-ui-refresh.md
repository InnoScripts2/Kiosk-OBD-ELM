# Change Report — 30.11.2025 (Session 48 — Payment UI refresh)

## Цели

- Синхронизировать экран QR-оплаты с фронтенд-макетом: ширинные ограничения, 12-колоночная сетка, честный 50/50 layout.
- Заменить экспериментальные FlowRow-компоненты на LazyVerticalGrid и адаптивные секции действий, сохранив тесты `PaymentCountdown`.

## Действия

1. `app/src/main/kotlin/com/selfservice/kiosk/ui/screens/PaymentQRScreen.kt`
   - Перестроен верхнеуровневый контейнер: ширина ограничена 1440 dp, прокрутка через `rememberScrollState`, стейт «Processing» отделён от остальных.
   - Добавлены `PaymentHeader`, `PaymentMetaGrid` и `PaymentMetaCard`: LazyVerticalGrid с supporting-текстами вместо FlowRow-чипов.
   - Панели `PaymentSummaryPanel` и `PaymentQrPanel` работают в адаптивном Row/Column (50/50 или стэк на узких экранах);
     блок действий выделен в `PaymentActionsSection` с responsive кнопками и DEV-баннером.
   - Создан helper `DevConfirmButton`, перенесены DEV подсказки и mock-баннер, чтобы управление тестовым подтверждением было единообразным в любой ширине.

## Команды

- `./gradlew :app:testDebugUnitTest :platform-ui:test`

## Результаты

- Gradle: SUCCESS (AGP 8.4.1 предупреждает о compileSdk 35, IC fallback не воспроизводился). 244 задачи, 19 выполнено.
- Payment UI повторяет фронтенд-ориентиры: заголовок с подсветкой, supporting-тексты, адаптивные карточки, responsive кнопки.

## Следующие шаги

- Продолжить выравнивание оставшихся экранов оплаты/инструкций и после завершения UI-паритета перейти к задачам Supabase/админ-панели.
