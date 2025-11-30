# Change Report — 03.12.2025 (Session 48 — Admin bridge command bus)

## Цели

- Добавить видимый журнал событий в панели Admin Bridge, чтобы операторы и будущие Windows/Android клиенты видели историю действий и предупреждений.
- Ввести единый шина команд (`adminbridge:command`) с whitelabel-обработчиками для переключения DEV-режима, смены источника данных, удалённого сброса таймлайна и навигации по экранам киоска.
- Обновить публичный API `window.__kioskAdminBridge` для локальных сценариев (отправка команд, управление журналом, чтение истории).

## Действия

1. `platform/ui/web/kiosk-frontend/index.html`:
   - Добавлен блок «События» в Admin Bridge (HTML + JS): хранение до 40 записей, форматирование времени, кнопка очистки, ограничения по DEV-режиму.
   - Реализованы `recordAdminEvent`, `renderAdminEvents`, очистка и форматирование журнала, логирование handshake'ов, отказов по origin и удалённых команд.
   - Введён `adminbridge:command` обработчик, таблица разрешённых команд (`devmode:set|toggle`, `timeline:reset`, `settings:update`, `session:navigate`) с валидацией, ответами ACK и унифицированным описанием источников.
   - Расширены API `window.__kioskAdminBridge` (sendCommand, clearEvents, recordEvent, getEvents) и локальные сервисные вызовы (`requestTimelineReset` теперь оборачивает контекст).
   - Улучшена телеметрия событий: переключения DEV-режима, смена источника данных, сброс таймлайна, подключение MessagePort/окон, ошибки ACK фиксируются в журнале и отправляются удалённым подписчикам.
2. `platform/ui/web/kiosk-frontend/styles.css`:
   - Добавлены стили для нового журнала: адаптивная сетка, цветовые коды по severity, скролл-лист, состояния пустого списка и кнопки очистки.

## Команды

- `npm run build`

## Результаты

- Vite build успешен: 91 модуля, main JS 188.94 kB (gzip 51.22 kB), основной CSS 40.24 kB (gzip 8.92 kB), `index.html` 206.54 kB (gzip 47.19 kB).
- Supabase типы синхронизированы перед сборкой (`sync:supabase-types`), артефакт `dist/` обновлён без ошибок.
- Admin Bridge теперь стримит журнал событий внешним клиентам (BroadcastChannel/MessagePort/postMessage) и принимает нормализованные команды с whitelisted действиями, повышая готовность к выделенным Windows/Android админ-приложениям.

## Следующие шаги

- Подключить в журнал события Supabase outbox/agent heartbeat (сессия 45) и отображение SLA платежей (сессия 44).
- Расширить командный набор `adminbridge:command` обработчиками для управления устройствами (выдача замков, перезапуск агента) и публикации отчётов в Supabase dashboard.
