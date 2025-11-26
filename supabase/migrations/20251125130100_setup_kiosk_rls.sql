-- Миграция: RLS политики для таблиц киоска самообслуживания
-- Дата: 25.11.2025
-- Назначение: настройка Row Level Security для бизнес-таблиц

-- ============================================================================
-- Включение RLS для всех таблиц киоска
-- ============================================================================
ALTER TABLE public.diagnostics_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostics_telemetry ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostics_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostics_report_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.thickness_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.thickness_report_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_events ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- diagnostics_logs: полный доступ для сервисной роли, запрет для анонимов
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert diagnostics_logs" ON public.diagnostics_logs;
CREATE POLICY "Service role can insert diagnostics_logs"
    ON public.diagnostics_logs
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read diagnostics_logs" ON public.diagnostics_logs;
CREATE POLICY "Service role can read diagnostics_logs"
    ON public.diagnostics_logs
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- diagnostics_telemetry: полный доступ для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert diagnostics_telemetry" ON public.diagnostics_telemetry;
CREATE POLICY "Service role can insert diagnostics_telemetry"
    ON public.diagnostics_telemetry
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read diagnostics_telemetry" ON public.diagnostics_telemetry;
CREATE POLICY "Service role can read diagnostics_telemetry"
    ON public.diagnostics_telemetry
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- diagnostics_reports: INSERT/SELECT для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert diagnostics_reports" ON public.diagnostics_reports;
CREATE POLICY "Service role can insert diagnostics_reports"
    ON public.diagnostics_reports
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read diagnostics_reports" ON public.diagnostics_reports;
CREATE POLICY "Service role can read diagnostics_reports"
    ON public.diagnostics_reports
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- diagnostics_report_deliveries: INSERT/SELECT/UPDATE для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can manage diagnostics_report_deliveries" ON public.diagnostics_report_deliveries;
CREATE POLICY "Service role can manage diagnostics_report_deliveries"
    ON public.diagnostics_report_deliveries
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- ============================================================================
-- thickness_reports: INSERT/SELECT для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert thickness_reports" ON public.thickness_reports;
CREATE POLICY "Service role can insert thickness_reports"
    ON public.thickness_reports
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read thickness_reports" ON public.thickness_reports;
CREATE POLICY "Service role can read thickness_reports"
    ON public.thickness_reports
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- thickness_report_deliveries: INSERT/SELECT/UPDATE для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can manage thickness_report_deliveries" ON public.thickness_report_deliveries;
CREATE POLICY "Service role can manage thickness_report_deliveries"
    ON public.thickness_report_deliveries
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- ============================================================================
-- payments_audit: INSERT/SELECT для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert payments_audit" ON public.payments_audit;
CREATE POLICY "Service role can insert payments_audit"
    ON public.payments_audit
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read payments_audit" ON public.payments_audit;
CREATE POLICY "Service role can read payments_audit"
    ON public.payments_audit
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- device_status: INSERT/SELECT для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert device_status" ON public.device_status;
CREATE POLICY "Service role can insert device_status"
    ON public.device_status
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read device_status" ON public.device_status;
CREATE POLICY "Service role can read device_status"
    ON public.device_status
    FOR SELECT
    TO service_role
    USING (true);

-- ============================================================================
-- device_commands: INSERT/SELECT/UPDATE для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can manage device_commands" ON public.device_commands;
CREATE POLICY "Service role can manage device_commands"
    ON public.device_commands
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- ============================================================================
-- device_events: INSERT/SELECT для сервисной роли
-- ============================================================================
DROP POLICY IF EXISTS "Service role can insert device_events" ON public.device_events;
CREATE POLICY "Service role can insert device_events"
    ON public.device_events
    FOR INSERT
    TO service_role
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can read device_events" ON public.device_events;
CREATE POLICY "Service role can read device_events"
    ON public.device_events
    FOR SELECT
    TO service_role
    USING (true);
