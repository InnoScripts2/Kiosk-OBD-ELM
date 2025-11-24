# Веб-компоненты и Node-агенты

Модуль содержит TypeScript/JavaScript/React компоненты для веб-интерфейсов киоска, локальные Node.js агенты и dev-tools.

## Родительский модуль
`android/platform-ui` — платформенный UI-слой Android-монорепозитория

## Структура (планируемая)

```
web/
├── agent/              # Основной локальный TypeScript сервис (из 03-apps/.../kiosk-shell/agent)
│   ├── src/
│   ├── package.json
│   └── README.md
├── kiosk-agent/        # Актуальный ESM агент (из 03-apps/.../kiosk-agent)
│   ├── src/
│   ├── package.json
│   └── README.md
├── legacy/             # Устаревшие JavaScript/CJS скрипты
│   └── ...
└── README.md           # Этот файл
```

## Текущий статус миграции

### Волна A — UI и Node-инфраструктура

**Планируется перенести**:
1. `03-apps/02-application/kiosk-shell/agent/` → `android/platform-ui/web/agent/`
   - Основной локальный TypeScript сервис (Electron, Node.js)
   - Управляет устройствами (BLE/Serial), замками, платежами, отчётами
   - Хранит конфигурацию и логи

2. `03-apps/02-application/kiosk-agent/` → `android/platform-ui/web/kiosk-agent/`
   - Актуальный ESM агент
   - Редактируется по требованию отдельными задачами

**Требуемые действия**:
- [ ] Физический перенос каталогов
- [ ] Создание Gradle-таски для запуска `npm run build`
- [ ] Настройка зависимостей (артефакты → `android/platform-ui/web/dist/`)
- [ ] Обновление CI workflows для работы с новыми путями
- [ ] Создание README каждого агента с инструкциями по переменным окружения

## Gradle интеграция (планируется)

```kotlin
// android/platform-ui/build.gradle.kts
tasks.register<Exec>("buildWebAgent") {
    workingDir = file("web/agent")
    commandLine("npm", "run", "build")
    outputs.dir("web/dist")
}

tasks.register<Exec>("testWebAgent") {
    workingDir = file("web/agent")
    commandLine("npm", "test")
}
```

## Связь с другими модулями

- **feature-obd-core**: Node-агент управляет OBD-адаптерами через BLE/Serial
- **feature-thickness**: Node-агент управляет толщиномером через BLE
- **feature-payments**: Node-агент обрабатывает платежи и генерирует QR-коды
- **feature-reports**: Node-агент генерирует и отправляет отчёты (HTML/PDF, Email/SMS)
- **feature-lock-control**: Node-агент управляет замками выдачи через Arduino Serial

## Переменные окружения

После миграции агенты будут использовать единый `.env` файл в `android/platform-ui/web/agent/.env`:

```bash
# Режим приложения
APP_MODE=DEV|QA|PROD

# Устройства
DEVICE_MOCK_OBD=true|false
DEVICE_MOCK_THICKNESS=true|false
DEVICE_MOCK_LOCK=true|false

# Платежи
PAYMENT_MOCK=true|false

# Supabase
SUPABASE_URL=https://ddaunoxyguqiejrjtwsf.supabase.co
SUPABASE_ANON_KEY=...
SUPABASE_SERVICE_KEY=...

# Прочие
LOG_LEVEL=debug|info|warn|error
```

См. `.env.example` для полного списка.

## Тестирование

```bash
# После миграции
cd android/platform-ui/web/agent
npm install
npm test

# Или через Gradle
cd android
./gradlew :platform-ui:testWebAgent
```

## Безопасность

- Все секреты хранятся в Vault: `kv/selfservice/platform/{service}/{env}`
- Документация: `09-docs/02-application/security/credential-inventory.md`
- Реестр секретов: `plan-secrets-config.md`

## История изменений

- **24.11.2025**: Создание модуля `platform-ui/web/` (Session 15G)
- **24.11.2025**: Обновление `.env.example` агента с новыми Supabase параметрами (Session 14G)
- **23.11.2025**: Создание Node-агента в `03-apps/.../kiosk-shell/agent/` (Session 08)

## Следующие шаги

1. Перенести `03-apps/02-application/kiosk-shell/agent/` в `android/platform-ui/web/agent/`
2. Перенести `03-apps/02-application/kiosk-agent/` в `android/platform-ui/web/kiosk-agent/`
3. Создать Gradle-таски для npm build/test
4. Обновить CI workflows (`.github/workflows/*`)
5. Обновить документацию и ссылки
6. Удалить исходные каталоги из `03-apps/`

## Лицензия

См. корневой LICENSE файл проекта.
