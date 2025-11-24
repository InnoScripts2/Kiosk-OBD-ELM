# report-kit

Комплект для генерации и отправки отчётов (PDF, email, SMS). Перенесён из `packages/report` в рамках Волны D миграции (Session 18G).

## Происхождение
- **Исходный путь**: `packages/report/`
- **Дата переноса**: 24.11.2025
- **Сессия**: 18G
- **Статус исходного каталога**: UTILIZED (помечен для удаления после подтверждения)

## Структура
```
report-kit/
├── src/           # Генераторы отчётов (HTML, PDF)
├── package.json   # Зависимости (jsPDF, nodemailer, twilio и т.п.)
├── tsconfig.json  # TypeScript конфигурация
└── jest.config.js # Тесты
```

## Функциональность
- **Генерация отчётов**: toHTML(), toPDF()
- **Отправка**: sendEmail(), sendSMS()
- **Шаблоны**: толщинометр, диагностика OBD-II

## Связь с модулями
- Используется в `android/feature-reports` для создания клиентских отчётов
- Интеграция с Node-агентом в `android/platform/ui/web/agent/`

## Использование
```bash
# Из корня репозитория
cd android/feature-reports/report-kit
npm install
npm test
npm run build
```

## Планируемая интеграция
- [ ] Создать Gradle-таску `:feature-reports:buildReportKit`
- [ ] Подключить к CI workflow
- [ ] Добавить шаблоны из оригинального пакета

## TODO
- [ ] Проверить наличие всех файлов из packages/report/src/
- [ ] Обновить зависимости до последних версий
- [ ] Добавить тесты на генерацию PDF
- [ ] Интегрировать с Kotlin через bridge
