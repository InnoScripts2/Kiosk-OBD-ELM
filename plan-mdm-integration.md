# План интеграции MDM для киосков диагностики

Дата обновления: 2025-11-22  
Версия: 1.0  
Связанные документы: `plan-80-session-roadmap.md`, `plan-component-migration.md`, `plan-power-management.md` (будет создан), `plan-ota-updates.md`, `plan-security-config.md`, `plan-device-monitoring.md`, `plan-connectivity.md`

## 1. Цели и границы
- **Единый контур управления устройствами**: каждая стойка киоска регистрируется в MDM, получает политики, сертификаты, обновления приложений.
- **Интеграция с OTA**: MDM выступает оркестратором для push-обновлений APK, прошивок BLE-адаптера и конфигураций Supabase/PSP.
- **Наблюдаемость и SLA**: статус устройств, уровень батареи, версия приложения, соответствие политике доступны в Supabase/Prometheus.
- **Соблюдение безопасности**: MDM обеспечивает ротацию сертификатов, запрет sideload, wipe при компрометации, управление VPN/сетями.

Вне зоны (на других планах): генерация контента отчётов, бизнес-процессы платежей, ручные вмешательства DevOps.

## 2. Архитектура и компоненты
| Компонент                 | Описание                                                                                  | Требования                                                                        |
| ------------------------- | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| **MDM Controller**        | SaaS или on-prem сервис (Intune, Scalefusion, Esper, AirWatch).                           | REST / Webhook API, поддержка Android Enterprise Device Owner, Custom OEM Config. |
| **Enrollment Service**    | Набор QR/PIN профилей для заводской регистрации, связывающий серийник и kiosk_id.         | Интеграция с ERP, генерация служебных учетных данных.                             |
| **MDM Agent**             | Приложение на киоске (обычно системное). Включает Device Owner и канал команд.            | Совместимость с Android 12+, поддержка lock task, расписания обновлений.          |
| **Kiosk App Integration** | `feature-kiosk-mode` получает события compliance/command через Broadcast/ContentProvider. | Поддерживает remote reboot, лог загрузок, отчёт об ошибках.                       |
| **Supabase Sync**         | Хранит зеркальные статусы устройств и историю команд.                                     | Обновления не реже 5 мин, REST RPC `insert_device_status`.                        |

## 3. Функциональные требования
1. **Enrollment & Inventory**
   - Поддержка Zero-touch или DPC Identifier (afw#mdmVendor).
   - Каждому устройству присваивается `kiosk_id`, `serial`, `mdm_device_id`.
2. **Политики**
   - Kiosk режим: whitelisted приложения (`com.selfservice.kiosk`, `com.vendor.mdm`), автостарт, блокировка status bar.
   - Сеть: Wi-Fi профили (WPA2-Enterprise), fallback LTE APN, VPN optional.
   - Сертификаты: клиентские TLS, Supabase JWT (через Managed Config).
3. **Команды**
   - Remote update (`installPackage`, `scheduleInstall`), reboot, bug report, file push (для логов).
   - Triggers: при критической телеметрии (температура, батарея) отправлять `showAlert`.
   - Канал доставки: Broadcast `com.selfservice.kiosk.mdm.COMMAND_DISPATCH`, принимается только от пакетов из `mdm.allowedPackages` (Gradle property, comma-separated). Payload — JSON в extras `payload_json`/`metadata_json`.
4. **Compliance**
   - Проверка версии приложения, наличия шифрования, целостности загрузок.
5. **Auditing & Logging**
   - Каждая команда фиксируется в Supabase (`device_commands`), включая исход, латентность.
6. **Resilience**
   - Авто-повтор команд (до 3 попыток) с экспоненциальным backoff и сохранением в очередь, если устройство оффлайн.

## 4. Потоки и сценарии
### 4.1 Жизненный цикл устройства
| Этап                 | Действия                                                                                               | Выход                                                                    |
| -------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| **Provisioning**     | Скан QR → Enrollment profile скачивает MDM agent → устанавливает Device Owner → пушит `kiosk_app.apk`. | Запись в Supabase `devices` со статусом `enrolled_pending`.              |
| **Activation**       | Kiosk App запускается, регистрирует `KioskDiagnostics` и Payments bridge, сообщает в MDM heartbeat.    | Статус `active`.                                                         |
| **Compliance Drift** | Обнаружена устаревшая версия / неизвестный APK.                                                        | MDM создаёт задачу `remediate`, Kiosk App показывает блокирующий баннер. |
| **Maintenance**      | Команда `schedule_ota` → загрузка APK из private CDN → silent install через MDM.                       | Отчёт `ota_result`.                                                      |
| **Decommission**     | Выполнение `factory_reset` + удаление из Supabase.                                                     | Статус `retired`.                                                        |

### 4.2 Команды и уведомления
- `CommandQueueProcessor` (MDM → webhook) записывает команду в Supabase, отправляет в `KioskApp` через FCM или локальный MDM SDK.
- Kiosk App подтверждает приём (`ACK`), выполняет действие, отправляет `RESULT` с логами (например, `ota_install.log`).
- Неуспешные команды (timeout > 10 мин) попадают в `plan-incident-response.md` runbook.

## 5. Политики и профили
| Политика           | Детали                                                                                                                 | Параметры                                                           |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **Wi-Fi/VPN**      | До 3 SSID (prod, qa, fallback LTE). VPN только для корпоративных сетей.                                                | Ротация паролей через Secrets Manager, MDM KPI «получено < 30 мин». |
| **App Catalog**    | Обязательные пакеты: `com.selfservice.kiosk`, `com.vendor.mdm`, `com.selfservice.logger`. Запрет установки прочих APK. | Проверка SHA256 APK при доставке.                                   |
| **Certificates**   | Устанавливаются через Managed Config, обновление каждые 90 дней.                                                       | Делегированное восстановление (MDM → Vault).                        |
| **Power & Screen** | Блокировка сна, яркость фиксированная, авто-ребут раз в 7 дней в 03:00.                                                | Согласовано с `plan-power-management.md`.                           |
| **Security**       | SELinux enforcing, USB debugging off, запрет скриншотов.                                                               | Compliance правила «critical».                                      |

## 6. Интеграция с OTA и DevOps
1. CI/CD публикует подписанный APK → Object Storage (per environment) + checksum.
2. DevOps создаёт MDM-политику `OTA Release nnn` с payload: URL, SHA, target cohort (prod pilot, prod general).
3. MDM пушит обновление, отслеживает установку по устройствам.
4. Supabase Outbox (`DiagnosticsReportOutboxBridge`) не останавливается; при OTA Kiosk App уходит в maintenance screen, сохраняет незавершённые отчёты.
5. При сбое OTA → MDM отправляет ` rollback_to_previous_apk` если доступно (см. `plan-rollback-procedures.md`).

## 7. Мониторинг и алерты
- Метрики: `mdm_device_online`, `mdm_policy_compliant`, `ota_install_latency`, `mdm_command_failure_total`.
- Интеграция: MDM webhooks → Supabase RPC `log_device_event`, Prometheus scrape (через exporter).
- Алерты:
  - device offline > 30 мин → Slack `#kiosk-ops` (severity warning).
  - ota success rate < 95% за 1 час → incident.
  - unauthorized package detected → auto-lock + PagerDuty (critical).

## 8. Безопасность и соответствие
- Все API-ключи MDM хранятся в Vault, Kiosk App получает только per-device tokens.
- Включена проверка целостности агент/клиент (Play Integrity или custom attestation).
- MDM логи хранятся 1 год, export в DWH через `plan-data-warehouse-integration.md` pipeline.
- Соответствие требованиям: GDPR, 152-ФЗ, PCI (для платежного модуля). MDM должен поддерживать отчёты по аудитам.

## 9. Тестирование
| Уровень         | Сценарии                                               | Инструменты                               |
| --------------- | ------------------------------------------------------ | ----------------------------------------- |
| **Unit**        | Mock SDK для команд `installPackage`, `lockTask`.      | Robolectric + MDM emulator.               |
| **Integration** | Enrollment QA-девайсов, push OTA, проверка compliance. | TestFlight/QA fleet, Supabase dashboards. |
| **Field**       | На пилотных киосках: remote reboot, лог-сбор, wipe.    | Runbook FT-MDM-01…06.                     |

## 10. Внедрение и сроки
1. **Выбор вендора (1 неделя)**: PoC с 2 поставщиками, проверка Device Owner, API, стоимость.
2. **Интеграция SDK (2 недели)**: добавление агентских API, build variants (prod/qa/dev), настройка secrets.
3. **Policy rollout (1 неделя)**: Wi-Fi, сертификаты, kiosk-mode, power schedule.
4. **OTA/CI связывание (1 неделя)**: автоматическое создание MDM кампаний после успешного CI.
5. **Pilot (2 недели)**: 5 устройств, мониторинг метрик, корректировки. Проверить, что все MDM агенты занесены в `mdm.allowedPackages` и команда `COMMAND_DISPATCH` достигает Kiosk App.
6. **General Availability**: включение всех киосков, запуск регулярных отчётов (ежедневно).

## 11. Риски и меры
| Риск                           | Импакт  | Митигация                                                                                           |
| ------------------------------ | ------- | --------------------------------------------------------------------------------------------------- |
| Vendor lock-in / API изменения | Средний | Абстрагировать MDM API интерфейсом, вести экспорт списка команд.                                    |
| Потеря связи во время OTA      | Высокий | Локальный watchdog, возможность перезапуска OTA вручную, сохранение пакета локально.                |
| Несовместимость с Device Owner | Высокий | Предварительное тестирование на эталонном устройстве, fallback через manual provisioning.           |
| Нарушение безопасности ключей  | Высокий | Ключи только в Vault, device tokens с TTL 24h, логирование всех операций.                           |
| Недостаток лицензий            | Средний | План закупок в `plan-hardware-requests.md`, наблюдение за количеством зарегистрированных устройств. |

## 12. Следующие шаги
1. Зафиксировать требования в RFP и отправить потенциальным поставщикам.
2. Создать прототип интеграции MDM SDK в `feature-kiosk-mode` (dev build). 
3. Подготовить Supabase таблицы `devices`, `device_commands`, `device_events` + RPC для webhooks.
4. Обновить `plan-ota-updates.md` ссылками на данный документ.
5. В `plan-incident-response.md` добавить раздел по MDM алертам.
