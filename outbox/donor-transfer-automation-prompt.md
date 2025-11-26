# Автоматический перенос донорских проектов (рес 1–рес 7)

**Контекст.** Все исходники доноров лежат в `C:\Users\Alexsey\Desktop\My project\рес N`. Рабочий монорепозиторий Android находится в `C:\Users\Alexsey\Desktop\My project\android`. Код доноров патчится *только* после переноса в `android/`. Допускается создавать недостающие каталоги в `android/`, но любые изменения внутри `рес N` запрещены. Все команды выполняются из корня репозитория `C:\Users\Alexsey\Desktop\My project`.

**Жёсткие ограничения.**
1. Используй только команды копирования/перемещения файлов и создания директорий: `robocopy`, `copy`, `move`, `xcopy`, `mkdir`. Любые другие команды (редакторы, утилиты архивации, git) запрещены.
2. Запускай `robocopy` с ключами `/E /COPYALL /NFL /NDL /NP /R:1 /W:1 /XD .git .github .gradle build gradle .idea .run .vscode node_modules release` и `/XF *.iml *.bat *.sh *.cmd`. Это исключит технический мусор.
3. Минимальный объём сессии — **500 уникальных файлов**. Подсчитывай прогресс по каждому `robocopy`-логу (см. поле `Files :`) и веди суммарный счётчик.
4. После копирования *не удаляй* оригиналы. Только фиксируй в отчёте, какие файлы перенесены и куда.
5. После завершения каждой пачки копирования создавай нужные директории в `android/` (если отсутствуют) и сразу же проверяй содержимое `dir /s /b <target>` на ожидаемые файлы.

---

## Карта источников и назначений

| Донор                                   | Источник                                                                                                                            | Назначение                                                                                            | Примечания                                                                                                                                   |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| **рес 1** (QRCode-Kotlin, сейчас пусто) | `рес 1\рес 1` (если появится)                                                                                                       | `android\platform\camera\qrcode` и `android\feature-payments\src\main\java` (генерация QR для оплаты) | На момент запуска каталог пуст. Проверь `dir рес 1` и пропусти, если файлов нет.                                                             |
| **рес 2** (Kiosk Launcher)              | `рес 2\app\src\main\java`                                                                                                           | `android\feature-kiosk-mode\src\main\java`                                                            | Перенести DeviceAdminReceiver, BootReceiver, KioskAccessibilityService, RestartScheduler.                                                    |
|                                         | `рес 2\app\src\main\res`                                                                                                            | `android\app\src\main\res`                                                                            | Только ресурсы (layout, xml, drawable). Оставь `mipmap` и иконки для ручного мерджа — положи их в `android\app\src\main\res-kiosk-launcher`. |
| **рес 3** (KasirPraktis)                | `рес 3\app\src\main\java`                                                                                                           | `android\feature-payments\src\main\java`                                                              | Экраны оплаты, корзины, QR.                                                                                                                  |
|                                         | `рес 3\app\src\main\res`                                                                                                            | `android\app\src\main\res-payments`                                                                   | Создай папку `res-payments` в `android/app/src/main` для временного хранения ресурсов.                                                       |
| **рес 4** (Kable BLE)                   | `рес 4\src\main\java` и `рес 4\src\main\kotlin`                                                                                     | `android\platform\bluetooth\kable` и `android\feature-obd-core\src\main\java\kable`                   | Скопируй BLE-ядро, менеджеры соединений, константы.                                                                                          |
| **рес 5** (FlowExt)                     | `рес 5\FlowExt-master\src\commonMain` и соседние sourceSets                                                                         | `android\platform\ui\flowext`                                                                         | Скопируй компоненты JetBrains Compose, адаптивные макеты, примеры.                                                                           |
| **рес 6** (Reaktive)                    | `рес 6\Reaktive-master\reaktive` `reaktive-annotations` `reaktive-testing` `coroutines-interop` `rxjava2-interop` `rxjava3-interop` | `android\platform\bluetooth\reaktive` и `android\feature-obd-core\src\main\java\reaktive`             | Каждую папку копируй отдельно, сохраняя структуру `src`.                                                                                     |
| **рес 7** (supabase-kt)                 | `рес 7\supabase-kt-master\Supabase` `Postgrest` `Realtime` `Storage` `Auth` `Functions`                                             | `android\platform\data\supabase` и `android\feature-reports\src\main\java\supabase`                   | Эти модули нужны для оффлайн/edge-кеша и отчётов.                                                                                            |

---

## Последовательность действий для агента

1. `cd C:\Users\Alexsey\Desktop\My project`.
2. Для каждого донорского блока выполни `robocopy` по таблице выше. Перед запуском убедись, что целевая папка существует (`if not exist mkdir`).
   - Пример: `robocopy "рес 2\app\src\main\java" "android\feature-kiosk-mode\src\main\java" /E ...`.
3. После каждой копии выполняй `dir /s /b <target> | measure` (или `Get-ChildItem`) чтобы убедиться, что файлы появились, и запиши количество файлов в итоговый отчёт.
4. Когда суммарно будет ≥500 файлов (по полю `Files :` из логов), зафиксируй достижение квоты.
5. Никаких изменений в Gradle или исходниках не вноси.
6. Финальный отчёт:
   - перечисли выполненные `robocopy` команды и число перенесённых файлов;
   - подтверди, что никакие другие команды не использовались;
   - зафиксируй, какие каталоги ещё остались неперенесёнными.

Следуй строго этой инструкции. Если встречаются ошибки доступа, логируй сообщение и переходи к следующей цели, чтобы не блокировать квоту.

---

## Продолжение (сессия 2)

Первая автоматическая сессия завершилась превышением квоты (1859 файлов), но агент отклонился от требований: использовал `rsync` вместо `robocopy`, не прикладывал `dir /s /b`-логи и не перечислил фактические команды. Настоящий промпт запускает **корректирующую волну**, которая должна:

1. Повторно перенести указанные ниже каталоги строго разрешёнными командами (`robocopy`, `xcopy`, `copy`, `move`, `mkdir`). Никаких `rsync`, `tar`, `zip` и т. п.
2. После каждого `robocopy` сразу выполнять `dir /s /b <target> | measure` и сохранять вывод в отчёт.
3. В финальном отчёте явно перечислить каждую выполненную команду и подтверждение, что ничего кроме разрешённых операций не запускалось.
4. Снова выполнить квоту **≥500 файлов** (можно больше, но минимум 500 счётчиком `Files :`).

### Целевые каталоги для повторного переноса

| Донор                      | Источник                                                                                                         | Назначение                                                                                | Замечания                                                                                                                  |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **рес 2** (Kiosk Launcher) | `рес 2\app\src\main\java` (папки `com/.../receiver`, `service`, `scheduler`)                                     | `android\feature-kiosk-mode\src\main\java`                                                | Повторить перенос DeviceAdminReceiver/BootReceiver/KioskAccessibilityService/RestartScheduler. Сохраняй структуру пакетов. |
| **рес 2**                  | `рес 2\app\src\main\res\mipmap*`                                                                                 | `android\app\src\main\res-kiosk-launcher\mipmap*`                                         | Переместить иконки отдельно, чтобы позже объединить вручную.                                                               |
| **рес 3** (KasirPraktis)   | `рес 3\app\src\main\java` (пакеты `id/.../cart`, `payment`, `report`)                                            | `android\feature-payments\src\main\java`                                                  | Нужны экраны корзины/оплаты + отчёты.                                                                                      |
| **рес 3**                  | `рес 3\app\src\main\res` (кроме `mipmap`)                                                                        | `android\app\src\main\res-payments`                                                       | Layout, values, drawable. Создай каталог, если отсутствует.                                                                |
| **рес 4** (Kable BLE)      | `рес 4\src\main\kotlin`                                                                                          | `android\platform\bluetooth\kable`                                                        | Повторно скопируй BLE-ядро (manager, observer, logger).                                                                    |
| **рес 5** (FlowExt)        | `рес 5\FlowExt-master\src\commonMain`                                                                            | `android\platform\ui\flowext\src\commonMain`                                              | Компоненты Compose/Flow.                                                                                                   |
| **рес 6** (Reaktive)       | `рес 6\Reaktive-master\reaktive` + соседние модули (`reaktive-testing`, `coroutines-interop`, `rxjava*-interop`) | `android\platform\bluetooth\reaktive` и `android\feature-obd-core\src\main\java\reaktive` | Каждую папку копируй отдельной командой, учитывая структуру `src`.                                                         |
| **рес 7** (supabase-kt)    | `рес 7\supabase-kt-master\Supabase` `Postgrest` `Realtime` `Storage` `Auth` `Functions`                          | `android\platform\data\supabase` и `android\feature-reports\src\main\java\supabase`       | Требуются клиентские SDK для оффлайн-режима.                                                                               |

### Порядок действий для сессии 2

1. `cd C:\Users\Alexsey\Desktop\My project`.
2. Для каждого источника из таблицы:
   - `if not exist <target> mkdir <target>`
   - `robocopy "<source>" "<target>" /E /COPYALL /NFL /NDL /NP /R:1 /W:1 /XD .git .github .gradle build gradle .idea .run .vscode node_modules release /XF *.iml *.bat *.sh *.cmd`
   - `dir /s /b <target> | measure`
   - Выписать в журнал значения `Files :` и результат `dir`.
3. Вести суммарный счётчик перенесённых файлов, пока не достигнем ≥500.
4. Финальный отчёт должен содержать:
   - Таблицу `команда → Files : → проверка dir`.
   - Подтверждение, что использовались **только** перечисленные команды + `dir`.
   - Список каталогов, которые ещё не перенесены или требуют следующей волны.

**Важно.** Эта сессия не занимается интеграцией в Gradle. После копирования не изменяй `settings.gradle.kts`, `build.gradle.kts` и т. д. Интеграцию оформим в отдельной задаче.

---

## Настройка и адаптация перенесённых файлов (сессия 3)

После того как вся масса исходников доноров оказалась в `android/`, необходимо привести их к требованиям монорепозитория, чтобы код начал собираться и соответствовал инструкциям §4.1–4.3, §9–§15. Настоящий промпт описывает **только** этап адаптации. Перед запуском убедись, что сессия 2 (повторное копирование) полностью завершена и зафиксирована в логах.

### Общие правила адаптации

1. Все изменения выполняются внутри `android/` (и при необходимости в `03-apps/02-application/kiosk-shell/agent`, если нужны синхронизированные схемы). Донорские каталоги `рес N` остаются read-only.
2. Любая новая директория/модуль должна быть зарегистрирована в `settings.gradle.kts`, `settings.gradle` дочерних проектов и (если нужно) в `gradle/libs.versions.toml`.
3. Пакеты и namespace приводим к `com.selfservice.*` или существующим пакетам модулей (`com.selfservice.kiosk`, `com.selfservice.platform.bluetooth`, и т. п.). Запрещены остаточные названия вроде `com.pnuema`, `id.co...`, `com.welie` без обёрток.
4. Перед любым коммитом — `cd android && ./gradlew clean lint detekt test assembleDebug`. Ошибки устраняем в этой сессии.
5. Любые новые зависимости документируем в `docs/tech/architecture.md` и отражаем в `android/scripts/session-05-archive-plan.ps1` (столбец “utilized components”).

### Конкретные задачи

| Блок                                                       | Действия                                                                                                                                                                                                                                                                            | Ожидаемый результат                                                     |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `feature-kiosk-mode` (рес 2)                               | 1) Добавить DeviceAdminReceiver/BootReceiver/KioskAccessibilityService в `AndroidManifest.xml` модуля и главного `app`. 2) Настроить DI/entry points (Hilt/ServiceLocator запрещён) — используем Hilt-модули в `core`. 3) Обновить пакеты файлов на `com.selfservice.kiosk.mode.*`. | Модуль компилируется, сервисы зарегистрированы, lint/Detekt без ошибок. |
| `android/app` ресурсы (рес 2)                              | Слить `res-kiosk-launcher` и `res-payments` с основными ресурсами: вручную проверить конфликты имён, иконки переместить в подпапку `mipmap-kiosk`.                                                                                                                                  | Нет дублирующих имён, `./gradlew lint` проходит.                        |
| `feature-payments` (рес 3)                                 | 1) Настроить Gradle зависимости (Compose, Room, networking). 2) Сконвертировать Koin/Hilt или другие DI паттерны в текущую архитектуру. 3) Добавить unit-тесты для корзины, QR-оплаты, отчётов.                                                                                     | Модуль подключён в app, тесты зелёные.                                  |
| `platform/bluetooth/kable` и `feature-obd-core` (рес 4)    | 1) Создать Kotlin Multiplatform-friendly структуру (если нужно) или ограничиться Android sourceSet. 2) Подключить к `ObdConnectionManager`. 3) Перенести логику логирования на существующий `Logger`.                                                                               | BLE-ядро интегрировано, OBD сервисы используют kable.                   |
| `platform/ui/flowext` (рес 5)                              | 1) Зарегистрировать модуль как Compose UI kit. 2) Переписать namespace. 3) Добавить snapshot-тесты или Compose тесты согласно инструкции.                                                                                                                                           | FlowExt доступен как библиотека UI, тесты выполняются.                  |
| `platform/bluetooth/reaktive` + `feature-obd-core` (рес 6) | 1) Конвертировать Gradle скрипты (Kotlin DSL). 2) Прописать зависимости на coroutines. 3) Удалить/заменить `rxjava*` если не используется, либо оформить адаптеры.                                                                                                                  | Reaktive-компоненты собираются, интегрированы в pipeline OBD.           |
| `platform/data/supabase` + `feature-reports` (рес 7)       | 1) Создать конфигурацию Supabase (переменные окружения вынести в `.env`+`credential-inventory`). 2) Обновить схемы данных (Kotlin serialization) под локальные модели отчётов. 3) Добавить интеграционные тесты (можно с mock server).                                              | Supabase клиент собирается, feature-reports получает необходимые API.   |

### Детальный сценарий

1. **Регистрация модулей:**
   - `android/settings.gradle.kts`: добавить `include("feature-kiosk-mode")`, `include("feature-payments")`, `include("feature-obd-elm-port")`, `include("platform:bluetooth:kable")`, `include("platform:bluetooth:reaktive")`, `include("platform:data:supabase")`, `include("platform:ui:flowext")` и т. д. Проверить, что пути соответствуют фактической структуре.
   - Внутри каждого модуля создать `build.gradle.kts` (если отсутствует) по образцу существующих feature/platform модулей.

2. **Gradle зависимости:**
   - Обновить `android/gradle/libs.versions.toml`, добавив библиотеки (Supabase, kotlinx-serialization, FlowExt, Reaktive). Использовать существующие версии Kotlin/Compose.
   - В `build.gradle.kts` модулей включить необходимые плагины (`kotlin-android`, `kotlin-kapt`, `kotlin-serialization`).

3. **Переименование пакетов:**
   - Массово заменить имена пакетов через IDE или скрипты, чтобы соответствовали структуре монорепо. Переименовать ресурсы (`res/values/strings.xml`) так, чтобы не конфликтовали.

4. **Интеграция бизнес-логики:**
   - Для payments: подключить экраны к навигации `feature-payments` ↔ `app`. Удалить legacy DI, задокументировать сервисы в `docs/tech/payment-module.md` (создать, если отсутствует).
   - Для OBD: внедрить Reaktive/kable в `ObdConnectionManager`, добавить тесты (`feature-obd-core/src/test`).
   - Для Supabase: связать `platform/data/supabase` с отчётами `feature-reports`, обновить `ThicknessReportSupabaseSchema` и сопутствующие тесты.

5. **Тестирование и линт:**
   - `cd android && ./gradlew lint detekt test assembleDebug`.
   - При необходимости запустить `npm --prefix 03-apps/.../kiosk-shell/agent run lint` если Supabase схемы синхронизируются с агентом.

6. **Документация и реестр:**
   - Обновить `plan-80-session-roadmap.md` (прогресс сессии 3).
   - В `android/scripts/session-05-archive-plan.ps1` пометить используемые подсекции доноров как “utilized”.
   - В `09-docs/02-application/security/credential-inventory.md` внести Supabase ключи (без значений).

7. **Финальный отчёт:**
   - Описать произведённые изменения, тесты и вес APK (ожидается ≥60 MB, фиксировать фактическое значение).
   - Перечислить модули, которые требуют дополнительных задач (например, если часть Reaktive всё ещё не адаптирована).

Следуй строго этому сценарию. Если при адаптации появляются блокеры (несовместимые версии, отсутствующие зависимости), фиксируй их в отчёте и переходи к следующему модулю, чтобы не блокировать всю сессию.
