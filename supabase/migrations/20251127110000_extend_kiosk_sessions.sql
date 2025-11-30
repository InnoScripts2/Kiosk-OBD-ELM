-- Миграция: расширение модели данных киоска (сессии, измерения, события)
-- Дата: 27.11.2025
-- Назначение: нормализовать хранение сессий, измерений, событий замков и платежей
--             для унифицированного обмена между Android-приложением и Supabase.

-- =========================================================================
-- 1. Таблица kiosk_sessions — мастер-запись любой услуги
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.kiosk_sessions (
    session_id TEXT PRIMARY KEY,
    kiosk_id TEXT NOT NULL,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    service_type TEXT NOT NULL CHECK (service_type IN ('thickness', 'diagnostics')),
    service_variant TEXT,
    status TEXT NOT NULL CHECK (
        status IN (
            'created', 'in_progress', 'awaiting_payment', 'paid',
            'measuring', 'scanning', 'reporting', 'completed',
            'failed', 'cancelled', 'expired'
        )
    ),
    stage TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    vehicle_brand TEXT,
    vehicle_model TEXT,
    vehicle_vin TEXT,
    vehicle_year SMALLINT,
    contact_phone TEXT,
    contact_email TEXT,
    payment_intent_id TEXT,
    payment_status TEXT CHECK (
        payment_status IS NULL OR payment_status IN ('none', 'pending', 'confirmed', 'failed', 'refunded')
    ),
    total_amount_minor INTEGER,
    currency TEXT NOT NULL DEFAULT 'RUB',
    lock_status TEXT,
    report_status TEXT,
    source_app_version TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kiosk_sessions_kiosk_env
    ON public.kiosk_sessions(kiosk_id, environment, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_kiosk_sessions_payment_intent
    ON public.kiosk_sessions(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_kiosk_sessions_status
    ON public.kiosk_sessions(status);

COMMENT ON TABLE public.kiosk_sessions IS 'Мастер-таблица сессий услуг киоска (толщиномер/диагностика)';
COMMENT ON COLUMN public.kiosk_sessions.metadata IS 'Расширенные атрибуты (JSONB) для заявок';

-- =========================================================================
-- 2. Таблица kiosk_session_events — таймлайн статусов и событий
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.kiosk_session_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT NOT NULL REFERENCES public.kiosk_sessions(session_id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (
        length(event_type) BETWEEN 1 AND 80
    ),
    event_status TEXT,
    device_timestamp_ms BIGINT,
    message TEXT,
    payload JSONB DEFAULT '{}'::JSONB,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kiosk_session_events_session
    ON public.kiosk_session_events(session_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_kiosk_session_events_type
    ON public.kiosk_session_events(event_type);

COMMENT ON TABLE public.kiosk_session_events IS 'Хронология статусов, ошибок и пользовательских действий в рамках сессии';

-- =========================================================================
-- 3. Таблицы диагностики: obd_scan_sessions + obd_dtc_records
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.obd_scan_sessions (
    obd_session_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES public.kiosk_sessions(session_id) ON DELETE CASCADE,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    adapter_serial TEXT,
    protocol TEXT,
    status TEXT NOT NULL CHECK (
        status IN ('planned', 'connecting', 'scanning', 'analyzing', 'completed', 'failed')
    ),
    scan_started_at TIMESTAMPTZ,
    scan_completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    ecu_count INTEGER,
    dtc_count INTEGER,
    mil_active BOOLEAN,
    battery_voltage NUMERIC(6,2),
    summary JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_obd_scan_sessions_session
    ON public.obd_scan_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_obd_scan_sessions_status
    ON public.obd_scan_sessions(status);

CREATE TABLE IF NOT EXISTS public.obd_dtc_records (
    record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obd_session_id TEXT NOT NULL REFERENCES public.obd_scan_sessions(obd_session_id) ON DELETE CASCADE,
    session_id TEXT NOT NULL,
    code TEXT NOT NULL,
    category TEXT,
    severity TEXT,
    status TEXT,
    description TEXT,
    is_confirmed BOOLEAN,
    is_pending BOOLEAN,
    cleared_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_obd_dtc_records_session
    ON public.obd_dtc_records(session_id);
CREATE INDEX IF NOT EXISTS idx_obd_dtc_records_code
    ON public.obd_dtc_records(code);

COMMENT ON TABLE public.obd_scan_sessions IS 'Прохождение OBD сканирования (адаптер, статус, длительность)';
COMMENT ON TABLE public.obd_dtc_records IS 'DTC коды и их статусы по каждой диагностической сессии';

-- =========================================================================
-- 4. Таблица thickness_measurements — сетка 40–60 точек
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.thickness_measurements (
    measurement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT NOT NULL REFERENCES public.kiosk_sessions(session_id) ON DELETE CASCADE,
    zone_code TEXT NOT NULL,
    zone_label TEXT,
    zone_index SMALLINT NOT NULL,
    value_microns NUMERIC(8,2),
    classification TEXT CHECK (
        classification IS NULL OR classification IN ('good', 'warning', 'critical')
    ),
    status TEXT NOT NULL CHECK (status IN ('pending', 'measuring', 'completed', 'error')),
    device_temperature_c NUMERIC(5,2),
    measurement_latency_ms INTEGER,
    measured_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_thickness_measurements_session_zone
    ON public.thickness_measurements(session_id, zone_code);
CREATE INDEX IF NOT EXISTS idx_thickness_measurements_classification
    ON public.thickness_measurements(classification);

COMMENT ON TABLE public.thickness_measurements IS 'Значения толщиномера по фиксированной сетке кузова';

-- =========================================================================
-- 5. Таблица lock_events — аудит выдачи устройств
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.lock_events (
    lock_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    device_type TEXT NOT NULL CHECK (device_type IN ('thickness', 'adapter')),
    action TEXT NOT NULL CHECK (action IN ('open', 'close', 'status', 'heartbeat')),
    result TEXT NOT NULL CHECK (result IN ('success', 'failed', 'timeout')),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_code TEXT,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lock_events_session
    ON public.lock_events(session_id);
CREATE INDEX IF NOT EXISTS idx_lock_events_device
    ON public.lock_events(device_type, requested_at DESC);

COMMENT ON TABLE public.lock_events IS 'История обращений к механизму выдачи устройств (замки/реле)';

-- =========================================================================
-- 6. Таблица kiosk_payment_intents — статус оплаты услуги
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.kiosk_payment_intents (
    intent_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES public.kiosk_sessions(session_id) ON DELETE CASCADE,
    kiosk_id TEXT,
    environment TEXT NOT NULL CHECK (environment IN ('dev', 'qa', 'prod')),
    gateway TEXT NOT NULL,
    status TEXT NOT NULL CHECK (
        status IN ('created', 'pending', 'succeeded', 'failed', 'refunded')
    ),
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'RUB',
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    failure_reason TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kiosk_payment_intents_session
    ON public.kiosk_payment_intents(session_id);
CREATE INDEX IF NOT EXISTS idx_kiosk_payment_intents_status
    ON public.kiosk_payment_intents(status);

COMMENT ON TABLE public.kiosk_payment_intents IS 'Статусы оплат по каждой сессии киоска (агрегация поверх payments_audit)';

-- =========================================================================
-- 7. Триггеры updated_at для изменяемых таблиц
-- =========================================================================
DROP TRIGGER IF EXISTS trg_kiosk_sessions_updated_at ON public.kiosk_sessions;
CREATE TRIGGER trg_kiosk_sessions_updated_at
    BEFORE UPDATE ON public.kiosk_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_obd_scan_sessions_updated_at ON public.obd_scan_sessions;
CREATE TRIGGER trg_obd_scan_sessions_updated_at
    BEFORE UPDATE ON public.obd_scan_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_kiosk_payment_intents_updated_at ON public.kiosk_payment_intents;
CREATE TRIGGER trg_kiosk_payment_intents_updated_at
    BEFORE UPDATE ON public.kiosk_payment_intents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =========================================================================
-- 8. Row Level Security и политики (service_role)
-- =========================================================================
ALTER TABLE public.kiosk_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.kiosk_session_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.obd_scan_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.obd_dtc_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.thickness_measurements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lock_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.kiosk_payment_intents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Service role can manage kiosk_sessions" ON public.kiosk_sessions;
CREATE POLICY "Service role can manage kiosk_sessions"
    ON public.kiosk_sessions
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage kiosk_session_events" ON public.kiosk_session_events;
CREATE POLICY "Service role can manage kiosk_session_events"
    ON public.kiosk_session_events
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage obd_scan_sessions" ON public.obd_scan_sessions;
CREATE POLICY "Service role can manage obd_scan_sessions"
    ON public.obd_scan_sessions
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage obd_dtc_records" ON public.obd_dtc_records;
CREATE POLICY "Service role can manage obd_dtc_records"
    ON public.obd_dtc_records
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage thickness_measurements" ON public.thickness_measurements;
CREATE POLICY "Service role can manage thickness_measurements"
    ON public.thickness_measurements
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage lock_events" ON public.lock_events;
CREATE POLICY "Service role can manage lock_events"
    ON public.lock_events
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service role can manage kiosk_payment_intents" ON public.kiosk_payment_intents;
CREATE POLICY "Service role can manage kiosk_payment_intents"
    ON public.kiosk_payment_intents
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- =========================================================================
-- 9. Вспомогательные функции для сессий
-- =========================================================================
CREATE OR REPLACE FUNCTION public.upsert_kiosk_session_state(
    p_session_id TEXT,
    p_kiosk_id TEXT,
    p_environment TEXT,
    p_service_type TEXT,
    p_status TEXT,
    p_stage TEXT DEFAULT NULL,
    p_metadata JSONB DEFAULT '{}'::JSONB
) RETURNS public.kiosk_sessions AS $$
DECLARE
    v_session public.kiosk_sessions;
BEGIN
    INSERT INTO public.kiosk_sessions AS ks (
        session_id, kiosk_id, environment, service_type, status, stage, metadata
    ) VALUES (
        p_session_id, p_kiosk_id, p_environment, p_service_type, p_status, p_stage, coalesce(p_metadata, '{}'::JSONB)
    )
    ON CONFLICT (session_id) DO UPDATE
        SET status = EXCLUDED.status,
            stage = COALESCE(EXCLUDED.stage, ks.stage),
            metadata = ks.metadata || COALESCE(EXCLUDED.metadata, '{}'::JSONB),
            updated_at = NOW()
    RETURNING * INTO v_session;

    RETURN v_session;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.upsert_kiosk_session_state IS 'Создать/обновить сессию киоска и вернуть текущее состояние';

CREATE OR REPLACE FUNCTION public.append_kiosk_session_event(
    p_session_id TEXT,
    p_event_type TEXT,
    p_event_status TEXT DEFAULT NULL,
    p_message TEXT DEFAULT NULL,
    p_device_timestamp_ms BIGINT DEFAULT NULL,
    p_payload JSONB DEFAULT '{}'::JSONB,
    p_kiosk_id TEXT DEFAULT NULL,
    p_environment TEXT DEFAULT 'dev'
) RETURNS public.kiosk_session_events AS $$
DECLARE
    v_event public.kiosk_session_events;
BEGIN
    INSERT INTO public.kiosk_session_events (
        session_id, event_type, event_status, message, device_timestamp_ms,
        payload, kiosk_id, environment
    ) VALUES (
        p_session_id, p_event_type, p_event_status, p_message,
        p_device_timestamp_ms, COALESCE(p_payload, '{}'::JSONB),
        p_kiosk_id, p_environment
    ) RETURNING * INTO v_event;

    RETURN v_event;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.append_kiosk_session_event IS 'Записать событие таймлайна по сессии и вернуть созданную запись';

CREATE OR REPLACE FUNCTION public.get_kiosk_session_overview(
    p_session_id TEXT
) RETURNS TABLE (
    session_id TEXT,
    status TEXT,
    service_type TEXT,
    payment_status TEXT,
    report_status TEXT,
    dtc_count INTEGER,
    measurement_completed INTEGER,
    measurement_total INTEGER,
    lock_failures INTEGER,
    last_event TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ks.session_id,
        ks.status,
        ks.service_type,
        ks.payment_status,
        ks.report_status,
        COALESCE(os.dtc_total, 0)::INTEGER,
        COALESCE(tm.completed_points, 0)::INTEGER,
        COALESCE(tm.total_points, 0)::INTEGER,
        COALESCE(le.failed_events, 0)::INTEGER,
        evt.last_event_at
    FROM public.kiosk_sessions ks
    LEFT JOIN (
        SELECT session_id, SUM(dtc_count) AS dtc_total
        FROM public.obd_scan_sessions
        WHERE session_id = p_session_id
        GROUP BY session_id
    ) os ON os.session_id = ks.session_id
    LEFT JOIN (
        SELECT 
            session_id,
            COUNT(*) FILTER (WHERE status = 'completed') AS completed_points,
            COUNT(*) AS total_points
        FROM public.thickness_measurements
        WHERE session_id = p_session_id
        GROUP BY session_id
    ) tm ON tm.session_id = ks.session_id
    LEFT JOIN (
        SELECT session_id, COUNT(*) FILTER (WHERE result = 'failed') AS failed_events
        FROM public.lock_events
        WHERE session_id = p_session_id
        GROUP BY session_id
    ) le ON le.session_id = ks.session_id
    LEFT JOIN (
        SELECT session_id, MAX(occurred_at) AS last_event_at
        FROM public.kiosk_session_events
        WHERE session_id = p_session_id
        GROUP BY session_id
    ) evt ON evt.session_id = ks.session_id
    WHERE ks.session_id = p_session_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.get_kiosk_session_overview IS 'Агрегированное состояние сессии (диагностика + толщиномер + замки)';

CREATE OR REPLACE FUNCTION public.purge_expired_kiosk_sessions(
    p_limit INTEGER DEFAULT 100
) RETURNS TABLE (
    session_id TEXT,
    expired_at TIMESTAMPTZ
) AS $$
DECLARE
    v_limit INTEGER := GREATEST(COALESCE(p_limit, 100), 1);
BEGIN
    RETURN QUERY
    WITH expired_targets AS (
        SELECT session_id
        FROM public.kiosk_sessions
        WHERE status IN ('created', 'awaiting_payment', 'in_progress')
          AND expires_at IS NOT NULL
          AND expires_at < NOW()
        ORDER BY expires_at ASC
        LIMIT v_limit
        FOR UPDATE SKIP LOCKED
    )
    UPDATE public.kiosk_sessions AS ks
       SET status = 'expired',
           ended_at = NOW(),
           updated_at = NOW()
      FROM expired_targets et
     WHERE ks.session_id = et.session_id
     RETURNING ks.session_id, NOW();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.purge_expired_kiosk_sessions IS 'Переводит просроченные сессии в статус expired и возвращает список обновлённых записей';
