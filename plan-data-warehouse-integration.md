# План интеграции диагностических данных в корпоративное DWH

Дата обновления: 2025-11-19  
Версия: 1.0  
Связанные документы: `plan-diagnostics-js-bridge.md`, `plan-payments-reports.md`, `plan-telemetry-analytics.md`, `plan-testing.md`

## 1. Цель и область
- Централизовать хранение диагностических отчётов, телеметрии и статусов доставки, чтобы поддерживать операционную аналитику, отчётность перед партнёрами и юридические проверки.
- Автоматизировать перенос данных из Supabase outbox (Android киоски) в корпоративное DWH (BigQuery/ClickHouse — выбор зависит от среды, ниже используется нейтральный термин «DWH»).
- Соблюдать требования PII/PII-light: контакты клиентов и VIN должны оставаться шифрованными на всём пути.

## 2. Источники и события
| Источник           | Схема/таблица                                                                | Частота                                  | Содержание                                                                                                   |
| ------------------ | ---------------------------------------------------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Android-приложение | `DiagnosticsReportSupabaseSchema`, `DiagnosticsReportDeliverySupabaseSchema` | Событийно (сразу после генерации отчёта) | HTML/PDF отчёт, диагностическое дерево, метаданные (vehicle/customer/summary), очереди доставки (email/SMS). |
| Telemetry bridge   | `DiagnosticsTelemetrySupabaseSchema`                                         | каждые 5 мин                             | RSSI, reconnect count, BLE watchdog.                                                                         |
| Payments audit     | `payments_audit`                                                             | по мере событий                          | важно для сквозной аналитики «диагностика → оплата».                                                         |

## 3. Целевая модель данных
Используем звездообразную схему: факт `fact_diagnostics_report` + измерения.

### 3.1 Факт `fact_diagnostics_report`
| Поле                    | Тип         | Описание                                                  |
| ----------------------- | ----------- | --------------------------------------------------------- |
| `report_id`             | STRING (PK) | `DiagnosticsReportSupabaseSchema.Columns.REPORT_ID`.      |
| `session_id`            | STRING      | OBD сессия.                                               |
| `kiosk_id`              | STRING      | точка установки.                                          |
| `environment`           | STRING      | prod/qa/dev.                                              |
| `generated_at`          | TIMESTAMP   | `generatedAtMillis`.                                      |
| `vehicle_hash`          | STRING      | SHA-256 VIN (если доступен) для поиска без раскрытия VIN. |
| `customer_hash`         | STRING      | SHA-256 email/phone.                                      |
| `dtc_count`             | INT64       | из `metadata.diagnostics.dtc_count`.                      |
| `warning_metrics`       | INT64       | `metadata.summary.metrics_warning`.                       |
| `critical_metrics`      | INT64       | `metadata.summary.metrics_critical`.                      |
| `recommendations_total` | INT64       | количество рекомендаций.                                  |
| `report_size_bytes`     | INT64       | длина PDF.                                                |
| `ingested_at`           | TIMESTAMP   | время загрузки в DWH.                                     |

### 3.2 `dim_vehicle`
- `vehicle_key` (hash), `make`, `model`, `year`, `vin_encrypted` (KMS), дата обновления.

### 3.3 `dim_customer`
- `customer_key`, `phone_encrypted`, `email_encrypted`, маркетинговые согласия.

### 3.4 `fact_diagnostic_pid_sample`
| Поле        | Тип       | Описание                        |
| ----------- | --------- | ------------------------------- |
| `report_id` | STRING    | FK → `fact_diagnostics_report`. |
| `pid_key`   | STRING    | SAE key `mode:pid`.             |
| `value`     | FLOAT64   | Последнее измерение.            |
| `unit`      | STRING    | Единица измерения.              |
| `status`    | STRING    | normal/warning/critical.        |
| `timestamp` | TIMESTAMP | `pid.timestampMillis`.          |

### 3.5 `fact_delivery_attempt`
| Поле             | Тип       | Описание                       |
| ---------------- | --------- | ------------------------------ |
| `delivery_id`    | STRING    | stable hash report_id+channel. |
| `report_id`      | STRING    | FK.                            |
| `channel`        | STRING    | email / sms / webhook.         |
| `status`         | STRING    | pending/sent/failed.           |
| `attempted_at`   | TIMESTAMP | момент отправки.               |
| `failure_reason` | STRING    | текст либо код.                |

## 4. Поток данных и ETL
```
Android App → Supabase Outbox → Supabase REST → Landing Storage → ETL/Streaming → DWH → Data Mart/BI
```

### Этапы
1. **Landing (T+0..5 мин)**
   - Supabase outbox uploader складывает JSON в bucket `diagnostics-landing/YYYY/MM/DD/HH/` (gzip, envelope с metadata).
   - Контроль полноты: счётчик файлов = числу записей `supabase_outbox.status.pendingCount` за период.
2. **Staging (T+5..10 мин)**
   - Dataflow/Spark job читает landing, нормализует JSON → Parquet, выносит крупные поля (PDF) в отдельный объект-хранилище с ссылкой.
   - Выполняется в режиме micro-batch каждые 5 минут.
3. **Processing (T+10..15 мин)**
   - Трансформации:
     - расчёт хэшей для VIN/email (SHA-256 + salt).
     - анонимизация PID (оставляем только последние значения + агрегаты).
     - джойн с референсами киосков/локаций.
   - Результат — набор staging-таблиц `stg_fact_diagnostics_report`, `stg_fact_pid_sample`, `stg_fact_delivery`.
4. **Loading в DWH (T+15..20 мин)**
   - Merge/Upsert в основные таблицы.
   - Обновление витрин (PowerBI/Looker): latency SLA ≤ 30 мин.

### Incremental keys
- `report_id` — источник правды. В случае дубликатов используем `Prefer=resolution=merge-duplicates` из Supabase и `ingested_at DESC` в DWH.
- `delivery_id` = SHA256(report_id + channel).

## 5. SLA и мониторинг
| Метрика            | Цель       | Алерт                                                            |
| ------------------ | ---------- | ---------------------------------------------------------------- |
| End-to-end latency | ≤ 30 минут | > 45 минут две итерации подряд.                                  |
| Ошибки загрузки    | 0          | Любая ошибка → PagerDuty + Slack `#diag-data-pipeline`.          |
| Completeness       | 100%       | Несовпадение количества отчётов (Supabase vs DWH) > 1% за 1 час. |
| DWH availability   | 99.5%/мес  | < 99.5% → инцидент.                                              |

Мониторинг реализуется через Prometheus экспортер (landing/staging) + BigQuery Scheduled Queries со сравнением количества отчётов.

## 6. Data Quality checks
1. **Синтаксис JSON** — schema validation (JSON Schema + Supabase contract).
2. **PK Uniqueness** — `report_id` + `delivery_id`.
3. **Foreign Keys** — наличие записи в `dim_kiosk` и `dim_vehicle`.
4. **PII Masking** — поля `phone`, `email`, `vin` никогда не попадают в открытом виде (проверка regex).
5. **Metrics coverage** — `metrics_total >= metrics_ok + metrics_warning + metrics_critical + metrics_no_data`.

## 7. Безопасность и соответствие
- Шифрование: TLS 1.3 при выгрузке из Supabase, GCS bucket с CMEK, DWH таблицы с column-level encryption для PII.
- Доступ: принцип наименьших привилегий, сервисные аккаунты для ETL, отдельные readonly роли для аналитиков.
- Retention: холодное хранилище PDF не более 90 суток, агрегаты — 3 года.
- GDPR/152-ФЗ: обработка согласий логируется в `fact_delivery_attempt` и связана с `ConsentLifecycleManager` (см. `plan-compliance.md`).

## 8. Оркестрация и runbook
- Оркестратор: Airflow (prod) / GitHub Actions (QA). DAG `diag_reports_to_dwh` содержит задачи `landing_sync`, `staging_transform`, `dwh_merge`, `quality_gate`.
- Перезапуск: idempotent — повторное чтение landing не создаёт дубликаты (используются checksum + `report_id`).
- Runbook: при алерте «latency > 45m» оператор проверяет Supabase Outbox backlog (`KioskApp` логи) и статус Dataflow job → при необходимости включает replay из landing за указанный интервал.

## 9. Тестирование
- Unit: schema validation (CI) с фиктивными JSON из `android/app/src/test/resources/diagnostics_report_samples`.
- Integration: nightly прогон против QA Supabase, проверяется загрузка ≥ 10 отчётов.
- Load: ежеквартальный тест с 10k отчётов (эмуляция) для оценки стоимости хранения и производительности ETL.

## 10. Следующие шаги
1. Подготовить Terraform для bucket `diagnostics-landing` и service accounts.
2. Реализовать Airflow DAG + мониторинг Prometheus.
3. Настроить BI-витрины (PowerBI dashboards): SLA, DTC распределение, конверсия «diagnostics → paid».  
4. Согласовать юридические политики хранения с DPO.
