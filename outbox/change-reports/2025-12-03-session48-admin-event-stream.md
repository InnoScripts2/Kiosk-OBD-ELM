# Change Report — 03.12.2025 (Session 48 — Admin event stream + manifest)

## Цели

- Ввести потоковые уведомления `adminbridge:event` для будущих Windows/Android админ‑приложений без необходимости запрашивать полный снапшот.
- Документировать доступные удалённые команды через публичный манифест и раздавать его при handshake вместе с ограничениями журнала событий.
- Расширить локальный API `window.__kioskAdminBridge` вспомогательными методами (`subscribeEvents`, `getCommandManifest`, descriptor событий) для удобной интеграции тестовых панелей.

## Действия

1. `platform/ui/web/kiosk-frontend/index.html`:
   - Добавлен `ADMIN_EVENT_STREAM_DESCRIPTOR` и иммутабельный `ADMIN_COMMAND_MANIFEST`, используемые при инициализации моста и во внешнем API.
   - Реализованы `adminbridge:event` рассылки: локальный `CustomEvent`, подписчики внутри страницы, BroadcastChannel, MessagePort и `postMessage` окна получают инкрементальные обновления без повторного снапшота.
   - Обновлён `recordAdminEvent`, теперь он вызывает новый широковещательный хелпер перед `emitAdminBridge`, а handshake ответ `adminbridge:ack` отправляет snapshot + manifest + параметры журнала.
   - Добавлены подписки `subscribeAdminEvents`, трекинг `adminEventSubscribers`, вспомогательные уведомления об ошибках обработчиков событий.
   - Публичный API `window.__kioskAdminBridge` дополнен полями `events`, `eventLimit`, методами `getCommandManifest`, `subscribeEvents`; `sendCommand` и прочие утилиты продолжают работать без изменений совместимости.

## Команды

- `npm run build`

## Результаты

- Vite build успешен (91 модуля). Артефакты: `dist/assets/main-DGQ9_Cx9.js` 188.94 kB (gzip 51.22 kB), `dist/assets/main-vfs6VhVv.css` 40.24 kB (gzip 8.92 kB), `dist/index.html` 210.89 kB (gzip 47.83 kB), `manifest` 0.38 kB.
- Скрипт `sync:supabase-types` выполнен перед сборкой, `src/integrations/supabase/types.generated.ts` обновлён.
- Admin Bridge теперь отправляет лёгкие event‑payload'ы всем внешним клиентам и делится списком поддерживаемых команд прямо на handshake, упрощая интеграцию Windows/Android админ панелей.

## Следующие шаги

- Завести события для Supabase heartbeat/lock telemetry (сессии 45/46) поверх нового `adminbridge:event` канала.
- Добавить команды управления устройствами (`lock:open`, `agent:reboot`) и телеметрию успешных/неуспешных команд в поток событий, чтобы закрыть требования админ панелей.
