# План консолидации языков и артефактов в `android/`

Документ фиксирует стратегию по объединению всей активной разработки в каталоге
`android/` и управлению смешанным стеком (Kotlin + TS/JS/TSX/CJS + PowerShell + INO).
Обновляется при каждой миграции или добавлении новых языков.

## 1. Общие правила
- Единственный источник правды: `android/`. Любой рабочий код, скрипт, шейдер,
  схема или инструкция должны находиться внутри соответствующего модуля
  Android-монорепо.
- Внешние директории (`03-apps/`, `packages/`, `infra/`, `archives/рес*` и т. п.)
  рассматриваются как доноры. После переноса содержимого они помечаются "utilized"
  и вычищаются.
- Новые языки подключаются только если они обслуживают функции киоска. Для каждой
  связки указывается Gradle-таска, которая собирает артефакты внутри `android/`.

## 2. Текущий стек и целевые директории
| Язык / технология           | Основное назначение                               | Целевой модуль внутри `android/`                              | Статус                                       | Дата обновления |
| --------------------------- | ------------------------------------------------- | ------------------------------------------------------------- | -------------------------------------------- | --------------- |
| Kotlin (KMP/JVM)            | Все основные фичи приложения                      | `app`, `core`, `feature-*`, `platform/*`                      | ✅ Уже в `android/`                          | —               |
| TypeScript / TSX / React    | Веб-компоненты, UI-песочницы, dev-tools           | `android/platform-ui/web/` (новый модуль)                     | 🔄 Структура создана, требуется миграция      | 24.11.2025      |
| JavaScript / CJS            | Скрипты вспомогательных агентов, Electron-обвязка | `android/platform-ui/web/legacy/` или `android/scripts/node/` | 🔄 Структура создана, требуется миграция      | 24.11.2025      |
| PowerShell (PS1)            | DevOps/CI/мониторинг                              | `android/scripts/powershell/`                                 | 🔄 Структура создана, частично в `infra/`    | 24.11.2025      |
| INO (Arduino)               | Прошивки замков/реле                              | `android/hardware/arduino/`                                   | ✅ Файлы перенесены из корня `android/`       | 24.11.2025      |
| Shell/Bash                  | Системные утилиты, проверки Maven                 | `android/scripts/shell/`                                      | 🔄 Структура создана, требуется миграция      | 24.11.2025      |

## 3. План миграции по волнам
### Волна A — UI и Node-инфраструктура
1. `03-apps/02-application/kiosk-shell/agent/` → `android/platform-ui/web/agent/`.
   - Создать Gradle-таску, вызывающую `npm run build` внутри нового каталога.
   - Настроить зависимости так, чтобы бинарники/артефакты складывались в
     `android/platform-ui/web/dist/`.
2. `03-apps/02-application/kiosk-agent/` (актуальный ESM агент) →
   `android/platform-ui/web/kiosk-agent/`.
   - Прописать README с указанием родительского Android-модуля.
3. Удалить/архивировать старые пути после успешных сборок и обновления CI.

### Волна B — DevOps и PowerShell
1. `infra/scripts/` → `android/scripts/powershell/` и `android/scripts/shell/`.
   - Каждому скрипту сопоставить Gradle-таску (`tasks.register("runFoo")`).
   - Обновить GitHub Actions/локальные инструкции на новые пути.
2. Убедиться, что `.github/workflows/*` обращаются к скриптам по пути из `android/`.

### Волна C — Аппаратные компоненты
**Статус**: ✅ Частично выполнено (24.11.2025, Session 15G)

1. ✅ `android/dispencer.ino` и `android/dispenser.ino` → `android/hardware/arduino/`
   - Файлы перенесены из корня `android/` в подмодуль
   - Создан `README.md` с инструкциями по сборке (Arduino IDE + Arduino CLI)
   - Документирована связь с `LockController` из Node-агента
   - Схема подключения: см. `ARDUINO_DISPENCER_README.md`
   
2. ⏳ Планируется: Добавить Gradle-таску для компиляции/прошивки
   ```kotlin
   tasks.register<Exec>("compileArduino") {
       commandLine("arduino-cli", "compile", "--fqbn", "arduino:avr:uno",
           "hardware/arduino/dispenser.ino")
   }
   ```

3. ⏳ USB/GPIO-прототипы: перенести в `android/feature-lock-control` при появлении

### Волна D — Общие библиотеки и пакеты
1. `packages/` (device-obd, device-thickness, report, payments) → соответствующие
   `android/feature-*` или `android/platform/*` модули.
   - Для каждого пакета определить мишень: например, `packages/report` →
     `android/feature-reports/report-kit`.
2. После интеграции обновить Gradle settings и закрыть оригинальные каталоги.

## 4. Чеклист для каждой миграции
1. Определить исходный путь и целевой модуль в `android/`.
2. Запустить `./gradlew lint test assembleDebug` для подтверждения целостности.
3. Обновить документацию/README модуля и указать источник (донор) + дату переноса.
4. Добавить запись в таблицу выше (статус → «выполнено», дата, ответственный).
5. Удалить или архивировать исходную папку (если требуется оставить, то только чтение).
6. Добавить запись в `.github/agents/session-notes/` о том, что вне `android/`
   новых файлов не осталось.

## 5. Запрещено
- Создавать новые репозитории или каталоги вне `android/` без письменного решения
  владельца проекта.
- Поддерживать параллельные копии одного и того же модуля (например, две версии
  агента в разных папках).
- Запускать npm/PowerShell/Arduino сборки из каталогов вне `android/`.

## 6. Следующие шаги

### 6.0 Обновление структуры (24.11.2025, Session 15G) ✅

**Выполнено**:
- ✅ Создана структура каталогов:
  - `android/platform-ui/web/` — для TypeScript/React агентов
  - `android/scripts/powershell/` — для PowerShell скриптов DevOps
  - `android/scripts/shell/` — для bash/shell утилит
  - `android/hardware/arduino/` — для Arduino прошивок
- ✅ Перенесены INO файлы: `dispencer.ino`, `dispenser.ino` → `android/hardware/arduino/`
- ✅ Созданы README для каждого модуля с инструкциями и планами интеграции
- ✅ Обновлена таблица «Текущий стек и целевые директории» с датами и статусами

**Метрики Session 15G**:
- Директорий создано: 5
- Файлов перенесено: 2 (INO)
- README созданы: 4
- Строк документации: ~10,900

### 6.1 Волна A — UI и Node-инфраструктура (следующая задача)
- [ ] Перенос `03-apps/02-application/kiosk-shell/agent/` → `android/platform-ui/web/agent/`
- [ ] Перенос `03-apps/02-application/kiosk-agent/` → `android/platform-ui/web/kiosk-agent/`
- [ ] Создание Gradle-тасок для `npm run build` и `npm test`
- [ ] Настройка артефактов в `android/platform-ui/web/dist/`
- [ ] Обновление CI workflows для работы с новыми путями
- [ ] Удаление исходных каталогов из `03-apps/`

### 6.2 Волна B — DevOps и PowerShell (следующая задача)
- [ ] Перенос `infra/scripts/kiosk-maintenance.ps1` → `android/scripts/powershell/maintenance/`
- [ ] Перенос `infra/scripts/log-rotation.ps1` → `android/scripts/powershell/maintenance/`
- [ ] Перенос `infra/scripts/check-maven-access.sh` → `android/scripts/shell/`
- [ ] Создание Gradle-тасок для каждого скрипта
- [ ] Обновление `.github/workflows/*` на новые пути
- [ ] Удаление `infra/scripts/`

### 6.3 Волна C — Аппаратные компоненты (частично выполнена)
- [x] Перенос INO файлов в `android/hardware/arduino/` ✅
- [x] Создание README с инструкциями ✅
- [ ] Добавление Gradle-таски для Arduino CLI
- [ ] Тестирование сборки через Gradle

### 6.4 Волна D — Общие библиотеки и пакеты (планируется)
- [ ] `packages/device-obd` → `android/feature-obd-core/device-obd-kit`
- [ ] `packages/device-thickness` → `android/feature-thickness/device-thickness-kit`
- [ ] `packages/report` → `android/feature-reports/report-kit`
- [ ] `packages/payment-mock` → `android/feature-payments/payment-mock-kit`
- [ ] Обновление Gradle settings
- [ ] Удаление исходной папки `packages/`

### 6.5 Обновление Supabase/DB (24.11.2025)

**Контекст**: Выполнена миграция на новый сервер Supabase `ddaunoxyguqiejrjtwsf.supabase.co`.
Все Supabase/DB артефакты теперь привязаны к новому серверу.

**Требуемые действия**:
- [ ] Перенести TS-агента (`03-apps/02-application/kiosk-shell/agent/`) в `android/platform-ui/web/agent/`
- [ ] Настроить Gradle-таску для запуска `npm run build` внутри нового модуля
- [x] Обновить `.env.example` агента с новыми Supabase параметрами ✅
- [x] Создать README агента с инструкциями по конфигурации переменных окружения ✅ (см. `android/platform-ui/web/README.md`)
- [ ] Обновить CI workflows для работы с новыми путями

**Статус**: ⏳ В процессе. Структура `platform-ui/web/` создана (Session 15G), требуется физический перенос каталога.

**Безопасность**:
- Все секреты Supabase хранятся в Vault: `kv/selfservice/platform/supabase/{env}/service`
- Документация обновлена: `plan-secrets-config.md`, `09-docs/02-application/security/credential-inventory.md`
- Gradle проверки: `:app:checkSupabaseServiceKey` валидирует наличие ключа перед сборкой

---

## 7. История изменений

| Дата       | Сессия | Изменения                                                                                           |
| ---------- | ------ | --------------------------------------------------------------------------------------------------- |
| 24.11.2025 | 15G    | Создана структура каталогов: `platform-ui/web/`, `scripts/powershell/`, `scripts/shell/`, `hardware/arduino/`. Перенесены INO файлы. Созданы README для всех модулей. Обновлены таблицы статусов. |
| 24.11.2025 | 14G    | Обновлён `.env.example` агента с Supabase параметрами                                               |
| 23.11.2025 | 08     | Создан Node-агент в `03-apps/02-application/kiosk-shell/agent/`                                     |

---

Документ обновляется при каждой миграции или добавлении новых языков. Следующее обновление планируется после завершения Волны A (перенос Node-агентов).
