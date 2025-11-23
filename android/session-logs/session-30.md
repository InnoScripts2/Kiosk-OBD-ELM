# Session 30 — Mode 09 metadata & catalog parity

Дата: 23.11.2025
Диапазон: Фаза B → Part 3 (Mode09 telemetry hardening + dictionary parity)

## Цели
- Сделать донорский PID-каталог воспроизводимым артефактом (snapshot generator) вместо ручного копирования JSON из AndroidOBD.
- Богатить канонический `PidCatalog` полями `payloadLengthBytes`, чтобы Mode 09 VIN/CAL ID/ECU Name можно было валидировать и визуализировать с ожидаемой длиной полезной нагрузки.
- Завести fail-fast parity-тест для Mode 09, а также убедиться, что импортированный DTC словарь действительно присутствует в `DtcCatalog`.

## Выполненные действия
- Добавлен `ElmPidSnapshotGenerator` + CLI-обёртка в `feature-obd-elm-port/tools` и smoke-тест, который пишет JSON снапшоты донорских PID. Можно запускать через `./gradlew :feature-obd-elm-port:testDebugUnitTest` (тест генерирует артефакты в `tmp`).
- `ObdPidDefinition` и `PidCatalog` расширены полем `payloadLengthBytes`; генератор `build_catalog.py` теперь тащит `Bytes` из AndroidOBD.
- Canonical `pids.json` обновлён для Mode 09 (VIN/CAL ID/ECU Name) с единицами «string», pollInterval=10s и ожидаемой длиной полезной нагрузки.
- `ElmPidDefinitionAdapter` / `ElmPidSnapshotGenerator` умеют прокидывать `payloadLengthBytes`, чтобы parity-тесты видели одинаковые числа.
- Расширен `ElmPidCatalogParityTest` проверкой Mode 09 payload lengths; существующие DTC parity-тесты оставлены, теперь они работают на обновлённых данных.

## Тесты
- `./gradlew :feature-obd-core:testDebugUnitTest :feature-obd-elm-port:testDebugUnitTest`

## Следующие шаги
- Использовать snapshot-артефакты как референс при ingest транспортов (ISO-TP/BLE) и для визуализации Mode 09 в UI.
- Принести аналогичный `payloadLengthBytes` для Mode 01 битовых масок (чтобы UI умел подсвечивать ожидаемый объём данных) и связать с Supabase-трансляцией VIN/ECU.
- Продолжить расширение parity-тестов для DTC (например, сверка альтернативных описаний Body/Chassis) до следующей сессии.
