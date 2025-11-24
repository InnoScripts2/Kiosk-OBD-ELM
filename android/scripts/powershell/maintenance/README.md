# Maintenance Scripts — PowerShell

Утилиты обслуживания системы киосков.

## Родительский модуль
`android/scripts/powershell` — PowerShell скрипты DevOps и CI

## Файлы

### kiosk-maintenance.ps1

**Назначение**: Комплексная утилита обслуживания системы киосков

**Параметры**:
- `-Task <string>` (обязательный): Тип задачи
  - `LogRotation` — ротация и архивация старых логов
  - `CollectMetrics` — сбор метрик размера APK и производительности
  - `ValidateLogs` — валидация сессионных логов и контрольных сумм
  - `CleanupArtifacts` — удаление build артефактов и временных файлов
  - `All` — выполнение всех задач
- `-DryRun` (необязательный): Режим имитации без внесения изменений

**Примеры**:
```powershell
# Ротация логов
pwsh android/scripts/powershell/maintenance/kiosk-maintenance.ps1 -Task LogRotation

# Все задачи в режиме DryRun
pwsh android/scripts/powershell/maintenance/kiosk-maintenance.ps1 -Task All -DryRun

# Сбор метрик
pwsh android/scripts/powershell/maintenance/kiosk-maintenance.ps1 -Task CollectMetrics
```

**Через Gradle**:
```bash
cd android
./gradlew runKioskMaintenance
```

**Зависимости**:
- PowerShell 7.0+
- Доступ к файловой системе (чтение/запись `logs/`, `android/build/`)

**Логирование**:
- Стандартный вывод: `Write-Host` (информация о прогрессе)
- Ошибки: `Write-Error` (критичные ошибки)

---

### log-rotation.ps1

**Назначение**: Ротация и архивация логов системы киосков

**Параметры**:
- `-RetentionDays <int>` (необязательный, по умолчанию 90): Количество дней хранения issue логов перед архивацией
- `-Compress` (необязательный): Сжимать архивированные логи в .zip формат
- `-DryRun` (необязательный): Режим имитации без внесения изменений

**Примеры**:
```powershell
# Архивация логов старше 90 дней
pwsh android/scripts/powershell/maintenance/log-rotation.ps1

# Архивация с 30-дневным хранением и сжатием
pwsh android/scripts/powershell/maintenance/log-rotation.ps1 -RetentionDays 30 -Compress

# Режим DryRun
pwsh android/scripts/powershell/maintenance/log-rotation.ps1 -DryRun
```

**Через Gradle**:
```bash
cd android
./gradlew runLogRotation
```

**Политика ротации**:
- **Session logs** (`logs/sessions/*.json`): Хранятся постоянно, не архивируются
- **Issue logs** (`logs/issues/*.json`): Архивируются после `RetentionDays`, удаляются через 1 год в архиве
- **.meta файлы**: Сохраняются вместе с логами

**Зависимости**:
- PowerShell 7.0+
- Доступ к `logs/sessions/`, `logs/issues/`, `logs/archive/`
- (Опционально) Compress-Archive для сжатия

**Логирование**:
- Прогресс архивации: `Write-Host`
- Ошибки доступа к файлам: `Write-Error`

---

## Общие правила

1. **Идемпотентность**: Скрипты можно запускать многократно без побочных эффектов
2. **DryRun**: Всегда тестируйте с `-DryRun` перед выполнением в production
3. **Ошибки**: Все ошибки логируются и прерывают выполнение (`$ErrorActionPreference = 'Stop'`)
4. **Параметры**: Передаются явно, нет скрытых глобальных переменных
5. **Совместимость**: Windows, Linux, macOS (через PowerShell 7+)

## Интеграция с Gradle

Таски определены в корневом `android/build.gradle.kts` (или `android/scripts/build.gradle.kts`):

```kotlin
tasks.register<Exec>("runKioskMaintenance") {
    group = "maintenance"
    description = "Run kiosk maintenance utility"
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/kiosk-maintenance.ps1", "-Task", "All", "-DryRun")
    // Настройте inputs/outputs для кэширования Gradle
}

tasks.register<Exec>("runLogRotation") {
    group = "maintenance"
    description = "Rotate and archive old logs"
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/log-rotation.ps1", "-DryRun")
}
```

## Интеграция с CI/CD

После обновления workflows скрипты вызываются по новым путям:

```yaml
# .github/workflows/maintenance.yml
- name: Kiosk Maintenance
  run: pwsh android/scripts/powershell/maintenance/kiosk-maintenance.ps1 -Task All

- name: Log Rotation
  run: pwsh android/scripts/powershell/maintenance/log-rotation.ps1 -RetentionDays 30 -Compress
```

## Расписание выполнения (рекомендуемое)

| Скрипт             | Частота              | Задачи                    |
| ------------------ | -------------------- | ------------------------- |
| kiosk-maintenance  | Ежедневно (02:00 AM) | ValidateLogs, CollectMetrics |
| log-rotation       | Еженедельно (Sun 01:00 AM) | Архивация старых логов |
| kiosk-maintenance  | Ежемесячно (1st, 03:00 AM) | All                  |

## История изменений

- **24.11.2025 (Session 17A)**: Создание README, перенос скриптов из `infra/scripts/`
- **23.11.2025 (Session 3G)**: Создание скриптов в `infra/scripts/`

## См. также

- `android/scripts/shell/README.md` — Shell скрипты (check-maven-access.sh)
- `plan-80-session-roadmap.md` — сессионный план
- `logs/README.md` — форматы логов и политики хранения
