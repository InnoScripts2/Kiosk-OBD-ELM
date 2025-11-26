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
