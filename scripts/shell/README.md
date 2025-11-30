# Shell скрипты утилит

Модуль содержит shell/bash скрипты для системных утилит и проверок.

## Родительский модуль
`android/scripts` — скрипты и утилиты Android-монорепозитория

## Текущий статус миграции

### Волна B — DevOps и Shell ✅ **Завершена (Session 17A)**

**Перенесено (24.11.2025)**:
- ✅ `infra/scripts/check-maven-access.sh` → `android/scripts/shell/check-maven-access.sh`

## Текущая структура

```
shell/
├── check-maven-access.sh               ✅ Перенесён из infra/scripts/
├── health-check.sh                     ⏳ Планируется
└── README.md                           ✅ Обновлён в Session 17A
```

## Файлы

### check-maven-access.sh

**Назначение**: Еженедельная проверка доступности Maven репозиториев

**Описание**: Проверяет HTTP доступность критичных Maven репозиториев (Google Maven, 
Maven Central, Gradle Plugin Portal, JitPack) и зеркал (Aliyun, Nexus).

**Параметры**: Нет (использует переменные окружения)

**Переменные окружения**:
- `ALIYUN_MIRROR_ENABLED` (опционально): Включить проверку Aliyun зеркал
- `NEXUS_URL` (опционально): URL для проверки локального Nexus

**Примеры**:
```bash
# Базовая проверка
bash android/scripts/shell/check-maven-access.sh

# С Aliyun зеркалами
export ALIYUN_MIRROR_ENABLED=true
bash android/scripts/shell/check-maven-access.sh

# С локальным Nexus
export NEXUS_URL="http://nexus.example.com:8081/repository/maven-public"
bash android/scripts/shell/check-maven-access.sh
```

**Через Gradle**:
```bash
cd android
./gradlew checkMavenAccess
```

**Выход**:
- Цветной вывод в консоль: ✅ OK (зелёный) или ❌ UNAVAILABLE (красный)
- Рекомендуется перенаправить в лог:
  ```bash
  bash android/scripts/shell/check-maven-access.sh | tee logs/infrastructure/maven-health-$(date +%Y-%m-%d).log
  ```

**Таймаут**: 10 секунд на каждый репозиторий

**Зависимости**:
- bash 4.0+
- curl
- grep

**Расписание (рекомендуемое)**: Каждый понедельник 09:00 AM

**См. также**: `docs/infra/agp-access-handbook.md` — стратегии разблокировки Maven

---

## Gradle интеграция

Таска создана в корневом `android/build.gradle.kts` (Session 17A):

```kotlin
tasks.register<Exec>("checkMavenAccess") {
    group = "verification"
    description = "Check Maven repository accessibility"
    commandLine("bash", "scripts/shell/check-maven-access.sh")
}
```

## Использование в CI/CD

После обновления workflows скрипт вызывается по новому пути:

```yaml
# .github/workflows/maintenance.yml
- name: Check Maven Access
  run: bash android/scripts/shell/check-maven-access.sh
```

## Расписание выполнения (рекомендуемое)

| Скрипт               | Частота               | Задача                    |
| -------------------- | --------------------- | ------------------------- |
| check-maven-access   | Еженедельно (Mon 09:00) | Проверка Maven репозиториев |

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

- **24.11.2025 (Session 17A)**: Волна B — перенос `check-maven-access.sh` из `infra/scripts/`
- **24.11.2025 (Session 15G)**: Создание модуля `scripts/shell/`
- **24.11.2025 (Session 14Z)**: Создание `check-maven-access.sh` в `infra/scripts/`

## Следующие шаги

1. ✅ Перенести `check-maven-access.sh` в `android/scripts/shell/`
2. ✅ Создать Gradle-таску для запуска
3. ✅ Обновить README с примерами
4. ⏳ Обновить ссылки в `docs/infra/agp-access-handbook.md` (следующая сессия)
5. ⏳ Обновить `.github/workflows/*` на новый путь (следующая сессия)

## Лицензия

См. корневой LICENSE файл проекта.
