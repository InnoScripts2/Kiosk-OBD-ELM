# Архивная нотификация — Миграция Node-агентов

**Дата**: 24.11.2025  
**Сессия**: 16A  
**Категория**: Волна A — UI и Node-инфраструктура

## Статус миграции

### ✅ Завершено

#### 1. `03-apps/02-application/kiosk-shell/agent/` → `android/platform/ui/web/agent/`

**Перенесено**:
- 14 файлов (~260 KB)
- TypeScript сервисы: PaymentService, LockController, ReportService, ArduinoAdapter
- Тесты: 32 теста (26 passing, 6 failing — mock mode issues)
- Конфигурация: package.json, tsconfig.json, jest.config.js, .eslintrc.cjs
- Документация: README.md, .env.example

**Новый путь**: `android/platform/ui/web/agent/`

**Gradle интеграция**:
```bash
cd android
./gradlew :platform-ui:npmInstallAgent    # npm install
./gradlew :platform-ui:buildWebAgent      # npm run build
./gradlew :platform-ui:testWebAgent       # npm test
./gradlew :platform-ui:lintWebAgent       # npm run lint
```

**Валидация**:
- ✅ npm install: 478 packages, 0 vulnerabilities
- ✅ npm test: 26/32 passing (6 failing — inherited from source)
- ✅ npm lint: ESLint clean

#### 2. Placeholder для kiosk-agent

**Путь**: `android/platform/ui/web/kiosk-agent/`

**Статус**: Зарезервирован для будущей миграции ESM агента

**Причина**: Исходный каталог `03-apps/02-application/kiosk-agent/` не существует на момент Session 16A

### ⏳ Не выполнено

1. **Обновление CI workflows** — требуется в следующей сессии (Волна B)
   - `.github/workflows/node-tests.yml` должен использовать путь `android/platform/ui/web/agent/`

2. **Удаление исходного каталога** — требуется явное подтверждение владельца проекта
   - Каталог `03-apps/` остаётся как read-only донор до получения разрешения

## Связанные документы

- `android/plan-multi-language-consolidation.md` — обновлён с завершением Волны A
- `android/platform/ui/web/README.md` — документация по новому расположению
- `android/platform/ui/build.gradle.kts` — Gradle-таски для npm команд
- `plan-80-session-roadmap.md` — обновление статуса Session 16A

## Блокеры

### AGP 8.4.1 недоступен
- **Статус**: BLOCKED (Session 14Z)
- **Причина**: Сетевая блокировка dl.google.com и maven.aliyun.com
- **Обходное решение**: Запуск npm tasks напрямую вместо Gradle wrapper
- **Требуется**: Снятие блокировки или миграция на GitHub-hosted runner

## Рекомендации

1. **Для следующей сессии (Волна B)**:
   - Обновить `.github/workflows/node-tests.yml`
   - Перенести PowerShell скрипты из `infra/scripts/` в `android/scripts/powershell/`
   - Перенести Shell скрипты в `android/scripts/shell/`

2. **Для удаления 03-apps/**:
   - Получить письменное подтверждение от владельца проекта
   - Создать backup перед удалением
   - Обновить все внутренние ссылки и документацию

3. **Исправление failing tests**:
   - 6 падающих тестов в LockController/ArduinoAdapter mock mode
   - Требуется отдельная сессия для рефакторинга mock logic
   - Не блокирует миграцию (issues существовали в исходном коде)

## Метрики Session 16A

| Параметр                | Значение                     |
| ----------------------- | ---------------------------- |
| Директорий перенесено   | 1 (agent)                    |
| Директорий создано      | 1 (kiosk-agent placeholder)  |
| Файлов перенесено       | 14                           |
| Размер                  | ~260 KB                      |
| npm пакетов             | 478                          |
| Тестов                  | 32 (26 passing, 6 failing)   |
| Vulnerabilities         | 0                            |
| Lint                    | ✅ Clean                      |
| Gradle-таски            | 10 (npm*, build*, test*)     |
| README                  | 2 (web/, kiosk-agent/)       |
| build.gradle.kts строк  | 180                          |

---

**Автор**: BKG Agent (Kiosk Background Maintainer)  
**Контекст**: `.github/instructions/instructions.instructions.md` раздел 4.2, 4.3
