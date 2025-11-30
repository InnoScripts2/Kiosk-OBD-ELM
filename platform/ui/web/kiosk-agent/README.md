# Kiosk Agent (ESM)

## Статус
⚠️ **Placeholder**: Этот каталог зарезервирован для будущей миграции актуального ESM агента из `03-apps/02-application/kiosk-agent/`.

## Родительский модуль
`android/platform/ui/web` — Node/TypeScript агенты Android-монорепозитория

## Назначение
Актуальный локальный агент на ESM TypeScript. Используется отдельными задачами и редактируется по требованию.

## Планируемая структура
```
kiosk-agent/
├── src/
│   ├── index.ts
│   └── ...
├── package.json
├── tsconfig.json
└── README.md
```

## Миграция
Согласно `plan-multi-language-consolidation.md` Волна A:
- Исходный каталог: `03-apps/02-application/kiosk-agent/` (не существует на момент Session 16A)
- Целевой каталог: `android/platform/ui/web/kiosk-agent/`
- Дата резервирования: 24.11.2025
- Session: 16A

## Связь с другими модулями
- **agent**: Основной Node-сервис с расширенными возможностями
- **feature-obd-core**: Управление OBD-адаптерами
- **feature-thickness**: Управление толщиномером
- **feature-lock-control**: Управление замками

## Примечания
До появления исходного каталога используется основной `agent/` для всех задач.
