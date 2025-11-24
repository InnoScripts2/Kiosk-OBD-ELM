# PowerShell скрипты DevOps и CI

Модуль содержит PowerShell скрипты для DevOps, CI/CD и мониторинга.

## Родительский модуль
`android/scripts` — скрипты и утилиты Android-монорепозитория

## Текущий статус миграции

### Волна B — DevOps и PowerShell

**Планируется перенести**:
- `infra/scripts/*.ps1` → `android/scripts/powershell/`
- Каждому скрипту сопоставить Gradle-таску
- Обновить GitHub Actions/локальные инструкции на новые пути

**Текущие скрипты в `infra/scripts/`**:
- `kiosk-maintenance.ps1` — обслуживание киосков (4 задачи)
- `log-rotation.ps1` — ротация логов (политики хранения)
- `check-maven-access.sh` — проверка доступности Maven репозиториев

**Уже в `android/scripts/`**:
- `export-supabase-service-key.ps1` — экспорт Supabase ключей из Vault
- `export-yookassa-webhook-secret.ps1` — экспорт YooKassa секретов из Vault
- `session-05-archive-plan.ps1` — матрица миграции донорских проектов

## Планируемая структура

```
powershell/
├── maintenance/
│   ├── kiosk-maintenance.ps1
│   └── log-rotation.ps1
├── ci/
│   ├── build-apk.ps1
│   └── run-tests.ps1
├── vault/
│   ├── export-supabase-service-key.ps1
│   └── export-yookassa-webhook-secret.ps1
└── README.md
```

## Gradle интеграция (планируется)

```kotlin
// android/build.gradle.kts
tasks.register<Exec>("runMaintenance") {
    commandLine("pwsh", "scripts/powershell/maintenance/kiosk-maintenance.ps1")
}

tasks.register<Exec>("rotatelogs") {
    commandLine("pwsh", "scripts/powershell/maintenance/log-rotation.ps1")
}
```

## Использование в GitHub Actions

После миграции workflows будут обращаться к скриптам по новым путям:

```yaml
# .github/workflows/maintenance.yml
- name: Run maintenance
  run: pwsh android/scripts/powershell/maintenance/kiosk-maintenance.ps1

- name: Rotate logs
  run: pwsh android/scripts/powershell/maintenance/log-rotation.ps1
```

## Требования

- PowerShell 7.0+ (`pwsh`)
- Windows/Linux/macOS совместимость

## Общие правила

1. Все скрипты должны быть идемпотентными
2. Параметры передаются явно (не используются глобальные переменные)
3. Логирование обязательно (`Write-Host`, `Write-Error`)
4. Примеры запуска в комментариях в начале файла
5. Обработка ошибок через `try-catch`

## История изменений

- **24.11.2025**: Создание модуля `scripts/powershell/` (Session 15G)
- **24.11.2025**: Создание скриптов экспорта секретов (Session 14G)
- **23.11.2025**: Создание скриптов обслуживания в `infra/scripts/` (Session 3G)

## Следующие шаги

1. Перенести `infra/scripts/*.ps1` в `android/scripts/powershell/`
2. Создать подкаталоги: `maintenance/`, `ci/`, `vault/`
3. Переместить существующие скрипты в соответствующие подкаталоги
4. Создать Gradle-таски для каждого скрипта
5. Обновить `.github/workflows/*` на новые пути
6. Удалить `infra/scripts/` (оставить только `check-maven-access.sh` → `android/scripts/shell/`)

## Лицензия

См. корневой LICENSE файл проекта.
