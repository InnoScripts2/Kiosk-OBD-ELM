# План подключения базы OBD-протоколов и кодов ошибок

## Цели
- Консолидировать все исходники из `android/base` (dtcmapping, каталоги производителей, библиотека `OBDII.DTC`, шпаргалки протоколов) в единую воспроизводимую базу данных.
- Встроить сформированную базу в Android-стек (`platform/data`, `feature-obd-core`, runtime PassThru) без ручного копирования.
- Синхронизировать новый фронтенд-дизайн киоска с кодовой базой, обеспечить единый источник truth для UI.

## Области работ
1. **Data Ingestion Engine**
   - [ ] Создать Python-утилиту `tools/dtc/build_catalog.py`, которая:
     - парсит `base/OBDII.DTC-main/DTC.cs` для системных описаний SAE/ISO;
     - агрегирует `dtcmapping.json` для валидации и обратной совместимости;
     - разбирает текстовые справочники производителей (`All * OBD2 Codes List`, `Generic ...`).
   - [ ] Определить формат выходного артефакта (`dtc_catalog.json`, `manufacturer_catalog.json`).
   - [ ] Добавить unit-тесты на парсинг (минимум по одному сэмплу на систему/производителя).
2. **Android Integration**
   - [ ] Расширить `platform/data` так, чтобы `./gradlew :platform:data:generateDtcCatalog` вызывал новую утилиту.
   - [ ] Обновить `DtcCatalog` и `ManufacturerDtcProvider`, чтобы они потребляли новые артефакты.
   - [ ] Добавить телеметрию о версии каталога в `PassThruDiagnosticsRuntime` и UI.
3. **Frontend Synchronization**
   - [ ] Скопировать свежий дизайн из `android/kiosk-frontend` в моно-репозиторий (`apps/kiosk-frontend`).
   - [ ] Настроить единый pipeline (pnpm + Vite) с линтами/Playwright.
   - [ ] Привести UI к финальному виду: тема, типографика, responsive-сетки, dev-mode панель.
4. **Quality & Release**
   - [ ] Автоматические проверки (Gradle unit+instrumented, Playwright smoke, Python lint/tests).
   - [ ] Документация: README в `tools/dtc`, обновлённый `platform/data/README.md`, onboarding-гайд для фронтенда.
   - [ ] Release checklist (версия каталога, обновление app assets, changelog для киоска).

## Вехи
| Неделя | Результат                                                                                  |
| ------ | ------------------------------------------------------------------------------------------ |
| W1     | Утилита парсинга + тестовые артефакты (`dtc_catalog.json`, `manufacturer_catalog.json`).   |
| W2     | Android билд подтягивает новые каталоги, PassThru runtime показывает расширенные описания. |
| W3     | Обновлённый фронтенд в `apps/kiosk-frontend`, настроены e2e тесты.                         |
| W4     | Интеграция завершена, документация и релизные артефакты готовы.                            |

## Риски и меры
- **Неструктурированный текст**: использовать эвристики + fallback в «raw» описания, добавлять логи.
- **Размер артефактов**: включить дедупликацию, gzip на Gradle таске, lazy-индексацию.
- **Фронтенд зависимостей**: фиксировать версии в `package-lock.json`, добавить renovate job.

## Следующие шаги
1. Реализовать утилиту `build_catalog.py` + минимальные тесты.
2. Настроить Gradle-таску генерации данных и обновить assets.
3. Реплицировать `kiosk-frontend` в `apps/` и начать полировку UI.
