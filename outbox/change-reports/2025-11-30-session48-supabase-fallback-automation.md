# Change Report — 30.11.2025 (Session 48 — Supabase fallback automation)

## Цели

- Исключить ручные шаги при публикации резервного UI в Supabase Storage.
- Обеспечить автоматическое обновление Android-строки `kiosk_url_remote` после выгрузки `dist/`.
- Обновить инструкции и runbooks, чтобы операторы запускали единый скрипт.

## Действия

1. `scripts/powershell/publish-kiosk-frontend.ps1`: добавлены флаги `-UpdateAndroidString`, `-AppendCacheBuster`, `-CacheBusterToken`, `-AndroidStringsPath`; реализован апдейт `app/src/main/res/values/strings.xml` и опциональный cache-busting (`?v=<timestamp>`), расширена справка.
2. `platform/ui/web/kiosk-frontend/README.md`, `03-apps/02-application/kiosk-shell/agent/README.md`: инструкции по публикации теперь рекомендуют запуск с `-ClearBucket -UpdateAndroidString -AppendCacheBuster` вместо ручного редактирования ресурсов.
3. `09-docs/02-application/plans/supabase-setup.md`, `scripts/powershell/README.md`: синхронизированы шаги runbook и описание скрипта, перечислены новые параметры и эффекты.

## Команды

- — (только правки PowerShell и документации)

## Результаты

- Скрипт публикации поддерживает обновление Android fallback URL и генерацию cache-buster токена, исключая устаревшие значения `kiosk_url_remote`.
- Документация и операции Supabase приведены к единому сценарию запуска.

## Следующие шаги

- Привязать запуск скрипта к nightly DevOps пайплайну, чтобы Supabase CDN всегда содержал свежий build.
- Дополнить smoke-тесты проверкой `kiosk_url_remote` перед релизными сборками.
