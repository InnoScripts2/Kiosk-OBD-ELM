# Shell скрипты утилит

Модуль содержит shell/bash скрипты для системных утилит и проверок.

## Родительский модуль
`android/scripts` — скрипты и утилиты Android-монорепозитория

## Текущий статус миграции

### Волна B — DevOps и Shell

**Планируется перенести**:
- `infra/scripts/check-maven-access.sh` → `android/scripts/shell/check-maven-access.sh`

**Текущие скрипты в `infra/scripts/`**:
- `check-maven-access.sh` — еженедельная проверка доступности Maven репозиториев

## Планируемая структура

```
shell/
├── check-maven-access.sh
├── health-check.sh (будущий)
└── README.md
```

## Gradle интеграция (планируется)

```kotlin
// android/build.gradle.kts
tasks.register<Exec>("checkMavenAccess") {
    commandLine("bash", "scripts/shell/check-maven-access.sh")
}
```

## Использование

```bash
# Проверка доступности Maven репозиториев
bash android/scripts/shell/check-maven-access.sh

# Результаты сохраняются в:
# logs/infrastructure/maven-health-YYYY-MM-DD.log
```

## Требования

- bash 4.0+
- curl
- Доступ к интернету

## Общие правила

1. Все скрипты должны быть POSIX-совместимыми (или явно требовать bash)
2. Параметры передаются через аргументы командной строки
3. Логирование через `echo` в stderr/stdout
4. Примеры запуска в комментариях в начале файла
5. Обработка ошибок через `set -e` или явные проверки
6. Исполняемые права (`chmod +x`)

## История изменений

- **24.11.2025**: Создание модуля `scripts/shell/` (Session 15G)
- **24.11.2025**: Создание `check-maven-access.sh` в `infra/scripts/` (Session 14Z)

## Следующие шаги

1. Перенести `infra/scripts/check-maven-access.sh` в `android/scripts/shell/`
2. Создать Gradle-таску для запуска
3. Обновить документацию (`docs/infra/agp-access-handbook.md`)
4. Удалить `infra/scripts/check-maven-access.sh`

## Лицензия

См. корневой LICENSE файл проекта.
