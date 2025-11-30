-- Миграция: server-upgrade stage 1 — очереди отчётов и метрики агента
-- Дата: 28.11.2025
-- Контекст: подготовка ingestion-очереди для отчётов и агрегатор метрик киоска

-- ============================================================================
-- 1. report_ingest_queue — входящая очередь отчётов (diagnostics/thickness)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.report_ingest_queue (
    ingest_id TEXT PRIMARY KEY,
    report_type TEXT NOT NULL CHECK (report_type IN ('diagnostics', 'thickness')),
    session_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('received', 'queued', 'processing', 'completed', 'failed', 'cancelled')),
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    contact_email TEXT,
    contact_phone TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    retries INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    received_at_ms BIGINT NOT NULL,
    sla_deadline_ms BIGINT NOT NULL,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    trace_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_ingest_status
    ON public.report_ingest_queue(status)
    WHERE status IN ('received', 'queued', 'processing');

CREATE INDEX IF NOT EXISTS idx_report_ingest_sla
    ON public.report_ingest_queue(sla_deadline_ms);

CREATE INDEX IF NOT EXISTS idx_report_ingest_env
    ON public.report_ingest_queue(environment, kiosk_id);

COMMENT ON TABLE public.report_ingest_queue IS 'Входящая очередь отчётов для последующей генерации и доставки';
COMMENT ON COLUMN public.report_ingest_queue.payload IS 'JSON-пакет с HTML/PDF и метаданными отчёта';
COMMENT ON COLUMN public.report_ingest_queue.sla_deadline_ms IS 'Дедлайн SLA доставки отчёта (UNIX millis)';

-- Триггер auto-updated_at для report_ingest_queue
DROP TRIGGER IF EXISTS update_report_ingest_queue_updated_at ON public.report_ingest_queue;
CREATE TRIGGER update_report_ingest_queue_updated_at
    BEFORE UPDATE ON public.report_ingest_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 2. agent_metrics — агрегатор метрик агента/киоска
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.agent_metrics (
    metric_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_type TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    value_number DOUBLE PRECISION,
    value_text TEXT,
    payload JSONB,
    source TEXT,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_metrics_recorded_at
    ON public.agent_metrics(recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_metrics_type
    ON public.agent_metrics(metric_type);

CREATE INDEX IF NOT EXISTS idx_agent_metrics_env
    ON public.agent_metrics(environment, kiosk_id);

COMMENT ON TABLE public.agent_metrics IS 'Метрики агента: intake, latency, watchdog, локальные health-checks';
COMMENT ON COLUMN public.agent_metrics.payload IS 'Дополнительные поля метрики в JSON';
