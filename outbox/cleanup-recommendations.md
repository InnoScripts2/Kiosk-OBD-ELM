# Cleanup Recommendations — 2025-11-30

Author: Cleanup Agent  
Branch: My-project  
Goal: Repository optimization without breaking functionality

---

## Резюме анализа

| Категория | Количество | Общий размер |
|-----------|------------|--------------|
| DUPLICATE файлы | 8 групп | ~15 MB |
| ARCHIVE кандидаты | 2 директории | ~37 MB |
| SAFE TO DELETE | 3 элемента | ~7 MB |
| NEEDS REVIEW | 4 элемента | — |

---

## Category: DUPLICATE FILES

### 1. DTC-codes.json (IDENTICAL)

- **Файл A**: `feature-obd-elm-port/src/main/resources/com/selfservice/obd/elm/port/assets/dtc-codes.json`
- **Файл B**: `feature-obd-core/src/main/assets/dtc-codes.json`
- **SHA256**: `bfaf0f3d1afc11da5ebf01138235b36dc9a032f6e260c262fe8b5dd4014591e1`
- **Reason**: Абсолютно идентичные файлы в двух модулях
- **Action**: Оставить только в `feature-obd-core/src/main/assets/`, удалить из `feature-obd-elm-port`
- **Risk**: LOW — нужно проверить, что `feature-obd-elm-port` использует зависимость на `feature-obd-core`

### 2. PID-mode1.json (IDENTICAL)

- **Файл A**: `feature-obd-elm-port/src/main/resources/com/selfservice/obd/elm/port/assets/pids-mode1.json`
- **Файл B**: `feature-obd-core/src/main/assets/pids-mode1.json`
- **SHA256**: `5814065b43f8f972d2acc5452a9ea31d30292b0f692d992e0f3562f7479f54dd`
- **Reason**: Идентичные файлы PID для Mode 1
- **Action**: Удалить из `feature-obd-elm-port`, оставить в `feature-obd-core`
- **Risk**: LOW

### 3. PID-mode4.json (IDENTICAL)

- **Файл A**: `feature-obd-elm-port/src/main/resources/com/selfservice/obd/elm/port/assets/pids-mode4.json`
- **Файл B**: `feature-obd-core/src/main/assets/pids-mode4.json`
- **SHA256**: `8b47b9ff6489b47aa27cc1a9071711cb32261b57a5b6e1cde0a53898f10ccc7b`
- **Reason**: Идентичные файлы PID для Mode 4
- **Action**: Удалить из `feature-obd-elm-port`, оставить в `feature-obd-core`
- **Risk**: LOW

### 4. PID-mode9.json (IDENTICAL)

- **Файл A**: `feature-obd-elm-port/src/main/resources/com/selfservice/obd/elm/port/assets/pids-mode9.json`
- **Файл B**: `feature-obd-core/src/main/assets/pids-mode9.json`
- **SHA256**: `f00f099b44dd36ce487d9d5ff0d75a0226ab76041ac19613b3ac128486cb973a`
- **Reason**: Идентичные файлы PID для Mode 9
- **Action**: Удалить из `feature-obd-elm-port`, оставить в `feature-obd-core`
- **Risk**: LOW

### 5. serialization-formats DUPLICATE STRUCTURE

- **Путь A**: `platform/data/serialization/formats/` (680 файлов, ~5.4 MB)
- **Путь B**: `platform/data/serialization-formats/` (680 файлов, ~5.4 MB)
- **SHA256 (README.md)**: `91077fb26275496ca4dd493158031abb867db55526dfb9fc1c521dc9bf3d0800`
- **Reason**: Полностью дублирующаяся структура директорий с идентичным содержимым
- **Action**: Удалить `platform/data/serialization-formats/`, оставить `platform/data/serialization/formats/`
- **Risk**: LOW — `settings.gradle.kts` не ссылается на `serialization-formats`

### 6. DTC-index.json (SIMILAR — 2 строки разница)

- **Файл A**: `feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc-index.json` (86824 строк)
- **Файл B**: `feature-obd-core/src/main/assets/dtc-index.json` (86826 строк)
- **Reason**: Практически идентичные файлы с минимальной разницей
- **Action**: Определить канонический источник, удалить дубликат
- **Risk**: MEDIUM — требуется review разницы

### 7. Class files (TEST RESOURCES — IDENTICAL)

- **Путь A**: `platform/data/serialization/formats/json-tests/jvmTest/resources/class_loaders/classes/example/`
- **Путь B**: `platform/data/serialization-formats/json-tests/jvmTest/resources/class_loaders/classes/example/`
- **SHA256 (Foo.class)**: `2cb733e80e819e7a8636058eb78610a1e4276c427e488aa64bd00c49d37cba2f`
- **Reason**: Тестовые ресурсы, дублируются вместе с родительской директорией
- **Action**: Удаляются автоматически при удалении `serialization-formats/`
- **Risk**: NONE

---

## Category: SAFE TO DELETE

### 1. tmp/ directory

- **Path**: `tmp/`
- **Size**: 7.3 MB
- **Contents**: 
  - `dtc-brand-codes.csv` (6.6 MB)
  - `dtc-summary.csv` (0.9 MB)
  - `row47-49.txt` (4 KB)
- **Reason**: Временные файлы экспорта, не являются частью исходного кода
- **Action**: Добавить `tmp/` в `.gitignore`, удалить из tracking
- **Risk**: NONE — временные артефакты

### 2. icon-gen.js wrapper (OPTIONAL)

- **Path**: `tools/icon-gen.js`
- **Size**: 200 bytes
- **Reason**: Простая обёртка, делегирующая работу `icon-gen.cjs`
- **Action**: Сохранить для совместимости или удалить, если не используется
- **Risk**: LOW — проверить внешние вызовы

### 3. DONORS .iml file

- **Path**: `DONORS/1-begaz-OBDII/obd2_plugin.iml`
- **Reason**: IDE-specific файл в read-only донорской директории
- **Action**: Добавить `**/*.iml` в `.gitignore` (уже игнорируется `**/.idea/`)
- **Risk**: NONE — IDE артефакт

---

## Category: ARCHIVE CANDIDATES (НЕ УДАЛЯТЬ)

### 1. base/ directory — Reference DTC data

- **Path**: `base/`
- **Size**: 29 MB
- **Contents**:
  - `All BMW OBD2 Codes List (7)` — 2.4 MB
  - `All Lexus OBD2 Codes List (5)` — 2.2 MB
  - `All Manufacturer-specific OBD2 Codes (4)` — 12 MB
  - `All Nissan OBD2 Codes List (3)` — 4.1 MB
  - `All Toyota OBD2 Codes List (2)` — 2.4 MB
  - `Generic OBD2 Codes List (6)` — 2.1 MB
  - `OBDII.DTC-main/` — 3.7 MB (.NET C# source)
  - `XT6300_Scripting_Guide_LLMCheatsheet/` — 68 KB
  - `dtcmapping.json` — 332 KB
- **Reason**: Справочные данные и исходные материалы для DTC/PID баз
- **Action**: Оставить как read-only референс; возможен архив в `archives/base-reference.tar.gz` после завершения интеграции
- **Risk**: LOW — не блокирует сборку

### 2. 03-apps/02-application/kiosk-shell/agent/ — Legacy agent copy

- **Path**: `03-apps/02-application/kiosk-shell/agent/`
- **Size**: 724 KB
- **Status**: Частично мигрирован в `platform/ui/web/agent/` (364 KB)
- **Reason**: Согласно `03-apps/ARCHIVE_NOTE.md`, миграция в прогрессе; оригинал содержит дополнительный код
- **Action**: НЕ УДАЛЯТЬ — миграция не завершена (см. ARCHIVE_NOTE.md)
- **Risk**: HIGH при удалении — потеря несмигрированного кода

---

## Category: NEEDS REVIEW (Требуют проверки)

### 1. Dual session-05-archive-plan.ps1

- **Path A**: `scripts/session-05-archive-plan.ps1`
- **Path B**: `tools/session-05-archive-plan.ps1`
- **Status**: Файлы РАЗЛИЧАЮТСЯ (разные хеши)
- **SHA A**: `a06cb44d3a53e0adf23e9517830b56c2a997c1613c130fa80a98f1cf5f235b66`
- **SHA B**: `b0621acea14981df39bd6117e6a731ffead3b416414a02594cbe5bfa60a85c6b`
- **Action**: Определить актуальную версию, удалить устаревшую
- **Risk**: MEDIUM — возможна потеря логики при неправильном выборе

### 2. DTC.json vs dtc-codes.json в feature-obd-core

- **Path A**: `feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc.json` (93571 строк)
- **Path B**: `feature-obd-core/src/main/assets/dtc-codes.json` (9067 строк)
- **Reason**: Разные форматы и размеры — возможно, разные целевые аудитории
- **Action**: Определить, какой файл используется в runtime; возможно оба нужны
- **Risk**: HIGH — неправильное удаление сломает DTC lookup

### 3. PID v2 files в resources

- **Paths**:
  - `feature-obd-core/src/main/resources/com/selfservice/obd/core/pids/pids-mode1-v2.json`
  - `feature-obd-core/src/main/resources/com/selfservice/obd/core/pids/pids-mode4-v2.json`
  - `feature-obd-core/src/main/resources/com/selfservice/obd/core/pids/pids-mode9-v2.json`
  - `feature-obd-core/src/main/resources/com/selfservice/obd/core/pids/pids.json`
- **Reason**: Версионированные PID файлы; неясно, какие используются
- **Action**: Проверить Kotlin код на предмет загрузки; возможно v2 — будущая версия
- **Risk**: MEDIUM

### 4. platform/ui/web/agent vs 03-apps/.../agent миграция

- **Source**: `03-apps/02-application/kiosk-shell/agent/` (724 KB, 9 subdirs in src)
- **Target**: `platform/ui/web/agent/` (364 KB, 1 subdir in src)
- **Status**: Миграция частичная — отсутствуют:
  - `src/app.ts`, `src/app.static.test.ts`
  - `src/config/`, `src/http/`, `src/integrations/`, `src/routes/`
  - `src/services/AgentHeartbeatService.ts`, `DeviceCommandService.ts`, `ReportIngestService.ts`
  - `src/websocket/`, `src/workers/`
- **Action**: Завершить миграцию или пометить 03-apps как архив
- **Risk**: HIGH при удалении 03-apps — потеря функциональности

---

## Category: .gitignore рекомендации

Текущий `.gitignore` уже покрывает:
- ✅ `node_modules`
- ✅ `.gradle/`, `**/.gradle/`
- ✅ `**/build/`
- ✅ `**/.idea/`
- ✅ `archives/`

**Рекомендуемые дополнения:**

```gitignore
# Temporary export files
tmp/
**/tmp/

# IDE-specific files
**/*.iml

# Lock operation logs (transient)
**/logs/sessions/*.json
**/logs/sessions/*.log

# OS-specific
.DS_Store
Thumbs.db
```

---

## Рекомендуемый план действий

### Phase 1: SAFE CLEANUP (можно выполнить сразу)

1. ✅ Добавить `tmp/` в `.gitignore`
2. ✅ Добавить `**/*.iml` в `.gitignore` (для полноты)
3. ⚠️ Удалить `platform/data/serialization-formats/` (полный дубликат)

### Phase 2: DUPLICATE CONSOLIDATION (требует тестирования)

1. Удалить дубликаты в `feature-obd-elm-port/src/main/resources/.../assets/`:
   - `dtc-codes.json`
   - `pids-mode1.json`
   - `pids-mode4.json`
   - `pids-mode9.json`
2. Обновить Kotlin код для загрузки из `feature-obd-core`
3. Запустить `./gradlew test` для проверки

### Phase 3: ARCHIVE (после подтверждения владельца)

1. Архивировать `base/` → `archives/base-reference.tar.gz`
2. Завершить миграцию `03-apps/.../agent/` → `platform/ui/web/agent/`
3. После успешной миграции: `03-apps/` → `archives/`

---

## Metrics

| Метрика | Значение |
|---------|----------|
| Общий размер репозитория | ~130 MB |
| Потенциальная экономия | ~22 MB (17%) |
| Дублирующиеся файлы | 8 групп |
| IDE-артефакты | 1 файл (.iml) |
| Временные файлы | 3 файла (7 MB) |

---

*Последнее обновление: 2025-11-30*
