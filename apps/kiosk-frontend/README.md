# Kiosk Frontend Application

Фронтенд-приложение для терминала самообслуживания автодиагностики и толщинометрии ЛКП.

## Технологический стек

- **React 18.3** — UI библиотека
- **TypeScript 5.6** — строгая типизация
- **Vite 5.4** — быстрая сборка и dev server
- **React Router 6** — навигация между экранами
- **Tailwind CSS 3.4** — utility-first стилизация
- **Vitest** — unit тестирование
- **Playwright** — E2E тестирование

## Быстрый старт

### Установка зависимостей

Проект использует **pnpm** как менеджер пакетов:

```bash
cd apps/kiosk-frontend
pnpm install
```

### Запуск dev-сервера

```bash
pnpm dev
```

Приложение будет доступно по адресу http://localhost:3000

### Сборка production

```bash
pnpm build
```

Результат сборки будет в папке `dist/`.

### Превью production сборки

```bash
pnpm preview
```

## Разработка

### Линтинг

```bash
pnpm lint
```

### Форматирование кода

```bash
pnpm format
```

### Проверка типов

```bash
pnpm type-check
```

## Тестирование

### Unit тесты (Vitest)

```bash
pnpm test
```

С UI интерфейсом:

```bash
pnpm test:ui
```

### E2E тесты (Playwright)

```bash
# Установить браузеры (только первый раз)
npx playwright install

# Запустить тесты
pnpm test:e2e
```

## Структура проекта

```
apps/kiosk-frontend/
├── src/
│   ├── components/       # Переиспользуемые компоненты
│   ├── pages/           # Экраны приложения
│   │   ├── AttractScreen.tsx       # Экран 1: Привлечение внимания
│   │   ├── WelcomeScreen.tsx       # Экран 2: Приветствие и согласие
│   │   └── ServiceSelectionScreen.tsx  # Экран 3: Выбор услуги
│   ├── services/        # API и бизнес-логика
│   ├── styles/          # Глобальные стили
│   │   └── index.css    # Tailwind + кастомные стили
│   ├── App.tsx          # Главный компонент с роутингом
│   └── main.tsx         # Точка входа
├── tests/
│   └── e2e/             # E2E тесты
│       └── kiosk.spec.ts
├── index.html           # HTML шаблон
├── vite.config.ts       # Конфигурация Vite
├── tailwind.config.js   # Конфигурация Tailwind CSS
├── tsconfig.json        # Конфигурация TypeScript
├── playwright.config.ts # Конфигурация Playwright
└── package.json
```

## Дизайн система

### Цвета

- **Primary**: `#0066CC` — основной синий
- **Secondary**: `#00AA66` — акцентный зелёный
- **Error**: `#CC0000` — ошибки
- **Warning**: `#FF9900` — предупреждения
- **Success**: `#00AA00` — успех
- **Background**: `#F5F5F5` — фон
- **Surface**: `#FFFFFF` — карточки/поверхности

### Типографика

Все размеры оптимизированы для сенсорных экранов:

- **Title**: 3rem (48px) — заголовки страниц
- **Heading**: 2rem (32px) — заголовки секций
- **Body**: 1.25rem (20px) — основной текст
- **Button**: 1.5rem (24px) — кнопки

### Spacing

- **xs**: 0.5rem (8px)
- **sm**: 1rem (16px)
- **md**: 1.5rem (24px)
- **lg**: 2rem (32px)
- **xl**: 3rem (48px)

### Компоненты

Используйте готовые Tailwind классы из `src/styles/index.css`:

```tsx
// Кнопки
<button className="kiosk-button-primary">Продолжить</button>
<button className="kiosk-button-secondary">Назад</button>

// Карточки
<div className="kiosk-card">...</div>

// Инпуты
<input className="kiosk-input" />
```

## UX принципы

1. **Самообслуживание**: клиенту очевидно что делать на каждом шаге
2. **Минимум касаний**: удалены все необязательные поля и подтверждения
3. **Крупные элементы**: минимальная высота кнопок 60px
4. **Быстрый отклик**: переходы < 200ms
5. **Контрастность**: WCAG AA минимум

## Навигация

```
/ (Attract) 
  → /welcome (Welcome + Terms)
    → /services (Service Selection)
      → /thickness/* (Толщинометрия)
      → /diagnostics/* (Диагностика OBD-II)
```

## Режимы работы

### DEV режим

- Доступна навигация без реальных устройств
- Логирование действий в консоль
- Можно пропустить некоторые шаги

### Production режим

- Все действия требуют реальных устройств
- Нет кнопок пропуска
- Полная валидация

Режим определяется переменной окружения:

```env
VITE_APP_MODE=production  # или development
```

## CI/CD

При push в репозиторий автоматически запускаются:

1. Линтинг (`pnpm lint`)
2. Проверка типов (`pnpm type-check`)
3. Unit тесты (`pnpm test`)
4. E2E тесты (`pnpm test:e2e`)
5. Сборка (`pnpm build`)

## Troubleshooting

### Ошибка при установке зависимостей

Убедитесь что у вас установлен **pnpm**:

```bash
npm install -g pnpm
```

### Playwright браузеры не установлены

```bash
npx playwright install
```

### Vite dev server не запускается

Проверьте что порт 3000 свободен:

```bash
lsof -i :3000  # macOS/Linux
netstat -ano | findstr :3000  # Windows
```

## Лицензия

Проприетарное ПО. Все права защищены.
