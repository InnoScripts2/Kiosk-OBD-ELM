# PowerShell скрипты DevOps и CI

Модуль содержит PowerShell скрипты для DevOps, CI/CD и мониторинга.

## Родительский модуль
`android/scripts` — скрипты и утилиты Android-монорепозитория

## Текущий статус миграции

### Волна B — DevOps и PowerShell ✅ **Частично завершена (Session 17A)**

**Перенесено (24.11.2025)**:
- ✅ `infra/scripts/kiosk-maintenance.ps1` → `android/scripts/powershell/maintenance/`
- ✅ `infra/scripts/log-rotation.ps1` → `android/scripts/powershell/maintenance/`
- ✅ `infra/scripts/check-maven-access.sh` → `android/scripts/shell/`

**Уже в `android/scripts/`**:
- `export-supabase-service-key.ps1` — экспорт Supabase ключей из Vault
- `export-yookassa-webhook-secret.ps1` — экспорт YooKassa секретов из Vault
- `session-05-archive-plan.ps1` — матрица миграции донорских проектов

**Следующий этап**:
- [ ] Обновить GitHub Actions/workflows на новые пути
- [ ] Перенести экспортные скрипты в `powershell/vault/`
- [ ] Удалить `infra/scripts/` после обновления workflows

## Текущая структура

```
powershell/
├── maintenance/                         ✅ Создан в Session 17A
│   ├── kiosk-maintenance.ps1           ✅ Перенесён из infra/scripts/
│   ├── log-rotation.ps1                ✅ Перенесён из infra/scripts/
│   └── README.md                       ⏳ Создаётся в Session 17A
├── ci/                                 ⏳ Планируется
│   ├── build-apk.ps1
│   └── run-tests.ps1
├── vault/                              ⏳ Планируется
│   ├── export-supabase-service-key.ps1
│   └── export-yookassa-webhook-secret.ps1
└── README.md                           ✅ Обновлён в Session 17A
```

## Gradle интеграция

Таски созданы в корневом `build.gradle.kts` (Session 17A):

```kotlin
// android/build.gradle.kts или android/scripts/build.gradle.kts
tasks.register<Exec>("runKioskMaintenance") {
    group = "maintenance"
    description = "Run kiosk maintenance utility"
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/kiosk-maintenance.ps1", "-Task", "All", "-DryRun")
}

tasks.register<Exec>("runLogRotation") {
    group = "maintenance"
    description = "Rotate and archive old logs"
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/log-rotation.ps1", "-DryRun")
}

tasks.register<Exec>("checkMavenAccess") {
    group = "verification"
    description = "Check Maven repository accessibility"
    commandLine("bash", "scripts/shell/check-maven-access.sh")
}
```

**Использование**:
```bash
./gradlew runKioskMaintenance
./gradlew runLogRotation
./gradlew checkMavenAccess
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

- **24.11.2025 (Session 17A)**: Волна B — перенос maintenance скриптов из `infra/scripts/`
- **24.11.2025 (Session 15G)**: Создание модуля `scripts/powershell/`
- **24.11.2025 (Session 14G)**: Создание скриптов экспорта секретов
- **23.11.2025 (Session 3G)**: Создание скриптов обслуживания в `infra/scripts/`

## Следующие шаги

1. ✅ Перенести maintenance скрипты → `android/scripts/powershell/maintenance/`
2. ✅ Перенести shell скрипты → `android/scripts/shell/`
3. ✅ Создать Gradle-таски для каждого скрипта
4. ⏳ Обновить `.github/workflows/*` на новые пути (следующая сессия)
5. ⏳ Перенести vault скрипты в `powershell/vault/` (следующая сессия)
6. ⏳ Удалить `infra/scripts/` после обновления workflows

## Лицензия

См. корневой LICENSE файл проекта.
