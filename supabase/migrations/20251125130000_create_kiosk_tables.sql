-- Миграция: создание таблиц киоска самообслуживания
-- Дата: 25.11.2025
-- Назначение: интеграция Android-приложения с Supabase для диагностических данных,
--             отчётов, телеметрии, платежей и MDM

-- ============================================================================
-- 1. diagnostics_logs — журнал диагностических событий
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.diagnostics_logs (
    entry_id TEXT PRIMARY KEY,
    device_timestamp_ms BIGINT NOT NULL,
    category TEXT NOT NULL CHECK (length(category) <= 50),
    message TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diagnostics_logs_device_timestamp 
    ON public.diagnostics_logs(device_timestamp_ms DESC);
CREATE INDEX IF NOT EXISTS idx_diagnostics_logs_category 
    ON public.diagnostics_logs(category);
CREATE INDEX IF NOT EXISTS idx_diagnostics_logs_kiosk_env 
    ON public.diagnostics_logs(kiosk_id, environment);

COMMENT ON TABLE public.diagnostics_logs IS 'Журнал диагностических событий с киосков';
COMMENT ON COLUMN public.diagnostics_logs.entry_id IS 'SHA-256 стабильный идентификатор';
COMMENT ON COLUMN public.diagnostics_logs.device_timestamp_ms IS 'Время события на устройстве (UNIX millis)';

-- ============================================================================
-- 2. diagnostics_telemetry — телеметрия BLE/OBD-сессий
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.diagnostics_telemetry (
    event_id TEXT PRIMARY KEY,
    device_timestamp_ms BIGINT NOT NULL,
    event_type TEXT NOT NULL CHECK (length(event_type) <= 50),
    session_id TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diagnostics_telemetry_device_timestamp 
    ON public.diagnostics_telemetry(device_timestamp_ms DESC);
CREATE INDEX IF NOT EXISTS idx_diagnostics_telemetry_session_id 
    ON public.diagnostics_telemetry(session_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_telemetry_event_type 
    ON public.diagnostics_telemetry(event_type);

COMMENT ON TABLE public.diagnostics_telemetry IS 'Телеметрия диагностических сессий (RSSI, reconnects, watchdog)';

-- ============================================================================
-- 3. diagnostics_reports — сгенерированные HTML/PDF отчёты диагностики
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.diagnostics_reports (
    report_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    generated_at_ms BIGINT NOT NULL,
    report_html TEXT NOT NULL,
    report_pdf_base64 TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diagnostics_reports_session_id 
    ON public.diagnostics_reports(session_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_reports_generated_at 
    ON public.diagnostics_reports(generated_at_ms DESC);
CREATE INDEX IF NOT EXISTS idx_diagnostics_reports_kiosk_env 
    ON public.diagnostics_reports(kiosk_id, environment);

COMMENT ON TABLE public.diagnostics_reports IS 'Диагностические отчёты (HTML/PDF) для клиентов';
COMMENT ON COLUMN public.diagnostics_reports.report_pdf_base64 IS 'PDF в base64 для прямого скачивания';

-- ============================================================================
-- 4. diagnostics_report_deliveries — очередь доставки диагностических отчётов
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.diagnostics_report_deliveries (
    delivery_id TEXT PRIMARY KEY,
    report_id TEXT NOT NULL REFERENCES public.diagnostics_reports(report_id) ON DELETE CASCADE,
    session_id TEXT NOT NULL,
    generated_at_ms BIGINT NOT NULL,
    channel TEXT NOT NULL CHECK (channel IN ('email', 'sms')),
    recipient TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('queued', 'processing', 'sent', 'failed')),
    attempts INTEGER DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diag_report_deliveries_report_id 
    ON public.diagnostics_report_deliveries(report_id);
CREATE INDEX IF NOT EXISTS idx_diag_report_deliveries_status 
    ON public.diagnostics_report_deliveries(status) WHERE status IN ('queued', 'processing');
CREATE INDEX IF NOT EXISTS idx_diag_report_deliveries_channel 
    ON public.diagnostics_report_deliveries(channel, recipient);

COMMENT ON TABLE public.diagnostics_report_deliveries IS 'Очередь доставки диагностических отчётов (email/SMS)';

-- ============================================================================
-- 5. thickness_reports — отчёты толщиномера ЛКП
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.thickness_reports (
    report_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    generated_at_ms BIGINT NOT NULL,
    report_html TEXT NOT NULL,
    report_pdf_base64 TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_thickness_reports_session_id 
    ON public.thickness_reports(session_id);
CREATE INDEX IF NOT EXISTS idx_thickness_reports_generated_at 
    ON public.thickness_reports(generated_at_ms DESC);
CREATE INDEX IF NOT EXISTS idx_thickness_reports_kiosk_env 
    ON public.thickness_reports(kiosk_id, environment);

COMMENT ON TABLE public.thickness_reports IS 'Отчёты толщинометрии ЛКП (40–60 точек замеров)';

-- ============================================================================
-- 6. thickness_report_deliveries — очередь доставки отчётов ЛКП
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.thickness_report_deliveries (
    delivery_id TEXT PRIMARY KEY,
    report_id TEXT NOT NULL REFERENCES public.thickness_reports(report_id) ON DELETE CASCADE,
    session_id TEXT NOT NULL,
    generated_at_ms BIGINT NOT NULL,
    channel TEXT NOT NULL CHECK (channel IN ('email', 'sms')),
    recipient TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('queued', 'processing', 'sent', 'failed')),
    attempts INTEGER DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_thk_report_deliveries_report_id 
    ON public.thickness_report_deliveries(report_id);
CREATE INDEX IF NOT EXISTS idx_thk_report_deliveries_status 
    ON public.thickness_report_deliveries(status) WHERE status IN ('queued', 'processing');
CREATE INDEX IF NOT EXISTS idx_thk_report_deliveries_channel 
    ON public.thickness_report_deliveries(channel, recipient);

COMMENT ON TABLE public.thickness_report_deliveries IS 'Очередь доставки отчётов толщиномера (email/SMS)';

-- ============================================================================
-- 7. payments_audit — аудит платёжных транзакций
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.payments_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    intent_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    service_type TEXT NOT NULL,
    status TEXT,
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'RUB',
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    gateway TEXT NOT NULL,
    operator_id TEXT,
    request_id TEXT,
    details JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_audit_intent_id 
    ON public.payments_audit(intent_id);
CREATE INDEX IF NOT EXISTS idx_payments_audit_session_id 
    ON public.payments_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_payments_audit_recorded_at 
    ON public.payments_audit(recorded_at DESC);

COMMENT ON TABLE public.payments_audit IS 'Аудит платёжных транзакций (создание, webhook, подтверждение)';
COMMENT ON COLUMN public.payments_audit.amount_minor IS 'Сумма в копейках (minor units)';

-- ============================================================================
-- 8. device_status — heartbeat статусы киосков
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.device_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recorded_at TIMESTAMPTZ NOT NULL,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    mdm_device_id TEXT,
    serial_number TEXT,
    hardware_model TEXT,
    hardware_manufacturer TEXT,
    os_version TEXT,
    os_api_level INTEGER,
    app_version TEXT,
    app_build INTEGER,
    battery_percent INTEGER CHECK (battery_percent BETWEEN 0 AND 100),
    battery_is_charging BOOLEAN,
    network_type TEXT,
    vpn_active BOOLEAN,
    compliance_state TEXT,
    policy_version TEXT,
    uptime_seconds BIGINT,
    status TEXT,
    last_command_id TEXT,
    last_command_status TEXT,
    annotations JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_device_status_kiosk_id 
    ON public.device_status(kiosk_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_status_mdm_device_id 
    ON public.device_status(mdm_device_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_status_recorded_at 
    ON public.device_status(recorded_at DESC);

COMMENT ON TABLE public.device_status IS 'Heartbeat состояния киосков (каждые 5 минут)';

-- ============================================================================
-- 9. device_commands — очередь MDM команд для киосков
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.device_commands (
    command_id TEXT PRIMARY KEY,
    command_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('received', 'acknowledged', 'in_progress', 'succeeded', 'failed', 'cancelled')),
    requested_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    latency_ms BIGINT,
    attempt INTEGER DEFAULT 1,
    payload JSONB DEFAULT '{}'::JSONB,
    result_payload JSONB DEFAULT '{}'::JSONB,
    error_message TEXT,
    source TEXT,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    mdm_device_id TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_device_commands_kiosk_id 
    ON public.device_commands(kiosk_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_commands_status 
    ON public.device_commands(status) WHERE status IN ('received', 'acknowledged', 'in_progress');

COMMENT ON TABLE public.device_commands IS 'Очередь MDM команд (reboot, update, config)';

-- ============================================================================
-- 10. device_events — исторический журнал MDM событий
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.device_events (
    event_id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('info', 'warning', 'error')),
    message TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    mdm_device_id TEXT,
    command_id TEXT,
    command_status TEXT,
    command_type TEXT,
    source TEXT,
    payload JSONB DEFAULT '{}'::JSONB,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_device_events_kiosk_id 
    ON public.device_events(kiosk_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_events_command_id 
    ON public.device_events(command_id);
CREATE INDEX IF NOT EXISTS idx_device_events_severity 
    ON public.device_events(severity) WHERE severity IN ('warning', 'error');

COMMENT ON TABLE public.device_events IS 'Исторический журнал MDM событий и ошибок';

-- ============================================================================
-- Триггеры для auto-updated_at
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_diagnostics_report_deliveries_updated_at ON public.diagnostics_report_deliveries;
CREATE TRIGGER update_diagnostics_report_deliveries_updated_at
    BEFORE UPDATE ON public.diagnostics_report_deliveries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_thickness_report_deliveries_updated_at ON public.thickness_report_deliveries;
CREATE TRIGGER update_thickness_report_deliveries_updated_at
    BEFORE UPDATE ON public.thickness_report_deliveries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_device_commands_updated_at ON public.device_commands;
CREATE TRIGGER update_device_commands_updated_at
    BEFORE UPDATE ON public.device_commands
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
