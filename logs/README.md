# Структура логов проекта

**Дата создания**: 23.11.2025  
**Версия**: 2.0  
**Обновлено**: Session 1G

## Оглавление
1. [Назначение](#назначение)
2. [Структура каталогов](#структура-каталогов)
3. [Формат файлов](#формат-файлов)
4. [Метаданные и checksums](#метаданные-и-checksums)
5. [Retention policy](#retention-policy)
6. [Процедуры](#процедуры)

## Назначение

Каталог `logs/` содержит структурированные записи о выполненных сессиях разработки и выявленных проблемах. Логи используются для:

- Трассировки изменений и принятых решений
- Анализа прогресса по сессиям
- Выявления повторяющихся проблем
- Аудита и отчётности

## Структура каталогов

```
logs/
├── sessions/           # Логи выполненных сессий
│   ├── README.md       # Краткое описание
│   ├── session-10b.json
│   ├── session-10b.json.meta
│   ├── session-10c.json
│   ├── session-10c.json.meta
│   ├── session-12.json
│   └── session-12.json.meta
├── issues/             # Записи о проблемах
│   ├── README.md       # Краткое описание
│   ├── 2025-11-23-agp-blocker.json
│   ├── 2025-11-23-agp-blocker.json.meta
│   └── 2025-11-23-maven-mirror.json
└── README.md           # Этот файл
```

### `sessions/`
Содержит JSON-файлы с детальной информацией о каждой сессии: дата, модули, изменённые файлы, тесты, метрики.

### `issues/`
Содержит JSON-файлы с описанием проблем: дата обнаружения, статус, воздействие, шаги для воспроизведения, резолюция.

## Формат файлов

### Session Log Format

Файл: `session-{number}{category}.json`  
Пример: `session-10b.json`, `session-12.json`, `session-1g.json`

```json
{
  "sessionId": "10B",
  "date": "2025-11-23",
  "category": "B",
  "module": "platform-bluetooth",
  "summary": "Исправление blessed-kotlin API методов",
  "filesChanged": [
    "android/platform/bluetooth/src/main/kotlin/..."
  ],
  "filesAdded": [
    "..."
  ],
  "linesChanged": 150,
  "linesAdded": 200,
  "testsAdded": 9,
  "testsPassed": 9,
  "testsFailed": 0,
  "apkSize": {
    "before": "5.41 MB",
    "after": "5.43 MB",
    "delta": "+0.02 MB"
  },
  "blockers": [
    {
      "id": "AGP-8.4.1",
      "description": "Google Maven недоступен",
      "workaround": "Code review без компиляции"
    }
  ],
  "knownIssues": [
    {
      "issueId": "2025-11-23-agp-blocker",
      "status": "open"
    }
  ],
  "nextSteps": [
    "Интеграция DI/Hilt",
    "UI integration"
  ],
  "metadata": {
    "checksum": "md5:abcd1234...",
    "generatedBy": "GitHub Copilot",
    "toolsVersion": "Session 1G"
  }
}
```

#### Обязательные поля

- `sessionId` (string): Уникальный идентификатор сессии (например, "10B", "12", "1G")
- `date` (string): Дата в формате YYYY-MM-DD
- `category` (string): Категория ("A", "B", "C", "G") или пусто для основных сессий
- `module` (string): Основной затронутый модуль
- `summary` (string): Краткое описание выполненной работы (≤200 символов)
- `filesChanged` (array): Список изменённых файлов (относительные пути)
- `linesChanged` (number): Количество изменённых строк кода
- `testsAdded` (number): Количество добавленных тестов
- `metadata.checksum` (string): MD5 checksum файла (без поля metadata)

#### Опциональные поля

- `filesAdded` (array): Новые файлы
- `linesAdded` (number): Добавленные строки
- `testsPassed` / `testsFailed` (number): Статистика тестов
- `apkSize` (object): Метрики размера APK
- `blockers` (array): Текущие блокирующие проблемы
- `knownIssues` (array): Ссылки на записи в `issues/`
- `nextSteps` (array): Планируемые шаги

### Issue Log Format

Файл: `YYYY-MM-DD-issue-name.json`  
Пример: `2025-11-23-agp-blocker.json`

```json
{
  "issueId": "2025-11-23-agp-blocker",
  "date": "2025-11-23",
  "title": "AGP 8.4.1 недоступен в Google Maven",
  "status": "open",
  "severity": "critical",
  "impact": "Блокирует компиляцию Android модулей",
  "affectedModules": [
    "android/app",
    "android/feature-*",
    "android/platform-*"
  ],
  "description": "Google Maven (dl.google.com) недоступен, зеркала maven.aliyun.com не содержат AGP 8.4.1. Gradle build fails.",
  "reproduction": [
    "1. ./gradlew clean",
    "2. ./gradlew :app:assembleDebug",
    "3. Error: Could not resolve com.android.tools.build:gradle:8.4.1"
  ],
  "workaround": "Code review без компиляции, тестирование Node.js отдельно",
  "resolution": null,
  "resolvedDate": null,
  "relatedSessions": ["08", "09", "10", "10B", "10C", "11", "12"],
  "metadata": {
    "checksum": "md5:xyz789...",
    "generatedBy": "GitHub Copilot"
  }
}
```

#### Обязательные поля

- `issueId` (string): Уникальный идентификатор (формат: YYYY-MM-DD-short-name)
- `date` (string): Дата обнаружения (YYYY-MM-DD)
- `title` (string): Краткое название проблемы
- `status` (enum): "open", "in-progress", "resolved", "wontfix"
- `severity` (enum): "critical", "high", "medium", "low"
- `impact` (string): Описание влияния на проект
- `description` (string): Детальное описание проблемы
- `metadata.checksum` (string): MD5 checksum

#### Опциональные поля

- `affectedModules` (array): Затронутые модули
- `reproduction` (array): Шаги для воспроизведения
- `workaround` (string): Временное решение
- `resolution` (string): Постоянное решение (если есть)
- `resolvedDate` (string): Дата резолюции
- `relatedSessions` (array): Связанные сессии

## Метаданные и checksums

Каждый JSON-файл лога имеет сопутствующий `.meta` файл с метаданными и контрольной суммой.

### Формат `.meta` файла

Файл: `{filename}.json.meta`  
Пример: `session-10b.json.meta`

```json
{
  "filename": "session-10b.json",
  "checksum": {
    "algorithm": "MD5",
    "value": "a1b2c3d4e5f6..."
  },
  "size": 2048,
  "created": "2025-11-23T16:30:00Z",
  "lastModified": "2025-11-23T16:30:00Z",
  "version": 1,
  "author": "GitHub Copilot",
  "notes": "Session 10B completion log"
}
```

### Генерация checksum

```bash
# Linux/macOS
md5sum session-10b.json | awk '{print $1}'

# PowerShell
Get-FileHash -Algorithm MD5 session-10b.json | Select-Object -ExpandProperty Hash
```

Checksum вычисляется **без** поля `metadata.checksum` в оригинальном JSON (чтобы избежать циклической зависимости).

### Валидация

Для проверки целостности логов:

```bash
# Bash
for file in *.json; do
  expected=$(jq -r '.metadata.checksum' "$file" | cut -d: -f2)
  actual=$(jq 'del(.metadata.checksum)' "$file" | md5sum | awk '{print $1}')
  if [ "$expected" != "$actual" ]; then
    echo "CHECKSUM MISMATCH: $file"
  fi
done

# PowerShell
Get-ChildItem *.json | ForEach-Object {
  $expected = (Get-Content $_.Name | ConvertFrom-Json).metadata.checksum -replace 'md5:', ''
  $content = (Get-Content $_.Name | ConvertFrom-Json)
  $content.metadata.PSObject.Properties.Remove('checksum')
  $actual = (ConvertTo-Json $content -Compress | Get-FileHash -Algorithm MD5).Hash.ToLower()
  if ($expected -ne $actual) {
    Write-Host "CHECKSUM MISMATCH: $($_.Name)" -ForegroundColor Red
  }
}
```

## Retention policy

### Автоматическая очистка

- **Session logs**: сохраняются **постоянно** (архив проекта)
- **Issue logs**: сохраняются **постоянно** до резолюции, затем 90 дней после `resolvedDate`
- **Временные логи** (если создаются): 7 дней ротация

### Архивация

Логи старше 1 года могут быть переданы в долгосрочное хранилище:

```
logs/archive/2025/sessions/
logs/archive/2025/issues/
```

### Размер хранилища

**Лимит**: 50 MB для `logs/` каталога (исключая `archive/`).  
**Контроль**: автоматическая проверка при каждом PR.

## Процедуры

### Создание session log

1. Завершить сессию (код, тесты, документация)
2. Собрать метрики: файлы, строки, тесты, APK size
3. Создать `logs/sessions/session-{id}.json` по шаблону выше
4. Вычислить checksum:
   ```bash
   jq 'del(.metadata.checksum)' session-{id}.json | md5sum | awk '{print $1}'
   ```
5. Добавить checksum в `metadata.checksum: "md5:..."`
6. Создать `.meta` файл
7. Обновить `android/session-logs/session-{id}.md` (детальный лог)
8. Commit оба файла

### Создание issue log

1. Обнаружить проблему
2. Создать `logs/issues/YYYY-MM-DD-issue-name.json` по шаблону
3. Вычислить checksum (без `metadata.checksum`)
4. Добавить checksum в файл
5. Создать `.meta` файл
6. Ссылаться на `issueId` в session logs через `knownIssues`
7. Commit

### Резолюция issue

1. Найти решение
2. Обновить `status: "resolved"`
3. Указать `resolution` и `resolvedDate`
4. Пересчитать checksum
5. Обновить `.meta` файл (lastModified, version++)
6. Commit

### Dry-run валидация

Перед коммитом выполнить валидацию всех логов:

```bash
# Проверка JSON syntax
for file in logs/**/*.json; do
  jq empty "$file" 2>/dev/null || echo "INVALID JSON: $file"
done

# Проверка checksums
cd logs/sessions && bash ../../scripts/validate-checksums.sh
cd logs/issues && bash ../../scripts/validate-checksums.sh
```

## Инструменты

### Скрипты валидации

- `scripts/validate-checksums.sh` — проверка всех checksums
- `scripts/generate-session-log.sh` — генерация шаблона session log
- `scripts/generate-issue-log.sh` — генерация шаблона issue log
- `scripts/archive-old-logs.sh` — архивация старых логов (>1 года)

### Интеграция с CI

GitHub Actions workflow проверяет:
1. JSON syntax для всех файлов в `logs/`
2. Checksums соответствуют содержимому
3. Обязательные поля присутствуют
4. Размер `logs/` не превышает 50 MB

---

## История изменений

| Дата       | Версия | Изменения                                          |
|------------|--------|----------------------------------------------------|
| 23.11.2025 | 2.0    | Session 1G: добавлены форматы, checksums, процедуры |
| 08.11.2025 | 1.0    | Session 08: создание базовой структуры              |

---

## Ссылки

- [План 80 сессий](../plan-80-session-roadmap.md)
- [История сессий](../android/session-logs/)
- [Руководство по инструкциям](./.github/instructions/instructions.instructions.md)

---

**Актуально на**: 23.11.2025  
**Следующее обновление**: после Session 2G
