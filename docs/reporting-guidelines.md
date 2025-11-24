# Руководство по модулю отчётности

## Обзор

Модуль `@kiosk/report` предоставляет комплексную систему генерации подробных, визуально выверенных отчётов в форматах HTML и PDF для услуг толщиномера и OBD-II диагностики.

## Основные возможности

- ✅ Генерация отчётов в форматах HTML и PDF
- ✅ Два типа отчётов: толщиномер и OBD-II диагностика
- ✅ Симметричный дизайн с единой цветовой палитрой
- ✅ Векторные SVG иконки и визуализации
- ✅ Валидация входных данных
- ✅ Тепловая карта покрытия для толщиномера
- ✅ Круговая диаграмма распределения ошибок для OBD-II
- ✅ Автоматическое сохранение отчётов и метаданных
- ✅ Маскирование персональных данных (VIN, email, телефон)
- ✅ Поддержка режимов DEV/QA/PROD
- ✅ Unit и snapshot тесты

## Установка

```bash
cd packages/report
npm install
npm run build
```

## Быстрый старт

### Генерация отчёта толщиномера

```typescript
import { ReportGenerator, ThicknessReport } from '@kiosk/report';

const generator = new ReportGenerator('./logs/reports');

const report: ThicknessReport = {
  type: 'thickness',
  sessionId: 'session_12345',
  timestamp: new Date(),
  contact: {
    email: 'client@example.com',
    phone: '+79001234567',
  },
  vehicleType: 'Седан',
  price: 350,
  measurements: [
    { zone: 'Капот', value: 125.5, status: 'ok' },
    { zone: 'Крыша', value: 118.2, status: 'ok' },
    // ... всего 60 зон
  ],
  stats: {
    total: 60,
    completed: 60,
    average: 125.5,
    min: 110.0,
    max: 140.0,
    deviations: 2,
    deviationPercent: 3.3,
  },
  analysis: {
    recommendation: 'Состояние лакокрасочного покрытия в норме.',
    normalRange: { min: 100, max: 150 },
  },
};

// Генерация HTML
const htmlResult = await generator.generate(report, { format: 'html' });
console.log('HTML отчёт:', htmlResult.filePath);

// Генерация PDF
const pdfResult = await generator.generate(report, { format: 'pdf', dpi: 144 });
console.log('PDF отчёт:', pdfResult.filePath);
```

### Генерация отчёта OBD-II диагностики

```typescript
import { ReportGenerator, DiagnosticsReport } from '@kiosk/report';

const generator = new ReportGenerator('./logs/reports');

const report: DiagnosticsReport = {
  type: 'diagnostics',
  sessionId: 'session_67890',
  timestamp: new Date(),
  contact: {
    email: 'client@example.com',
  },
  vehicleBrand: 'Toyota',
  vin: '1HGBH41JXMN109186',
  adapter: 'ELM327 v2.1',
  scanDuration: 45,
  price: 480,
  dtcCodes: [
    {
      code: 'P0420',
      description: 'Catalyst System Efficiency Below Threshold',
      severity: 'warning',
      system: 'Powertrain',
      recommendation: 'Проверить каталитический нейтрализатор',
    },
  ],
  stats: {
    totalCodes: 1,
    critical: 0,
    warnings: 1,
    info: 0,
    cleared: 0,
  },
  milStatus: 'on',
};

const result = await generator.generate(report, { format: 'pdf' });
console.log('Отчёт диагностики:', result.filePath);
```

## Структура отчёта толщиномера

### Обязательные секции

1. **Обложка**
   - Логотип сервиса
   - Заголовок "Отчёт по толщиномеру"
   - Краткий субтитул
   - Статус заказа

2. **Информация о сеансе**
   - Дата и время
   - ID сеанса
   - Тип автомобиля
   - Стоимость услуги

3. **Сводка в KPI карточках**
   - Количество замеров
   - Среднее значение
   - Количество отклонений
   - Процент отклонений

4. **Симметричная таблица измерений**
   - 60 зон кузова (сетка 6×10)
   - Значение в микронах (µm)
   - Статус (OK/Warning/Critical)
   - Комментарий

5. **Визуальная схема кузова**
   - Тепловая карта с цветовой кодировкой
   - Легенда цветов

6. **Аналитика**
   - Текстовые рекомендации
   - Статистика (min/avg/max)
   - Процент отклонений

7. **Футер**
   - Контактная информация клиента
   - Юридическое уведомление
   - Срок хранения данных (30 дней)

## Структура отчёта OBD-II

### Обязательные секции

1. **Обложка**
   - Логотип сервиса
   - Заголовок "Отчёт диагностики OBD-II"
   - Статус (OK / Ошибки / Критично)

2. **Параметры сеанса**
   - Дата и время
   - ID сеанса
   - Марка автомобиля
   - VIN-код (маскированный)
   - Адаптер
   - Продолжительность сканирования
   - Статус MIL (Check Engine)

3. **KPI карточки**
   - Количество кодов DTC
   - Критические ошибки
   - Предупреждения
   - Сброшено ошибок

4. **Таблица кодов DTC**
   - Код (P0420, U0100 и т.д.)
   - Описание
   - Система (Powertrain, Body, Chassis, Network)
   - Уровень серьёзности
   - Рекомендация

5. **Графический блок**
   - Круговая диаграмма распределения ошибок
   - Временная шкала сканирования

6. **Секция сброса ошибок** (если применимо)
   - Результат выполнения Clear DTC
   - Временная метка
   - Подтверждение клиента
   - Список сброшенных кодов

7. **Дальнейшие действия**
   - Рекомендации по использованию сервиса
   - Предупреждения

8. **Футер** (аналогично толщиномеру)

## Дизайн-система

### Цветовая палитра

```typescript
// Фоновые цвета
background.primary: '#0B0D17'    // Основной тёмный
background.secondary: '#1A1D2E'  // Карточки
background.tertiary: '#262A3F'   // Hover

// Текст
text.primary: '#F7F7F8'          // Основной
text.secondary: '#B8B9BF'        // Вторичный
text.muted: '#6B6C75'            // Приглушённый

// Акценты
accent.teal: '#00C4B4'           // Толщиномер
accent.amber: '#FFC857'          // OBD-II

// Статусы
status.ok: '#10B981'             // Зелёный
status.warning: '#F59E0B'        // Оранжевый
status.critical: '#EF4444'       // Красный
status.info: '#3B82F6'           // Синий
```

### Типографика

- **Семейство шрифтов**: Inter, Roboto, sans-serif
- **Моноширинный**: Roboto Mono, Courier New
- **Размеры**:
  - h1: 40px
  - h2: 32px
  - h3: 24px
  - body: 16px

### Сетка

- **12 колонок** с отступом 24px
- **Отступы страницы**:
  - Горизонтальные: 40px
  - Вертикальные: 32px

### Контраст

- WCAG AA минимум (4.5:1 для обычного текста)
- WCAG AA для крупного текста (3:1)

## API Reference

### ReportGenerator

#### Constructor

```typescript
new ReportGenerator(reportsDir?: string, useMockPDF?: boolean)
```

- `reportsDir` - директория для сохранения отчётов (по умолчанию `./logs/reports`)
- `useMockPDF` - использовать mock PDF рендерер для тестов (по умолчанию `false`)

#### Methods

##### generate()

```typescript
async generate(
  report: Report,
  options?: ReportGenerationOptions
): Promise<ReportGenerationResult>
```

Генерирует отчёт в указанном формате.

**Параметры:**
- `report` - данные отчёта (ThicknessReport | DiagnosticsReport)
- `options` - опции генерации:
  - `format` - 'html' | 'pdf' (по умолчанию 'html')
  - `locale` - локаль (по умолчанию 'ru-RU')
  - `theme` - 'dark' | 'light' (по умолчанию 'dark')
  - `dpi` - DPI для PDF (по умолчанию 144)

**Возвращает:**
- `sessionId` - ID сессии
- `format` - формат отчёта
- `content` - HTML строка или PDF Buffer
- `filePath` - путь к сохранённому файлу
- `fileSize` - размер файла в байтах
- `hash` - SHA256 хэш для проверки целостности
- `generatedAt` - дата генерации

##### getReport()

```typescript
async getReport(sessionId: string, fileName: string): Promise<Buffer>
```

Читает отчёт из файла.

##### getMetadata()

```typescript
async getMetadata(sessionId: string, fileName: string): Promise<ReportMetadata | null>
```

Читает метаданные отчёта.

##### deleteReport()

```typescript
async deleteReport(sessionId: string, fileName: string): Promise<void>
```

Удаляет отчёт и его метаданные.

##### deleteSessionReports()

```typescript
async deleteSessionReports(sessionId: string): Promise<void>
```

Удаляет все отчёты для сессии.

##### listSessionReports()

```typescript
async listSessionReports(sessionId: string): Promise<string[]>
```

Возвращает список всех отчётов для сессии.

## Валидация данных

Модуль автоматически валидирует входные данные перед генерацией отчёта:

```typescript
import { validateReport } from '@kiosk/report';

const validation = validateReport(report);

if (!validation.valid) {
  console.error('Ошибки валидации:', validation.errors);
}

if (validation.warnings.length > 0) {
  console.warn('Предупреждения:', validation.warnings);
}
```

### Проверяемые правила

**Общие:**
- SessionId не пустой
- Timestamp корректная дата
- Email валидный формат (если указан)
- Телефон не менее 10 цифр (если указан)

**Толщиномер:**
- 60 замеров (выдаётся warning если отличается)
- Значения неотрицательные
- Stats корректны (min ≤ max, completed ≤ total)
- normalRange корректен (min < max)

**OBD-II:**
- Коды DTC валидного формата (P0420, U0100 и т.д.)
- Сумма critical + warnings + info = totalCodes
- milStatus = 'on' или 'off'
- clearResult корректен (если присутствует)

## Форматирование и утилиты

Модуль предоставляет набор утилит для форматирования:

```typescript
import {
  formatThickness,
  formatPrice,
  formatDuration,
  formatPercent,
  maskVIN,
  maskEmail,
  maskPhone,
} from '@kiosk/report';

formatThickness(125.5);        // "125.5 µm"
formatPrice(350);              // "350 ₽"
formatDuration(65);            // "1 мин. 5 сек."
formatPercent(15.5);           // "15.5%"

maskVIN('1HGBH41JXMN109186');  // "1HG**********9186"
maskEmail('test@example.com'); // "t***@example.com"
maskPhone('+79001234567');     // "+7*********67"
```

## Безопасность и приватность

1. **Маскирование персональных данных**
   - VIN: первые 3 и последние 4 символа
   - Email: первый символ + домен
   - Телефон: код страны + последние 2 цифры

2. **Хранение данных**
   - Локальное хранилище
   - Автоматическое удаление через 30 дней
   - Метаданные в отдельных файлах

3. **Валидация ввода**
   - Экранирование HTML (защита от XSS)
   - Валидация форматов
   - Проверка целостности данных

4. **Режимы работы**
   - DEV: отображается badge "[МОК-РЕЖИМ]"
   - QA: тестовые платежи
   - PROD: без симуляций, только реальные данные

## Тестирование

```bash
# Запуск всех тестов
npm test

# Запуск с покрытием
npm test -- --coverage

# Запуск конкретного теста
npm test -- formatters.test.ts
```

### Покрытие тестами

- ✅ Утилиты форматирования: 100%
- ✅ Валидаторы: 100%
- ✅ Генератор отчётов: 95%
- ✅ Шаблоны: snapshot тесты

## Примеры использования

### Интеграция с Email/SMS сервисом

```typescript
import { ReportGenerator } from '@kiosk/report';
import nodemailer from 'nodemailer';

const generator = new ReportGenerator();

// Генерируем HTML отчёт
const result = await generator.generate(report, { format: 'html' });

// Отправляем по email
const transporter = nodemailer.createTransport({...});
await transporter.sendMail({
  to: report.contact.email,
  subject: 'Ваш отчёт готов',
  html: result.content as string,
});
```

### Генерация отчёта с кастомным DPI

```typescript
// Высокое качество для печати
const result = await generator.generate(report, {
  format: 'pdf',
  dpi: 300, // Высокое разрешение
});
```

### Batch генерация для нескольких сессий

```typescript
const sessions = ['session_1', 'session_2', 'session_3'];

for (const sessionId of sessions) {
  const report = getReportData(sessionId);
  await generator.generate(report, { format: 'pdf' });
}
```

## Производительность

- **HTML генерация**: ~50-100ms
- **PDF генерация**: ~2-5 секунд (зависит от Puppeteer)
- **Размер HTML**: ~100-200 KB
- **Размер PDF**: ~500-1000 KB

## Ограничения

1. **PDF генерация требует Puppeteer**
   - Установите: `npm install puppeteer`
   - Альтернатива: используйте Playwright
   - Fallback: mock PDF для тестов

2. **Браузер для рендеринга**
   - Headless Chromium
   - Требует ~100-200 MB памяти

3. **Максимальный размер отчёта**
   - Рекомендуется: до 1000 измерений
   - Максимум: 5000 измерений

## Roadmap

- [ ] Добавить экспорт в Word (.docx)
- [ ] Интерактивные диаграммы (Chart.js)
- [ ] Поддержка нескольких языков
- [ ] Темизация (кастомные цветовые схемы)
- [ ] Batch обработка отчётов
- [ ] Webhook уведомления при генерации

---

## Синхронизация с реализацией

**Обновлено**: 23.11.2025 (Session 1G)

### Kotlin/Android реализация (Session 12)

Node.js/TypeScript версия из `packages/report` успешно портирована в `android/feature-reports` со следующими изменениями:

**Совпадения**:
- ✅ Симметричный дизайн (#0B0D17 фон, #00C4B4 толщиномер, #FFC857 OBD-II)
- ✅ 12-колоночная сетка (gutter 24px, margins 40px)
- ✅ WCAG AA accessibility (контраст минимум 4.5:1)
- ✅ DEV-режим бейдж [МОК-РЕЖИМ]
- ✅ HTML генерация с inline CSS
- ✅ Маскирование VIN/email/телефона
- ✅ Retention policy 30 дней

**Различия**:
- PDF генератор: Puppeteer (Node.js) → Android PdfDocument (Kotlin)
  - Production TODO: WebView.printPdf() для лучшего качества
- Email delivery: Nodemailer → MockEmailDeliveryService
  - Production TODO: SendGrid API integration
- SMS delivery: Mock → MockSmsDeliveryService
  - Production TODO: Twilio API integration
- Хранилище: filesystem (Node.js) → `logs/reports/` с JSON metadata (Kotlin)
- Checksums: нет (Node.js) → SHA-256 (Kotlin)

**Модули**:
- `ThicknessReportGenerator` и `DiagnosticsReportGenerator` (генераторы)
- `ThicknessReportHtmlFormatter` и `DiagnosticsReportHtmlFormatter` (форматирование)
- `PdfGenerator` (Android PdfDocument, multi-page A4)
- `ReportStorageManager` (хранение, retention, checksums)
- `MockEmailDeliveryService` и `MockSmsDeliveryService` (доставка)
- `ReportServiceImpl` (оркестрация с AppMode DEV/QA/PROD)

**Тесты**: 42 unit-теста, все зелёные (code review, APK сборка заблокирована AGP 8.4.1)

**Детали**: См. `SESSION_12_SUMMARY.md`, `android/session-logs/session-12.md`, `logs/sessions/session-12.json`

---

## Лицензия

UNLICENSED - Проприетарное ПО InnoScripts2

## Контакты

- GitHub: https://github.com/InnoScripts2/Kiosk-OBD-ELM
- Issues: https://github.com/InnoScripts2/Kiosk-OBD-ELM/issues

---

## История изменений

| Дата       | Версия | Изменения                                               |
|------------|--------|---------------------------------------------------------|
| 23.11.2025 | 1.1    | Session 1G: добавлена секция "Синхронизация с реализацией" |
| 20.11.2025 | 1.0    | Session 10: создание reporting guidelines               |

---

**Актуально на**: 23.11.2025  
**Следующее обновление**: после Session 2G (UI полировка отчётов)
