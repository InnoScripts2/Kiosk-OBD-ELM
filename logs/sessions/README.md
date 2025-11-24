# Session Logs

**Дата обновления**: 24.11.2025 (Session 3G)  
**Версия**: 2.0

## Назначение

Каталог `logs/sessions/` содержит JSON-файлы с детальной информацией о каждой завершённой сессии разработки: дата, модули, изменённые файлы, тесты, метрики, блокеры.

## Формат

Каждая сессия представлена двумя файлами:
- `session-{id}.json` — основной лог с детальной информацией
- `session-{id}.json.meta` — метаданные с MD5 checksum

### Примеры

| Session ID | Дата       | Категория | Модуль            | Описание                                  |
|------------|------------|-----------|-------------------|-------------------------------------------|
| 10B        | 23.11.2025 | B         | platform-bluetooth| blessed-kotlin API методов исправление    |
| 10C        | 23.11.2025 | C         | feature-obd-core  | BLE scanner интеграция                    |
| 11B        | 23.11.2025 | B         | feature-reports   | ReportService Compose migration           |
| 11C        | 23.11.2025 | C         | feature-obd-core  | BLE state machine critical fixes          |
| 12         | 23.11.2025 | C         | feature-reports   | ReportService полная реализация           |
| 12B        | 24.11.2025 | B         | feature-thickness | Thickness device migration to Kotlin      |
| 12C        | 24.11.2025 | C         | feature-payments  | Payment UI chain error fixes              |
| 1G         | 23.11.2025 | G         | docs, logs        | Comprehensive documentation update        |
| 2G         | 24.11.2025 | G         | docs, plans       | Documentation mass maintenance            |

## Категории сессий

- **B (BLE/OBD Diagnostics)**: Работа с BLE, OBD-II, толщиномером, диагностическими модулями
- **C (Reports/Payments)**: Работа с отчётами, платежами, UI
- **G (Documentation/Infrastructure)**: Документация, логи, скрипты, планы (без изменений .kt/.ts кода)

## Retention Policy

- Session logs хранятся **постоянно** (не удаляются)
- Используются для трассировки изменений, анализа прогресса, аудита
- Для архивации: создать `.archive/` каталог и переместить старые логи (> 1 год)

## Связанные документы

- [logs/README.md](../README.md) — полный формат файлов
- [plan-80-session-roadmap.md](../../plan-80-session-roadmap.md) — план всех 80 сессий
- [plan-maintenance-backlog.md](../../plan-maintenance-backlog.md) — задачи категории G

## Процедура создания лога

1. Завершить сессию разработки
2. Создать `session-{id}.json` с детальной информацией
3. Вычислить checksum: `jq 'del(.metadata)' session-{id}.json | md5sum`
4. Создать `session-{id}.json.meta` с checksum и метаданными
5. Обновить этот README.md с новой записью в таблице

## История изменений

| Версия | Дата       | Автор          | Изменения                                |
|--------|------------|----------------|------------------------------------------|
| 1.0    | 23.11.2025 | GitHub Copilot | Первоначальная версия (Session 1G)       |
| 2.0    | 24.11.2025 | GitHub Copilot | Полное описание, таблица, процедуры (3G) |

