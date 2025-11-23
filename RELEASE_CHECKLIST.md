# OBD Database & Kiosk Frontend — Release Checklist

Данный документ содержит чек-лист действий перед релизом новой версии каталога DTC и/или фронтенда киоска.

## Версионирование

Используем семантическое версионирование (Semantic Versioning 2.0.0):

- **MAJOR** (X.0.0) — несовместимые изменения API или структуры данных
- **MINOR** (0.X.0) — добавление функциональности с обратной совместимостью
- **PATCH** (0.0.X) — исправления ошибок

### Примеры

- Добавление новых производителей в каталог → MINOR
- Исправление неверных описаний DTC → PATCH
- Изменение формата JSON (breaking change) → MAJOR
- Новый экран в UI киоска → MINOR
- Исправление багов в навигации → PATCH

## Pre-Release Checklist

### 1. Подготовка данных (DTC Catalog)

- [ ] Проверить актуальность источников данных в `android/base/`
- [ ] Обновить версию каталога (формат: `MAJOR.MINOR.PATCH`)
- [ ] Запустить генерацию каталога:
  ```bash
  cd android
  python3 tools/dtc/build_catalog.py --version X.Y.Z
  ```
- [ ] Проверить размеры артефактов:
  - `dtc.json` должен быть ~2-3 MB
  - `dtc_database.json` должен быть ~8-10 MB
  - `catalog_version.json` должен содержать корректную метаинформацию
- [ ] Проверить количество записей (должно быть >= текущего):
  - Generic entries: ~17,958+
  - Manufacturer entries: ~58,217+
- [ ] Запустить Python тесты:
  ```bash
  cd android/tools/dtc
  pytest test_build_catalog.py -v
  ```
- [ ] Запустить Android тесты для `DtcDataLoader` и `DtcCatalogVersion`:
  ```bash
  cd android
  ./gradlew :platform-data:test
  ```

### 2. Android Integration

- [ ] Обновить версию каталога в `build.gradle.kts` (если требуется)
- [ ] Запустить Gradle task для генерации:
  ```bash
  ./gradlew :platform-data:generateDtcCatalog -PdtcCatalogVersion=X.Y.Z
  ```
- [ ] Проверить что артефакты находятся в правильных директориях:
  - `feature-obd-core/src/main/resources/.../dtc.json`
  - `platform/data/src/main/assets/dtc_database.json`
  - `platform/data/src/main/assets/catalog_version.json`
- [ ] Запустить полный Android build:
  ```bash
  ./gradlew clean build
  ```
- [ ] Проверить размер APK (не должен увеличиться более чем на 10% от предыдущей версии)
- [ ] Запустить instrumented тесты (если доступны):
  ```bash
  ./gradlew connectedAndroidTest
  ```

### 3. Frontend (Kiosk Application)

- [ ] Обновить версию в `apps/kiosk-frontend/package.json`
- [ ] Проверить все зависимости на актуальность:
  ```bash
  cd apps/kiosk-frontend
  pnpm outdated
  ```
- [ ] Запустить линтинг:
  ```bash
  pnpm lint
  ```
- [ ] Запустить проверку типов:
  ```bash
  pnpm type-check
  ```
- [ ] Запустить unit тесты (когда будут добавлены):
  ```bash
  pnpm test
  ```
- [ ] Запустить E2E тесты:
  ```bash
  pnpm test:e2e
  ```
- [ ] Создать production build:
  ```bash
  pnpm build
  ```
- [ ] Проверить размер bundle (должен быть < 1 MB gzipped для основного chunk)
- [ ] Протестировать preview build локально:
  ```bash
  pnpm preview
  ```
- [ ] Проверить работу на реальном touch-устройстве или эмуляторе

### 4. Документация

- [ ] Обновить `android/tools/dtc/README.md` если изменилась утилита
- [ ] Обновить `android/platform/data/README.md` если изменилась интеграция
- [ ] Обновить `apps/kiosk-frontend/README.md` если изменился frontend
- [ ] Проверить что все примеры кода в документации работают
- [ ] Добавить entry в `CHANGELOG.md` (если есть) с описанием изменений
- [ ] Обновить version badges в README (если есть)

### 5. CI/CD

- [ ] Убедиться что все CI проверки проходят:
  - Python tests
  - Android tests
  - Frontend lint & type-check
  - Frontend build
  - (опционально) E2E tests
- [ ] Проверить что нет warnings в логах сборки
- [ ] Проверить покрытие тестами (должно быть >= 70% для критических модулей)

### 6. Security & Performance

- [ ] Проверить отсутствие уязвимостей в Python зависимостях:
  ```bash
  cd android/tools/dtc
  pip-audit
  ```
- [ ] Проверить отсутствие уязвимостей в npm зависимостях:
  ```bash
  cd apps/kiosk-frontend
  pnpm audit
  ```
- [ ] Убедиться что нет секретов или токенов в коде
- [ ] Проверить производительность каталога (время загрузки < 500ms)
- [ ] Проверить что UI отклик < 200ms на всех экранах

## Release Process

### 1. Создание Release Branch

```bash
git checkout -b release/v1.2.3 develop
```

### 2. Финальная проверка

- [ ] Выполнить все пункты Pre-Release Checklist
- [ ] Закоммитить все изменения:
  ```bash
  git add .
  git commit -m "chore: prepare release v1.2.3"
  ```

### 3. Merge в Main

```bash
git checkout main
git merge release/v1.2.3 --no-ff
```

### 4. Создание Tag

```bash
git tag -a v1.2.3 -m "Release v1.2.3: <краткое описание>"
git push origin main --tags
```

### 5. Deploy (если применимо)

- [ ] Создать GitHub Release с описанием изменений
- [ ] Прикрепить артефакты (APK, frontend build) к релизу
- [ ] Обновить production deployment (если есть автоматизация)
- [ ] Отправить уведомление команде о новом релизе

### 6. Post-Release

- [ ] Merge release branch обратно в develop:
  ```bash
  git checkout develop
  git merge release/v1.2.3 --no-ff
  git push origin develop
  ```
- [ ] Удалить release branch:
  ```bash
  git branch -d release/v1.2.3
  ```
- [ ] Обновить версию в develop на следующую (например, 1.2.4-SNAPSHOT)
- [ ] Проверить что CI/CD работает после мержа

## Rollback Plan

Если в релизе обнаружена критическая ошибка:

1. Откатить изменения в main:
   ```bash
   git revert <commit-hash>
   git push origin main
   ```

2. Или создать hotfix branch от предыдущего stable тега:
   ```bash
   git checkout -b hotfix/v1.2.4 v1.2.2
   # Исправить ошибку
   git commit -m "fix: critical issue"
   git checkout main
   git merge hotfix/v1.2.4
   git tag -a v1.2.4 -m "Hotfix: critical issue"
   git push origin main --tags
   ```

## Metrics to Track

После каждого релиза фиксируем:

- Размер каталога DTC (generic + manufacturer)
- Количество производителей
- Размер APK (Android)
- Размер bundle (Frontend)
- Время загрузки каталога в Android
- Время первого рендера UI
- Покрытие тестами (%)
- Количество известных багов

## Contacts

- **Release Manager**: <имя/email>
- **Android Lead**: <имя/email>
- **Frontend Lead**: <имя/email>
- **QA Lead**: <имя/email>

---

**Версия документа**: 1.0.0  
**Последнее обновление**: 2025-11-23
