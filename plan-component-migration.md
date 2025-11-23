# План миграции внешних компонентов в Android монорепозиторий

## Цели и ограничения
- Сконсолидировать рабочий код в каталоге `android/`, минимизируя количество «внешних» форков в корне репозитория.
- Поддержать требования плана 80 сессий: сохранение структуры модулей `app`, `core`, `feature-*`, `platform`, а также выполнение задач фазы B (диагностика) параллельно с подготовкой платежного и лаунчер-контуров.
- Сохранять воспроизводимость сборок: каждая миграция сопровождается unit-тестами/инструментальными тестами и фиксацией метрик размера APK.
- Любая интеграция чувствительных SDK (платежи) проходит через `plan-secrets-config.md` + placeholder переменные `.env`/`local.properties`.

## Исходные активы
| Каталог                                                                                            | Технологии                                     | Назначение                                                               | Планы по встраиванию                                                                                                                                  |
| -------------------------------------------------------------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AndroidOBD-main/AndroidOBD-main`                                                                  | Kotlin/Java, Gradle, модуль `obd`              | Современная библиотека ELM327 (инициализация, PID-команды, формулы).     | Стать основой `feature-obd-core` и `feature-obd-diagnostics`: перенос `ObdInitSequence`, `OBDCommand`, формул PID как reference-тестов.               |
| `obd-master`                                                                                       | Java, Gradle, модуль `obd` + sample `app`      | Библиотека с USB/Bluetooth транспортами и async-API.                     | Извлечь транспортные драйверы и примеры AsyncCommand для тестов `feature-obd-core`, утилизировать sample-приложение (только архивный артефакт).       |
| `Kiosk-Launcher-main/Kiosk-Launcher-main`                                                          | Android Java, kiosk mode, системные разрешения | Полноэкранный лаунчер с блокировкой статус-бара и расписанием рестартов. | Встроить как модуль `feature-kiosk-mode` (уже существует) + перенести политики DeviceAdmin, расписание рестартов и настройку whitelists.              |
| `yookassa-android-sdk-master/yookassa-android-sdk-master`                                          | Kotlin, Gradle, платежное SDK                  | SDK YooKassa/YooMoney: токенизация, 3DS, Google Pay, SberPay.            | Подключить в `feature-payments` как внешнюю зависимость через MavenCentral; вынести пример интеграции в `app` и `03-apps/02-application/kiosk-shell`. |
| `kable-main` (JetBrains)                                                                           | Kotlin Multiplatform BLE                       | BLE-стек (Kotlin Flow)                                                   | Использовать как источник примеров для переезда BLE-транспорта в `feature-obd-core` (ч. 2 плана), не встраивая весь проект.                           |
| `Kiosk-Launcher-main`, `KasirPraktis-master`, `blessed-kotlin-main`, `yookassa-android-sdk-master` | Разнородные проекты                            | Содержат UI/UX паттерны и интеграции, которые нужно декомпозировать.     | Каждый компонент мигрируется отдельной частью, см. ниже.                                                                                              |

## Части миграции

### Часть 1 — Аудит и подготовка (Сессии 21–22 Roadmap)
1. **Каталогизация кода**
   - Финализировать перечень папок/branch, зафиксировать SHA в `session-logs/`.
   - Добавить в `plan-inventory.md` таблицу «Источник → целевой модуль → тесты».
2. **Вспомогательные скрипты**
   - Создать `android/tools/migrate-component.ps1` с dry-run и проверкой лицензий.
3. **Лицензии и авторские права**
   - Для каждого источника собрать SPDX-метаданные в `plan-deps-licenses.md`.
4. **Гейты**
   - Gradle: `./gradlew :app:lintDebug :core:test`.
   - Node-agent: smoke `npm --prefix 03-apps/02-application/kiosk-shell/agent test` (проверка отчётного контура по окончании части).

### Часть 2 — Диагностические библиотеки (Фаза B, сессии 13–15 продолжение)
1. **Перенос ядра ELM (`AndroidOBD-main`)**
   - Создать модуль `android/feature-obd-elm-port` (временный) для «сырого» импорта классов `ObdModes`, `PIDUtils`, `OBDCommand`.
   - Написать адаптеры к уже существующим `PidFormulaEvaluator`, чтобы переиспользовать формулы как golden-тесты.
   - [x] Добавлен `ElmPidCatalogParityTest`, который сравнивает каталоги режимов 01/04/09 и формулы донорских PID с каноническими `PidCatalog`/`PidConversions` (session 29). Тест использует `PidFormulaEvaluator` для проверки эквивалентности формул вне зависимости от порядка операций и требует обновления при каждом ingest-е.
   - Покрыть unit-тестами: `:feature-obd-elm-port:testDebugUnitTest` + интеграция в `feature-obd-diagnostics` (обновить `StandardPidsTest`).
2. **USB/Bluetooth транспорты (`obd-master`)**
   - Выделить `UsbTransportDriver`, `BluetoothRfcommTransport` в `feature-obd-core` через common-интерфейсы `ObdTransport`.
   - Перенести async API как тестовые двойники для `ObdConnectionManagerTest` (coverage каналы/Retry).
3. **Доп. инструменты**
   - Сгенерировать Kotlin wrappers для AT-команд и ISO-TP smoke (подпункт сессии 14).
4. **Гейт**
   - `./gradlew :feature-obd-core:test :feature-obd-diagnostics:test` + watchdog smoke `android/tools/pass-thru-smoke.ps1` (DEV адаптер).

### Часть 3 — Kiosk shell и лаунчер (Фаза D зависимость, но подготовить заранее)
1. **Политики Device Owner**
   - Вынести из `Kiosk-Launcher-main` реализации `DeviceAdminReceiver`, расписание рестартов и блокировки кнопок в `feature-kiosk-mode`.
   - Добавить unit-Тесты на `KioskPolicyController` и e2e Espresso на автозапуск.
2. **UI/UX компоненты**
   - Привести стили лаунчера к Material 3 и подключить к текущему `MainActivity` через флаг DEV_MODE.
3. **Гейт**
   - `./gradlew :feature-kiosk-mode:testDebugUnitTest :app:connectedDebugAndroidTest`.

### Часть 4 — Платежи и безопасность (Фаза E, перекрывает текущую сессию 22)
1. **YooKassa SDK**
   - Перевести пример интеграции в `feature-payments`: обернуть `Checkout.createTokenizeIntent` в `PaymentsJavascriptBridge`.
   - Настроить mock режимы для скриншотов (см. README SDK) + добавить smoke в `app/src/test`.
2. **Секреты и конфигурация**
   - Обновить `plan-secrets-config.md`: добавить `YOOKASSA_CLIENT_APP_KEY`, `YOOKASSA_SHOP_ID`, `YOOKASSA_RETURN_URL` с плейсхолдерами `.env`.
3. **MDM/OTA зависимость**
   - Связать платежный контур с лаунчер-режимом (при падении оплаты → возврат в kiosk-home).
4. **Гейт**
   - `./gradlew :feature-payments:testDebugUnitTest` + `npm --prefix 03-apps/02-application/kiosk-shell/agent test -- run payments` (при наличии).

### Часть 5 — Рефакторинг и архивирование источников
1. После миграции каждого компонента переместить исходный каталог в `archives/` с README «migrated in session NN».
2. Автоматически обновлять `plan-reuse-rework.md` с экономией LOC/MB.
3. Финальный проход `./gradlew lint test assembleDebug --build-cache` и сравнение веса APK.

## Следующие действия (ближайшие 2–3 сессии)
1. Завершить фичу с кэшем PID (сессия 13) — текущая работа в `feature-obd-diagnostics`.
2. Подготовить заготовку модуля `feature-obd-elm-port` и smoke-тест для USB транспорта.
3. Согласовать с безопасностью требования к YooKassa и зафиксировать placeholders в `.env`.
4. Одновременно вести документацию в `plan-80-session-roadmap.md` + `session-logs/` (без расширения общих текстов, только техники).
