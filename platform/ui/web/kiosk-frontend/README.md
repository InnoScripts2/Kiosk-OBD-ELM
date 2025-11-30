# Kiosk Frontend - Modular Architecture

## Quick Start (Локальная разработка)

```bash
cd android/platform/ui/web/kiosk-frontend
npm install
cp .env.dev .env    # Используйте DEV-конфигурацию
npm run dev
```

Откройте `http://localhost:5173` в браузере. Интерфейс работает сразу после запуска.

### Подача сборки в локальный агент `/ui`

1. Соберите фронтенд: `npm run build`
2. Запустите агент: `npm --prefix 03-apps/02-application/kiosk-shell/agent run dev`
3. Агент автоматически найдёт `platform/ui/web/kiosk-frontend/dist` и отдаст его по `http://localhost:7070/ui`
4. Android WebView теперь по умолчанию грузит `/ui` с локального агента, Supabase хранит только резервную копию
5. Путь/префикс можно переопределить переменными агента `KIOSK_UI_DIST_PATH`, `KIOSK_UI_ROUTE_PREFIX`, `KIOSK_UI_INDEX_FILE`

### Режимы работы

| Режим | Supabase  | Mock устройств | Кнопка "Пропустить" |
| ----- | --------- | -------------- | ------------------- |
| DEV   | Отключён  | Включены       | Да                  |
| PROD  | Требуется | Отключены      | Нет                 |

Файлы конфигурации:
- `.env.dev` — DEV-режим (Supabase отключён, mock-режимы включены)
- `.env.example` — шаблон PROD-режима (требует настройки Supabase)

Для PROD-режима скопируйте `.env.example` в `.env` и заполните Supabase credentials.

## Overview

This is the modernized kiosk self-service frontend built with vanilla JavaScript ES modules and Vite. The architecture follows clean separation of concerns with no reactive framework dependencies.

## Architecture

```
apps/kiosk-frontend/
├── src/
│   ├── core/              # Core functionality modules
│   │   ├── config.js      # Configuration management
│   │   ├── api-client.js  # REST API wrapper with retry logic
│   │   ├── navigation.js  # Screen navigation and routing
│   │   ├── device-status.js # WebSocket device status updates
│   │   ├── payment-client.js # Payment intent and polling
│   │   ├── session-manager.js # Session state and idle timeout
│   │   ├── error-handler.js # Global error handling
│   │   └── dev-mode.js    # Dev mode activation and UI
│   ├── screens/           # Screen-specific logic (future)
│   ├── utils/             # Utility functions
│   │   ├── debounce.js
│   │   ├── formatters.js
│   │   └── validators.js
│   └── main.js            # Entry point
├── tests/                 # Playwright tests
├── styles.css            # Global styles with WCAG AA support
├── index.html            # Main HTML structure
├── service-worker.js     # PWA service worker with caching strategies
├── vite.config.js        # Vite build configuration
└── playwright.config.js  # Playwright test configuration
```

## Development

### Prerequisites

- Node.js 18+
- npm 9+

### Setup

```bash
cd apps/kiosk-frontend
npm install
```

### Development Server

```bash
npm run dev
```

Runs Vite dev server on `http://localhost:5173` with HMR.

### Build

```bash
npm run build
```

Builds optimized production bundle to `dist/`.

### Preview

```bash
npm run preview
```

Preview the production build locally.

### Testing

```bash
npm test              # Run all Playwright tests
npm run test:ui       # Run tests in UI mode
```

## Features

### 1. Modular ES Architecture

- Clean separation of concerns
- ES6 modules for better code organization
- Tree-shaking for optimal bundle size
- No framework dependencies

### 2. Service Worker Caching

Implements intelligent caching strategies:

- **Cache-first**: Static assets (JS, CSS, images)
- **Network-first**: API requests with fallback to cache
- **Stale-while-revalidate**: HTML pages
- Automatic cache versioning and cleanup

### 3. Dev Mode Isolation

Dev mode is completely isolated from production:

- **Activation**: 
  - Keyboard: `Ctrl+Shift+D`
  - Touch: 3 fingers for 5 seconds
- **Storage**: `localStorage.devMode` (not URL parameter)
- **Tree-shaking**: Dev code removed in production builds
- **Visual indicator**: Red banner when active

### 4. WCAG AA Accessibility

- Color contrast ratio: 4.5:1 minimum
- Touch targets: 44x44px minimum
- Keyboard navigation support
- Focus visible states
- ARIA labels and semantic HTML
- Screen reader support
- Reduced motion support
- High contrast mode support

### 5. Real-time Device Status

- WebSocket connection for live updates
- Automatic reconnection with exponential backoff
- Progress indicators
- Status badges

### 6. Payment Integration

- Intent creation and status polling
- DEV mode confirmation bypass
- Polling interval: 2 seconds
- Automatic stop on completion

### 7. Session Management

- Idle timeout: 120 seconds
- Automatic state persistence
- Session ID generation
- Auto-reset to attract screen

## Configuration

Configuration is loaded from multiple sources:

1. URL parameters: `?apiPort=7070&agent=http://localhost:7070`
2. localStorage: `AGENT_API_BASE`
3. Defaults: `http://localhost:7070`

### Environment Variables

Via Vite `import.meta.env`:

- `import.meta.env.DEV` - Development mode
- `import.meta.env.PROD` - Production mode
- `VITE_SHOW_DELIVERY_MONITOR` — разрешает вывод диагностической панели Supabase в PROD. В DEV/QA панель включена всегда, но для отладки можно передать `?deliveryMonitor=1` в URL. Для e2e или локального обхода предусмотрен флаг `window.__DISABLE_DELIVERY_MONITOR__ = true`, который полностью скрывает панель даже в DEV.

### Delivery monitor overlay

- В DEV/QA панель Supabase отображается автоматически и помогает инженерам видеть состояние очереди.
- В PROD по умолчанию панель **выключена**. Включить её можно установкой `VITE_SHOW_DELIVERY_MONITOR=true` перед сборкой или добавив к URL параметр `?deliveryMonitor=1`.
- Приложение выставляет `data-delivery-monitor="true|false"` на `<body>`, поэтому админские стили могут реагировать на состояние. Если требуется полностью убрать панель на стенде, установите `window.__DISABLE_DELIVERY_MONITOR__ = true` через DevTools и обновите страницу.

### Supabase hosting & storage (резерв)

Основной канал теперь — локальный агент `/ui`. Supabase Storage используется только как резервный CDN и для QA/дистанционных стендов.

1. Скопируйте `.env.example` → `.env.local` и заполните `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` (см. Vault / `09-docs/02-application/security/local-credential-notes.local.md`).
2. Установите Supabase CLI (`npm install -g supabase`), выполните `supabase login` и `supabase link --project-ref ddaunoxyguqiejrjtwsf`.
3. Выполните `npm run build` — получится тот же `dist/`, который читает агент (или передайте `-SkipBuild` скрипту ниже, если сборка уже выполнена).
4. Для публикации резерва используйте скрипт `scripts/powershell/publish-kiosk-frontend.ps1`. Он может очистить бакет (`-ClearBucket`), выгрузить `dist/` и сразу синхронизировать Android-строку `kiosk_url_remote` (`-UpdateAndroidString`). Для busting-а Supabase CDN добавьте `-AppendCacheBuster`:

  ```powershell
  pwsh scripts/powershell/publish-kiosk-frontend.ps1 -ClearBucket -UpdateAndroidString -AppendCacheBuster
  ```

  При необходимости можно вручную вызвать `supabase storage cp -r dist ss:///kiosk-ui`.
5. Если флаг `-UpdateAndroidString` не использовался, вручную проверьте/обновите `kiosk_url_remote` в `app/src/main/res/values/strings.xml` (обычно это `https://ddaunoxyguqiejrjtwsf.supabase.co/storage/v1/object/public/kiosk-ui/index.html` + `?v=<timestamp>`).

Логика деплоя через Supabase остаётся в документации, но не используется в продовой цепочке по умолчанию.

### Runtime Supabase configuration

- `assets/runtime-config.js` подхватывает `kiosk-settings` из `localStorage`, публикует API `window.__kioskRuntimeConfig` и синхронизирует `window.__supabaseConfig` до загрузки модулей.
- Настройки открываются в киоске через кнопку ⚙️ (доступна в DEV или по клавишам `Ctrl+Shift+S`). Выберите источник Supabase, заполните URL и публичный `anon` ключ, затем сохраните изменения.
- После сохранения скрипт отправляет событие `supabase:config-change`, и `src/bootstrap/supabase-client.js` пересоздаёт клиент без перезагрузки UI.
- Переключение обратно на локальный агент очищает Supabase-конфиг на лету, поэтому режим read-only не мешает автономной работе киоска.

## Service Worker

The service worker implements three caching strategies:

### Cache-first Strategy

Used for static assets that rarely change:
- JavaScript bundles
- CSS files
- Images, fonts
- SVG icons

### Network-first Strategy

Used for dynamic content:
- API calls (`/api/*`)
- WebSocket connections
- Dynamic pages

### Stale-while-revalidate Strategy

Used for HTML pages:
- Serves cached version immediately
- Updates cache in background
- Ensures fast page loads

### Cache Invalidation

- Version-based cache names
- Automatic cleanup of old caches
- Manual cache clear via dev tools

## Testing

### Test Structure

```
tests/
├── navigation.spec.js      # Screen navigation tests
├── accessibility.spec.js   # WCAG AA compliance tests
├── dev-flag.spec.js       # Dev mode tests
└── *.spec.js              # Additional test suites
```

### Running Tests

```bash
# All tests
npm test

# Specific suite
npx playwright test navigation

# UI mode
npm run test:ui

# Debug mode
npx playwright test --debug
```

### Accessibility Testing

Uses `@axe-core/playwright` to verify WCAG AA compliance:

- Automated violation detection
- Color contrast verification
- Touch target size validation
- Keyboard navigation checks

## API Integration

### REST API

```javascript
import { apiClient } from '@core/api-client';

// GET request
const data = await apiClient.get('/api/endpoint');

// POST request
const result = await apiClient.post('/api/endpoint', { key: 'value' });
```

### WebSocket

```javascript
import { deviceStatus } from '@core/device-status';

// Subscribe to status updates
const unsubscribe = deviceStatus.subscribe((payload) => {
  console.log('Status update:', payload);
});

// Unsubscribe
unsubscribe();
```

## Build Optimization

Vite configuration includes:

- **Code splitting**: Core, screens, and vendor chunks
- **Minification**: ESBuild in production
- **Tree-shaking**: Dead code elimination
- **Source maps**: Only in development
- **Asset optimization**: Images, fonts optimized

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers with ES6 support

## Security

- No secrets in client code
- XSS protection via content escaping
- CSP headers (recommended)
- CORS configuration on backend
- No eval() or inline scripts

## Performance

- First Contentful Paint: < 1.5s
- Time to Interactive: < 3.5s
- Lighthouse score: > 90
- Bundle size: < 200KB (gzipped)

## Troubleshooting

### Service Worker not updating

1. Clear browser cache
2. Unregister service worker in DevTools
3. Hard reload (Ctrl+Shift+R)

### Dev mode not activating

1. Check localStorage: `localStorage.getItem('devMode')`
2. Try keyboard shortcut: Ctrl+Shift+D
3. Check browser console for errors

### WebSocket connection failing

1. Verify agent is running
2. Check WebSocket URL in config
3. Inspect network tab for connection errors

## Contributing

1. Follow existing code style
2. Add tests for new features
3. Update documentation
4. Run linter before commit
5. Ensure accessibility standards

## License

See root LICENSE file.
