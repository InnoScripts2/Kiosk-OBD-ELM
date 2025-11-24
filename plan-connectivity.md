# План подключения киоска к интернету

**Дата обновления**: 24.11.2025 (Session 14Z)  
**Версия**: 1.2  
**Связанные документы**: `plan-device-monitoring.md`, `plan-offline-resilience.md`, `plan-incident-response.md`, `plan-power-management.md`, `plan-mdm-integration.md`, `plan-ota-updates.md`, `docs/infra/agp-unblock-plan.md`, `docs/infra/agp-access-handbook.md`

## Оглавление
1. [Цели и границы](#1-цели-и-границы)
2. [Каналы связи и профили](#2-каналы-связи-и-профили)
3. [Текущий статус реализации](#текущий-статус-реализации)
4. [Текущие блокеры](#текущие-блокеры)
5. [История изменений](#история-изменений)

## 1. Цели и границы
- Обеспечить устойчивое подключение киосков диагностики к интернету для телеметрии, Supabase, OTA и платёжных шлюзов.
- Задать стандартизированный стек транспортов (Ethernet → Wi-Fi → LTE) с автоматическим резервированием и едиными политиками безопасности.
- Определить QoS-метрики, требования к оборудованию, процесс мониторинга и реагирования.

Вне зоны: внутренняя маршрутизация дата-центра, логика приложений поверх сетевого слоя, управление BLE-каналом (см. `plan-ble-session-orchestration.md`).

## 2. Каналы связи и профили
| Приоритет | Канал       | Оборудование                         | Пропускная способность | SLA доступности | Особенности                                                            |
| --------- | ----------- | ------------------------------------ | ---------------------- | --------------- | ---------------------------------------------------------------------- |
| 1         | Ethernet    | Промышленный роутер + PoE коммутатор | ≥50 Мбит/с             | 99.5%           | VLAN сегментация, статический IP или DHCP с резервированием MAC.       |
| 2         | Wi-Fi 5 GHz | Встроенный модем планшета / CPE      | ≥25 Мбит/с             | 98%             | WPA2-Enterprise, EAP-TLS, авто-переподключение каждые 30 мин проверки. |
| 3         | LTE Cat 6   | eSIM/SIM с multi-operator профилем   | ≥10 Мбит/с down / 5 up | 97%             | Частный APN, CGNAT-aware порты, keep-alive каждые 60 сек.              |

- **Failover**: RouterOS/OpnSense (или встроенный модем) управляет маршрутами: Ethernet primary, на loss of carrier → Wi-Fi (auto), при RSSI < -70 dBm или потеря SSID → LTE. Возврат на primary после стабильности 10 мин.
- **Power**: Ethernet PoE предпочтителен (см. `plan-power-management.md`); при LTE требуется отдельный UPS для модема.

## 3. Топология и адресация
1. Локальная сеть киоска (VLAN 120) содержит: планшет, BLE-гейт (если внешний), IP-камера безопасности.
2. Маршрутизация наружу идёт через edge-роутер с firewall-политиками «deny all» + allow-list доменов:
   - Supabase, OTA CDN, MDM API, PSP, NTP. Список версионируется в Git (`network/allowlist.yml`).
3. VPN (WireGuard или IPsec) включается для площадок с требованием сегментации; туннель устанавливается только при Ethernet/ Wi-Fi, LTE использует приватный APN.
4. Адресация внутренних устройств статическая (DHCP reservations). Примеры: kiosk tablet `10.20.x.10`, BLE bridge `10.20.x.20`.

## 4. QoS и эксплуатационные метрики
| Метрика                       | Цель               | Метод измерения               | Реакция                            |
| ----------------------------- | ------------------ | ----------------------------- | ---------------------------------- |
| Latency (до Supabase)         | ≤ 120 мс p95       | ICMP/TCP пинг каждые 30 сек   | Alert WARNING при >200 мс >5 мин.  |
| Packet loss                   | < 1%               | Iperf3 тест каждые 4 часа     | Авто-переключение канала.          |
| Bandwidth доступный (down/up) | ≥ 5 / 2 Мбит/с BLE | Speedtest CLI off-peak        | Плановая проверка сети.            |
| Jitter для WebSocket          | < 30 мс            | Наблюдение TelemetryCollector | Перезапуск сессии/канала.          |
| LTE RSSI                      | > -95 dBm          | Модем AT-команды → telemetry  | Отправить заявку на вынос антенны. |

- QoS политики: DSCP маркировка для диагностического трафика (AF31), фоновые обновления (CS1).
- Scheduler: диагностика/телеметрия приоритет 1, OTA 2, лог-аплоады 3.

## 5. Безопасность сети
1. **SIM/eSIM управление**: профили выдаются централизованно, ICCID привязан к kiosk_id; хранение PIN/PUK в Vault.
2. **APN**: частный APN с статическим IP и IPSec-входом или VPN over LTE. Трафик whitelist'ится на L3.
3. **Firewall**: inbound запрещён; outbound только к утверждённым FQDN (см. `plan-threat-model.md`).
4. **Certificate pinning**: Kiosk App применяет TLS pinning для Supabase/PSP. Wi-Fi EAP-TLS использует сертификаты из `plan-secrets-config.md`.
5. **NAC**: Ethernet порты требуют 802.1X, fallback MAC filtering. При Wi-Fi обязательна ротация ключей 90 дней.
6. **Logging**: вся конфигурация роутеров версионируется, изменения проходят через `plan-incident-response.md` change window.

## 6. Процесс подключения и резервирования
1. **Site survey**: до установки фиксируются параметры RSSI, наличие Ethernet, качество электропитания.
2. **Provisioning**:
   - Настроить роутер: загрузить конфиг из Git, задать VLAN, VPN, правила firewall.
   - Зарегистрировать SIM/eSIM, проверить APN attach, записать ICCID в `plan-device-fleet.md` журнал.
   - Провести каблирование Ethernet, проверить PoE/UPS.
3. **Failover тест**: отключить primary канал, убедиться в переходе на Wi-Fi, затем на LTE.
4. **Документация**: загрузить фото стойки, схемы в `plan-documentation-workflow.md` хранилище (SharePoint/Confluence).

## 7. Мониторинг и алерты
- Источники: Router SNMP, LTE модем AT, Android `NetworkStatsManager`, TelemetryCollector (см. `plan-telemetry-analytics.md`).
- Метрики и алерты:
  - `network_primary_status{channel="ethernet"}` = 0 → предупреждение на 15 мин.
  - `lte_data_usage_daily` > 2 ГБ → уведомление Ops (перерасход).
  - `wifi_rssi_p95` < -75 dBm → тикет на переустановку антенны.
- Логи сетевых событий сохраняются 30 дней локально + 1 год в DWH (`plan-data-warehouse-integration.md`).

## 8. Тестирование и валидация
| Уровень        | Сценарии                                                     | Инструменты                       |
| -------------- | ------------------------------------------------------------ | --------------------------------- |
| Лаборатория    | Имитация обрывов (Ethernet cut, Wi-Fi jam, LTE throttle).    | Network emulator, RF shield box.  |
| Интеграционные | Диагностическая сессия при каждом типе канала, прогон OTA.   | Kiosk QA fleet, Supabase staging. |
| Полевые        | Минимум 3 локации с разными провайдерами, наблюдение 7 дней. | HIL rig + Prometheus dashboards.  |

Pass/Fail: отсутствуют недоставленные отчёты, среднее время восстановления < 2 мин, OTA успешна ≥95%.

## 9. Эксплуатация и реагирование
- Наблюдение выполняет NOC/Ops (см. `plan-team-roles.md`) 24/7; shift handover фиксируется в `plan-support-operations.md`.
- Инциденты связи классифицируются по `plan-incident-response.md`; SLA реакции: критический канал недоступен >15 мин → 30 мин на реакцию.
- При повторяющихся сбоях (>3 инцидентов/нед): эскалация к провайдеру, проработка альтернативной инфраструктуры (Starlink, 5G FWA).

## 10. Риски и меры
| Риск                                 | Импакт  | Митигация                                                                  |
| ------------------------------------ | ------- | -------------------------------------------------------------------------- |
| Отсутствие стабильного Ethernet      | Высокий | Требовать готовность площадки, предусмотреть LTE booster/антенну.          |
| Перегруз LTE сети в часы пик         | Средний | QoS с ограничением фоновых задач, многосимочный роутер с мультиоператором. |
| Компрометация SIM/маршрутизатора     | Высокий | Голосовые PIN, блокировка IMEI, мониторинг аномального трафика.            |
| Несоответствие VPN/Firewall правилам | Средний | Infra-as-code, еженедельные проверки, CI lint сетевых конфигов.            |
| Высокий расход LTE данных            | Средний | Сжатие логов, приоритизация OTA по Ethernet, лимиты в контракте.           |

## 11. План внедрения
1. **Шаблоны конфигураций** (1 нед): подготовить RouterOS/OpnSense профили, Ansible playbook.
2. **Закупка модемов/SIM** (1 нед): согласовать с `plan-hardware-requests.md` и операторами.
3. **Пилот 3 локации** (2 нед): измерить KPI, скорректировать allowlist.
4. **MDM интеграция** (параллельно): распространение Wi-Fi/VPN профилей через `plan-mdm-integration.md`.
5. **GA rollout** (3 нед): 10 киосков/неделю, контроль чёрным списком адресов.

## 12. Следующие шаги
1. Добавить сетевые профили в MDM политики и связать с OTA (`plan-ota-updates.md`).
2. Создать dashboards в Grafana/Prometheus с метриками QoS.
3. Обновить runbooks `plan-offline-resilience.md` и `plan-incident-response.md` ссылкой на процедуры failover.
4. Подготовить чек-лист site survey и включить в `plan-maintenance-schedule.md` регулярный аудит связи.

---

## Текущий статус реализации (24.11.2025, Session 2G)

### Реализовано ✅

#### Session 1G: .env.example переменные
- ✅ `NODE_AGENT_PORT` — порт локального Node.js агента (default: 3000)
- ✅ `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` — интеграция с Supabase
- ✅ `PROMETHEUS_PUSHGATEWAY_URL`, `GRAFANA_API_KEY` — мониторинг и телеметрия
- ✅ `KIOSK_SERIAL_NUMBER`, `HEARTBEAT_INTERVAL_SEC` — идентификация и heartbeat (default: 300s)

#### Session 23 (In Progress): MDM/OTA контур
- 🚧 `DeviceStatusSnapshot` модель (серийник, версия, батарея, сети)
- 🚧 `DeviceStatusReporter` пишет heartbeat в Supabase каждые 5 мин
- 🚧 Supabase outbox категория для device status
- 🚧 UI отображение новой категории очереди

**Статус Session 23**: Частично завершено (базовые модели созданы)
**Требуется**: Supabase таблицы `device_commands`/`device_events`, MDM SDK, OTA/reboot обработка

### Не реализовано ⏳

#### Каналы связи (Section 2)
- ⏳ Ethernet с PoE поддержкой
- ⏳ Wi-Fi 5 GHz с WPA2-Enterprise
- ⏳ LTE Cat 6 с eSIM/SIM multi-operator

**План**: Session 24+ (требуется физическое оборудование)

#### Failover и QoS (Sections 3-4)
- ⏳ RouterOS/OpnSense конфигурация
- ⏳ VLAN сегментация (VLAN 120 для киосков)
- ⏳ QoS политики (DSCP маркировка)
- ⏳ Latency/Packet loss мониторинг

**План**: Session 25+ (зависит от оборудования и сетевой инфраструктуры)

#### Безопасность сети (Section 5)
- ⏳ SIM/eSIM управление и Vault хранение
- ⏳ Частный APN с статическим IP
- ⏳ Firewall allowlist доменов (Supabase, OTA CDN, PSP, NTP)
- ⏳ Certificate pinning для Supabase/PSP
- ⏳ 802.1X для Ethernet

**План**: Session 26+ (интеграция с plan-secrets-config.md)

#### Мониторинг и алерты (Section 7)
- ⏳ SNMP monitoring для роутеров
- ⏳ Prometheus метрики (`network_primary_status`, `lte_data_usage_daily`, `wifi_rssi_p95`)
- ⏳ Alert правила для NOC/Ops

**План**: Session 27+ (требуется Prometheus/Grafana инфраструктура)

---

## Текущие блокеры (24.11.2025, Session 2G)

### Отсутствие тестовой инфраструктуры (критический) ❌

**Воздействие**:
- Невозможно протестировать Ethernet/Wi-Fi/LTE failover
- Невозможно измерить QoS метрики (latency, packet loss, jitter)
- Невозможно проверить VLAN сегментацию и firewall правила
- Невозможно запустить роутер с RouterOS/OpnSense конфигурацией

**Требуется**:
- Физический роутер с RouterOS/OpnSense (или виртуальная машина)
- SIM-карта с LTE для тестирования failover
- Wi-Fi точка доступа с WPA2-Enterprise
- Тестовая VLAN инфраструктура

**План разрешения**: Session 24-25 (после приобретения оборудования)

### Отсутствие MDM провайдера (высокий) ⚠️

**Воздействие**:
- Невозможно централизованно управлять Wi-Fi/VPN профилями
- Невозможно проверить распространение сетевых конфигураций через MDM
- Невозможно тестировать OTA updates через сетевые каналы

**Текущий статус**: `MDM_PROVIDER=NONE` в .env.example

**Требуется**:
- Выбрать MDM провайдера (plan-mdm-integration.md)
- Интегрировать MDM SDK в android/app
- Подготовить Wi-Fi/VPN профили для распространения

**План разрешения**: Session 26+ (в рамках plan-mdm-integration.md)

### Supabase недоступен для heartbeat (средний) ⚠️

**Воздействие**:
- `DeviceStatusReporter` записывает heartbeat только локально
- Невозможно проверить работу Supabase outbox для device status
- Невозможно тестировать мониторинг доступности киосков

**Текущий workaround**:
- Heartbeat записывается в локальные логи
- Supabase sync откладывается до разрешения AGP блокера

**Требуется**:
- Компиляция Android кода (AGP 8.4.1)
- Настройка Supabase таблиц `device_status`, `device_commands`, `device_events`
- Тестирование Supabase REST API интеграции

**План разрешения**: Session 27+ (после разрешения AGP блокера)

---

## 6. AGP & Build Infrastructure

### Проблема доступа к Google Maven Repository

**Обнаружено**: Session 10C (23.11.2025)  
**Проанализировано**: Session 14Z (24.11.2025)  
**Статус**: BLOCKED

#### Описание проблемы
Android Gradle Plugin (AGP) версии 8.4.1 недоступен из-за сетевой блокировки доступа к:
- `dl.google.com` (Google Maven Repository)
- `maven.aliyun.com` (китайские зеркала)

Это блокирует:
- ❌ Сборку Android APK (`./gradlew assembleDebug`)
- ❌ Запуск Android тестов
- ❌ Линтинг и статический анализ

#### Диагностика сети

| Репозиторий | URL | Статус | Примечание |
|------------|-----|--------|------------|
| Google Maven | https://dl.google.com | ❌ БЛОКИРОВАН | Could not resolve host |
| Aliyun mirrors | https://maven.aliyun.com | ❌ БЛОКИРОВАН | Could not resolve host |
| Maven Central | https://repo1.maven.org | ✅ ДОСТУПЕН | AGP только ≤2.3.0 |
| Gradle Plugin Portal | https://plugins.gradle.org | ✅ ДОСТУПЕН | AGP не публикуется там |

#### Стратегии разблокировки

См. детальный анализ в `docs/infra/agp-unblock-plan.md`.

**Приоритетные решения**:

1. **GitHub-hosted runner** (рекомендуется):
   - Использовать `ubuntu-latest` вместо self-hosted runner
   - ИЛИ запросить whitelist для `dl.google.com`
   - Время реализации: 1-2 часа

2. **Корпоративный прокси**:
   - Настроить прокси с доступом к Google Maven
   - Добавить секреты: `PROXY_HOST`, `PROXY_PORT`, `PROXY_USER`, `PROXY_PASSWORD`
   - Время реализации: 2-4 часа

3. **Локальное зеркало Maven (Nexus)**:
   - Развернуть Nexus Repository Manager
   - Загрузить AGP артефакты вручную
   - Время реализации: 4-8 часов

#### Требования к сетевой инфраструктуре

После выбора стратегии:

**Для стратегии Прокси**:
- Доступ к прокси-серверу из GitHub Actions runner
- Whitelist для `dl.google.com` на прокси
- Порты: 80, 443 (HTTP/HTTPS)

**Для стратегии Nexus**:
- Сервер: 4GB RAM, 50GB storage
- Доступ к серверу из GitHub Actions runner
- Порты: 8081 (Nexus UI/API)

**Для стратегии GitHub-hosted**:
- Отсутствие сетевых ограничений для GitHub-hosted runners
- ИЛИ whitelist для `dl.google.com` на организационном уровне

#### Документация

- `docs/infra/agp-unblock-plan.md` - Комплексный план разблокировки (4 стратегии)
- `docs/infra/agp-access-handbook.md` - Руководство по обслуживанию AGP
- `android/session-logs/session-14z.md` - Детальные логи диагностики
- `logs/issues/agp-blocker-14z.json` - Issue log в JSON формате

#### Временные меры

Пока блокер не снят:
- ✅ Разработка TypeScript компонентов
- ✅ Статический анализ Kotlin кода (без компиляции)
- ✅ Написание документации
- ❌ НЕ коммитить Android APK сборки

#### Мониторинг (после снятия блокера)

Добавить в регулярный мониторинг:
- Еженедельная проверка доступности Google Maven (`infra/scripts/check-maven-access.sh`)
- Мониторинг размера Gradle cache (warning при >10GB)
- Проверка времени сборки (baseline: 3-5 минут чистая сборка)

**Алерты**:
- Google Maven недоступен → CRITICAL
- Maven Central недоступен → HIGH
- Nexus недоступен (если используется) → CRITICAL

---

## История изменений

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 22.11.2025 | Первоначальная версия с планом подключения киосков |
| 1.1 | 24.11.2025 | Добавлены текущий статус реализации, текущие блокеры (Session 2G) |
| 1.2 | 24.11.2025 | Добавлен раздел "AGP & Build Infrastructure" с анализом блокировки (Session 14Z) |
