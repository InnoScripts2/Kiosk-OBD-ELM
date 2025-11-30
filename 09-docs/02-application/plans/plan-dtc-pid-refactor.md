# План реструктуризации DTC/PID словарей (инициация 27.11.2025)

## 1. Контекст
Сессия 41 из `plan-80-session-roadmap.md` требует приведения в порядок источников диагностических кодов (DTC) и PID. Сейчас в репозитории присутствуют:
- `feature-obd-core/src/main/assets/dtc-codes.json` — 2 266 универсальных записей.
- `base/dtcmapping.json` — 3 760 записей, включая расширенные русские описания и дополнительные комбинации датчиков.
- Донорские текстовые коллекции по маркам (`base/All BMW…`, `All Lexus…`, `All Toyota…`, `All Nissan…`, `Generic OBD2…`, `All Manufacturer-specific…`).
- PID-словарь в `feature-obd-core/src/main/assets/pids-mode*.json` (Mode1: 97 строк, Mode4: 1, Mode9: 4) с устаревшей структурой (простые поля без единиц измерения и битовых масок).

Выявленные расхождения:
1. **Объём DTC**. В `dtcmapping.json` на ~1 500 записей больше, чем в текущем активе приложения. Содержимое включает уточнённые формулировки (ряд, датчик, условия).
2. **Формат описаний**. В `dtc-codes.json` многих описаний нет (буквенные placeholders «A/B»), а в `dtcmapping.json` данные богаче. Локализация смешанная (русский, латиница), нет полей категории/уровня критичности.
3. **Производительские расширения**. Текстовые справочники по брендам содержат коды вида `B`, `C`, `U`, которых нет в основном JSON.
4. **PID**. Текущие JSON-файлы не имеют метаданных о типе значения, масштабе, наличии графиков. Список Mode 4/9 фактически заглушки.

## 2. Цель
1. Объединить глобальный справочник DTC из `dtcmapping.json` и текстовых доноров в единый JSON + индекс производителя.
2. Включить в `feature-obd-core` новый формат активов:
   - `dtc-index.json` (универсальные коды + ссылки на расширения по маркам).
   - `dtc-brand-overrides/*.json` (брендовые описания, если отличаются).
3. Обновить PID-справочники: Mode 1, 4, 9 как таблицы с полями `pid`, `description`, `unit`, `min`, `max`, `scaling`, `alerts`.
4. Настроить unit-тесты parity: проверять наличие всех SAE P0*** кодов и ключевых PID.

## 3. Целевой формат файлов
```jsonc
{
  "code": "P0300",
  "class": "powertrain",
  "subsystem": "ignition",
  "severity": "warning",
  "description": "Случайный/множественный пропуск зажигания",
  "details": {
    "ru": "…",
    "en": "…"
  },
  "sources": ["dtcmapping.json", "All Manufacturer-specific OBD2 Codes"],
  "brands": {
    "BMW": {
      "description": "…",
      "notes": "…"
    }
  }
}
```
Аналогично для PID:
```jsonc
{
  "mode": 1,
  "pid": "0C",
  "description": "Обороты двигателя",
  "unit": "rpm",
  "bytes": 2,
  "formula": "((A*256)+B)/4",
  "range": {"min": 0, "max": 16383.75},
  "sources": ["SAE J1979"],
  "status": "supported"
}
```

## 4. План работ по этапам
- [x] Сконвертировать `dtcmapping.json` в таблицу (code, description) и сравнить с `dtc-codes.json` (`tools/dtc/export-dtc-summary.ps1` → `tmp/dtc-summary.csv`).
- [x] Для каждого файлового донора (BMW, Lexus, Nissan, Toyota, Generic, Manufacturer-specific) выделить уникальные коды (`tools/dtc/extract-dtc-brand-codes.ps1` → `tmp/dtc-brand-codes.csv`).
- [x] Подготовить отчёт (CSV) с классами P/B/C/U и принадлежностью к брендам (см. вывод скрипта, включающий статистику по каждому бренду).
- [x] Построить unit-тест, сигнализирующий об отсутствии SAE-кодов (минимум P0001–P0999) — `DtcCatalogParityTest` в `feature-obd-core`.

### Этап B. Новая структура активов (сессии 41–42)
- [x] Сгенерировать `dtc-index.json` и `dtc-brand-overrides/` из CSV (скрипт `android/scripts/dtc/generate-dtc-assets.ps1`).
- [x] Обновить `feature-obd-core` для чтения нового формата, добавить кеш и fallback на брендовые overrides внутри `ObdDictionaryManager`.
- [x] Перенести PID данные в `pids-modeX-v2.json` с полной схемой.
- [x] Добавить unit-тесты на чтение, поиск по брендам, кэширование.

### Этап C. UI/отчёты (сессии 43, 44)
- [ ] Подключить новые описания к `feature-obd-ui` и отчётам (`feature-reports`).
- [ ] Для DEV режима включить диагностику «отсутствует описание».
- [ ] Обновить Supabase отчёт, чтобы хранить коды + нормализованное описание.

### Этап D. Документация и мониторинг (сессия 45)
- [ ] Обновить `AI_AGENT_BRIEFING.md` (секция словарей).
- [ ] Дополнить `BLE_OBD_INTEGRATION_GUIDE.md` ссылкой на новый формат.
- [ ] Включить проверку размера активов в QA чек-лист (не более 1 MB на DTC).

## 5. Требования к тестам
- Kotlin unit: `:feature-obd-core:test` (новые тест-кейсы `DtcCatalogParityTest`, `PidCatalogSchemaTest`).
- Snapshot/JSON schema тест для активов (Gradle task `verifyDtcAssets`).
- Smoke-тест Diagnostics flow: убедиться, что новые описания отображаются.
- QA Dictionary parity (строки 0759–0762 briefing): отчёты писать в change-report.

## 6. Выводы по анализу 27.11.2025
- Количество записей в `dtcmapping.json` (3 760) значительно превышает текущее количество в приложении (2 266), часть описаний теряется.
- PID списки обрезаны до 97 Mode1/1 Mode4/4 Mode9 записей, что не соответствует SAE J1979.
- Текстовые доноры содержат бренды с кодами B/C/U, которых нет в JSON.
- Требуется автоматизированная миграция, чтобы избегать ручного редактирования.

## 7. Связанные артефакты
- Change-report: `outbox/change-reports/2025-11-27-session-bootstrap.md` (инициация сессии 41).
- Change-report: `outbox/change-reports/2025-11-27-session41-dtc-plan.md` (план работ).
- Change-report: `outbox/change-reports/2025-11-27-session41-dtc-export.md` (экспорт CSV и статистика).
- Плановая таблица: `plan-80-session-roadmap.md` (строка 41).
- Следующие change-reports будут фиксироваться для каждого этапа.
