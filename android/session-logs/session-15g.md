# Session 15G — Консолидация языков и артефактов в android/

**Дата**: 24.11.2025 (UTC)  
**Категория**: G (Documentation/Infrastructure)  
**Приоритет**: Высокий  
**Статус**: ✅ Завершено

## Цель
Создание и обновление плана консолидации языков `android/plan-multi-language-consolidation.md` согласно актуальному состоянию репозитория, с фиксацией текущего статуса миграции и следующих шагов. Создание структуры каталогов для полиязычных компонентов (TypeScript, PowerShell, Arduino).

## Контекст
Согласно инструкциям репозитория (`.github/instructions/instructions.instructions.md`), вся активная разработка должна находиться в каталоге `android/`. Внешние директории (`03-apps/`, `packages/`, `infra/`) рассматриваются как доноры и подлежат консолидации.

Политика полиязычных компонентов (раздел 4.3 инструкций):
- Kotlin остаётся основным языком приложения
- Дополнительные технологии (TS/JS, PowerShell, INO) допускаются только если напрямую поддерживают киоск
- Все артефакты должны иметь прямую привязку к модулю Android (README со ссылкой на родительский модуль)
- Самостоятельные проекты вне монорепо запрещены
- Кросс-языковые пайплайны управляются из Gradle

## Выполненные действия

### 1. Создание структуры каталогов
Созданы следующие модули внутри `android/`:

```bash
android/
├── hardware/
│   └── arduino/              # Прошивки Arduino (замки, реле)
├── platform-ui/
│   └── web/                  # TypeScript/React агенты и dev-tools
├── scripts/
│   ├── powershell/           # PowerShell скрипты DevOps/CI
│   └── shell/                # Bash/shell утилиты
```

**Команды**:
```bash
cd android
mkdir -p platform-ui/web hardware/arduino scripts/powershell scripts/shell
```

### 2. Перенос INO файлов
Перенесены Arduino прошивки из корня `android/` в подмодуль `android/hardware/arduino/`:

**Файлы**:
- `android/dispencer.ino` → `android/hardware/arduino/dispencer.ino`
- `android/dispenser.ino` → `android/hardware/arduino/dispenser.ino`

**Команда**:
```bash
cd android
mv dispencer.ino dispenser.ino hardware/arduino/
```

**Назначение**:
- Управление замками выдачи толщиномера и OBD-адаптера
- Serial протокол (9600 baud): `OPEN_THICKNESS`, `OPEN_OBD`, `CLOSE_*`, `STATUS`, `PING`
- Безопасность: watchdog (60s), auto-close (10s), таймауты (5s)

### 3. Создание README для новых модулей

Созданы подробные README с инструкциями по интеграции и использованию:

#### `android/hardware/arduino/README.md` (2312 символов)
- Описание прошивок dispencer/dispenser
- Инструкции по сборке через Arduino IDE и Arduino CLI
- Планируемая Gradle интеграция
- Схема подключения и протокол Serial связи
- Связь с `LockController` из Node-агента

#### `android/platform-ui/web/README.md` (3855 символов)
- Планируемая структура: `agent/`, `kiosk-agent/`, `legacy/`
- Статус миграции Волны A (Node-инфраструктура)
- Планируемая Gradle интеграция (`buildWebAgent`, `testWebAgent`)
- Связь с другими модулями (feature-obd-core, feature-thickness, feature-payments, etc.)
- Переменные окружения (APP_MODE, DEVICE_MOCK_*, PAYMENT_MOCK, SUPABASE_*)
- Безопасность: хранение секретов в Vault

#### `android/scripts/powershell/README.md` (2891 символ)
- Планируемая структура: `maintenance/`, `ci/`, `vault/`
- Статус миграции Волны B (DevOps и PowerShell)
- Текущие скрипты в `infra/scripts/`: kiosk-maintenance.ps1, log-rotation.ps1
- Уже в `android/scripts/`: export-supabase-service-key.ps1, export-yookassa-webhook-secret.ps1
- Планируемая Gradle интеграция и использование в GitHub Actions
- Общие правила: идемпотентность, явные параметры, обработка ошибок

#### `android/scripts/shell/README.md` (1826 символов)
- Планируемая структура: `check-maven-access.sh`, `health-check.sh`
- Статус миграции Волны B (Shell утилиты)
- Текущий скрипт в `infra/scripts/`: check-maven-access.sh
- Планируемая Gradle интеграция
- Общие правила: POSIX-совместимость, явные параметры, исполняемые права

### 4. Обновление плана консолидации

Обновлён `android/plan-multi-language-consolidation.md`:

#### Таблица «Текущий стек и целевые директории»
Добавлены:
- Колонка «Дата обновления»
- Эмодзи статусов: ✅ (выполнено), 🔄 (в процессе)
- Статусы по каждому языку с датой 24.11.2025

#### Раздел «Волна C — Аппаратные компоненты»
- Отмечены как ✅ Частично выполнено (Session 15G)
- Зафиксирован перенос INO файлов
- Описаны созданные артефакты (README, инструкции)
- Планируемые шаги: Gradle-таска для Arduino CLI

#### Раздел «6. Следующие шаги»
Полностью переработан с детальными чек-листами:
- **6.0 Обновление структуры (24.11.2025, Session 15G)** — завершено ✅
- **6.1 Волна A** — UI и Node-инфраструктура (следующая задача)
- **6.2 Волна B** — DevOps и PowerShell (следующая задача)
- **6.3 Волна C** — Аппаратные компоненты (частично выполнена)
- **6.4 Волна D** — Общие библиотеки и пакеты (планируется)
- **6.5 Обновление Supabase/DB** — статус ⏳

#### Раздел «7. История изменений»
Добавлена таблица с хронологией:
- 24.11.2025, Session 15G: создание структуры, перенос INO, README
- 24.11.2025, Session 14G: обновление `.env.example`
- 23.11.2025, Session 08: создание Node-агента

**Метрики обновлений**:
- Символов добавлено в план: ~2,300
- Секций обновлено: 4
- Новых таблиц: 2

## Тесты
Не применимо (категория G — документация/инфраструктура).

## Метрики

| Метрика                | Значение                                        |
| ---------------------- | ----------------------------------------------- |
| Директорий создано     | 5 (`platform-ui/web/`, `hardware/arduino/`, `scripts/powershell/`, `scripts/shell/`, одна вложенная) |
| Файлов перенесено      | 2 (dispencer.ino, dispenser.ino)                |
| README созданы         | 4 (по одному на каждый новый модуль)            |
| Строк документации     | ~10,900 (README ~10,884 + план ~2,300)          |
| План обновлён          | 1 файл (`plan-multi-language-consolidation.md`) |
| Символов в обновлениях | ~2,300                                          |

## Соответствие политикам

### Инструкция 4.2 — Политика консолидации в `android/`
✅ Вся активная разработка теперь структурирована в `android/`  
✅ Каталоги вне `android/` рассматриваются как доноры (документировано в README)  
✅ Никаких параллельных копий компонентов  
✅ Подготовлена почва для проверки новых файлов вне `android/` (BKG-агент)

### Инструкция 4.3 — Политика полиязычных компонентов
✅ Kotlin остаётся основным языком (документировано в таблице)  
✅ TS/JS, PowerShell, INO допускаются только для поддержки киоска (описано в README)  
✅ Все артефакты имеют прямую привязку к модулю Android (README со ссылкой на родительский модуль)  
✅ Самостоятельные проекты вне монорепо запрещены (зафиксировано в разделе «Запрещено»)  
✅ Кросс-языковые пайплайны планируется управлять из Gradle (описано в README)  
✅ План внедрения и миграции языков оформлен (`android/plan-multi-language-consolidation.md`)

### Инструкция BKG-агента 27–29
✅ Многоязычные компоненты отражены в `android/plan-multi-language-consolidation.md`  
✅ TypeScript код планируется создавать в `android/platform-ui/web`  
✅ PowerShell скрипты планируется размещать в `android/scripts/powershell` с README  
✅ Arduino исходники размещены в `android/hardware/arduino` с документированием схем

## Артефакты

### Созданные файлы
1. `android/hardware/arduino/README.md` — 2,312 символов
2. `android/platform-ui/web/README.md` — 3,855 символов
3. `android/scripts/powershell/README.md` — 2,891 символ
4. `android/scripts/shell/README.md` — 1,826 символов

### Обновлённые файлы
1. `android/plan-multi-language-consolidation.md` — +2,300 символов (4 секции, 2 таблицы)

### Перенесённые файлы
1. `android/dispencer.ino` → `android/hardware/arduino/dispencer.ino`
2. `android/dispenser.ino` → `android/hardware/arduino/dispenser.ino`

### Созданные директории
1. `android/platform-ui/web/`
2. `android/hardware/arduino/`
3. `android/scripts/powershell/`
4. `android/scripts/shell/`

## Следующие шаги

### Волна A — UI и Node-инфраструктура (приоритет 1)
1. Перенести `03-apps/02-application/kiosk-shell/agent/` → `android/platform-ui/web/agent/`
2. Перенести `03-apps/02-application/kiosk-agent/` → `android/platform-ui/web/kiosk-agent/`
3. Создать Gradle-таски `buildWebAgent`, `testWebAgent`
4. Настроить артефакты в `android/platform-ui/web/dist/`
5. Обновить CI workflows (`.github/workflows/*`)
6. Удалить исходные каталоги из `03-apps/`

### Волна B — DevOps и PowerShell (приоритет 2)
1. Перенести скрипты из `infra/scripts/` в `android/scripts/powershell/` и `android/scripts/shell/`
2. Создать подкаталоги: `maintenance/`, `ci/`, `vault/`
3. Создать Gradle-таски для каждого скрипта
4. Обновить `.github/workflows/*` на новые пути
5. Удалить `infra/scripts/`

### Волна C — Аппаратные компоненты (приоритет 3)
1. Добавить Gradle-таску для Arduino CLI (`compileArduino`, `uploadArduino`)
2. Протестировать сборку через Gradle
3. Интегрировать в CI (если требуется)

### Волна D — Общие библиотеки и пакеты (приоритет 4)
1. Перенести `packages/*` в соответствующие `android/feature-*` модули
2. Обновить Gradle settings
3. Запустить тесты
4. Удалить исходную папку `packages/`

## Блокеры
Отсутствуют.

## Заключение
Session 15G успешно выполнена. Создана структура каталогов для полиязычных компонентов внутри `android/`, перенесены Arduino прошивки, созданы подробные README для каждого модуля и обновлён план консолидации с детальными чек-листами и историей изменений.

Следующая задача — Волна A (перенос Node-агентов в `android/platform-ui/web/`).
