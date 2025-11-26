-- Миграция: edge-функция для очистки старых данных (retention coordinator)
-- Дата: 25.11.2025
-- Назначение: автоматическая очистка логов, телеметрии и отчётов старше retention window

CREATE OR REPLACE FUNCTION public.cleanup_old_kiosk_data(
    retention_days INTEGER DEFAULT 30
)
RETURNS TABLE (
    table_name TEXT,
    deleted_count BIGINT
) AS $$
DECLARE
    threshold_timestamp TIMESTAMPTZ;
    deleted_logs BIGINT;
    deleted_telemetry BIGINT;
    deleted_reports BIGINT;
    deleted_deliveries BIGINT;
    deleted_device_status BIGINT;
    deleted_device_events BIGINT;
BEGIN
    -- Вычисляем пороговую дату
    threshold_timestamp := NOW() - (retention_days || ' days')::INTERVAL;
    
    -- Очистка diagnostics_logs
    DELETE FROM public.diagnostics_logs 
    WHERE created_at < threshold_timestamp;
    GET DIAGNOSTICS deleted_logs = ROW_COUNT;
    
    -- Очистка diagnostics_telemetry
    DELETE FROM public.diagnostics_telemetry 
    WHERE created_at < threshold_timestamp;
    GET DIAGNOSTICS deleted_telemetry = ROW_COUNT;
    
    -- Очистка diagnostics_reports (каскадно удалит deliveries)
    DELETE FROM public.diagnostics_reports 
    WHERE created_at < threshold_timestamp;
    GET DIAGNOSTICS deleted_reports = ROW_COUNT;
    
    -- Очистка thickness_reports (каскадно удалит deliveries)
    DELETE FROM public.thickness_reports 
    WHERE created_at < threshold_timestamp;
    
    -- Очистка diagnostics_report_deliveries со статусом sent/failed старше порога
    DELETE FROM public.diagnostics_report_deliveries 
    WHERE created_at < threshold_timestamp 
      AND status IN ('sent', 'failed');
    GET DIAGNOSTICS deleted_deliveries = ROW_COUNT;
    
    -- Очистка thickness_report_deliveries со статусом sent/failed старше порога
    DELETE FROM public.thickness_report_deliveries 
    WHERE created_at < threshold_timestamp 
      AND status IN ('sent', 'failed');
    
    -- Очистка device_status (heartbeat snapshots)
    DELETE FROM public.device_status 
    WHERE created_at < threshold_timestamp;
    GET DIAGNOSTICS deleted_device_status = ROW_COUNT;
    
    -- Очистка device_events (severity info)
    DELETE FROM public.device_events 
    WHERE created_at < threshold_timestamp 
      AND severity = 'info';
    GET DIAGNOSTICS deleted_device_events = ROW_COUNT;
    
    -- Возвращаем статистику
    RETURN QUERY VALUES 
        ('diagnostics_logs', deleted_logs),
        ('diagnostics_telemetry', deleted_telemetry),
        ('diagnostics_reports', deleted_reports),
        ('report_deliveries', deleted_deliveries),
        ('device_status', deleted_device_status),
        ('device_events', deleted_device_events);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.cleanup_old_kiosk_data IS 
'Автоматическая очистка старых логов/телеметрии/отчётов. По умолчанию 30 дней.';

-- ============================================================================
-- Функция для получения статистики очереди доставки
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_delivery_queue_stats()
RETURNS TABLE (
    queue_name TEXT,
    queued_count BIGINT,
    processing_count BIGINT,
    failed_count BIGINT,
    oldest_queued TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        'diagnostics_deliveries'::TEXT,
        COUNT(*) FILTER (WHERE status = 'queued'),
        COUNT(*) FILTER (WHERE status = 'processing'),
        COUNT(*) FILTER (WHERE status = 'failed'),
        MIN(created_at) FILTER (WHERE status = 'queued')
    FROM public.diagnostics_report_deliveries
    
    UNION ALL
    
    SELECT 
        'thickness_deliveries'::TEXT,
        COUNT(*) FILTER (WHERE status = 'queued'),
        COUNT(*) FILTER (WHERE status = 'processing'),
        COUNT(*) FILTER (WHERE status = 'failed'),
        MIN(created_at) FILTER (WHERE status = 'queued')
    FROM public.thickness_report_deliveries;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.get_delivery_queue_stats IS 
'Получение статистики очередей доставки отчётов (queued/processing/failed)';

-- ============================================================================
-- Функция для получения статистики MDM команд
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_device_commands_stats()
RETURNS TABLE (
    status TEXT,
    count BIGINT,
    avg_latency_ms NUMERIC,
    oldest_command TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        device_commands.status,
        COUNT(*)::BIGINT,
        AVG(latency_ms),
        MIN(requested_at)
    FROM public.device_commands
    GROUP BY device_commands.status
    ORDER BY 
        CASE device_commands.status
            WHEN 'received' THEN 1
            WHEN 'acknowledged' THEN 2
            WHEN 'in_progress' THEN 3
            WHEN 'succeeded' THEN 4
            WHEN 'failed' THEN 5
            WHEN 'cancelled' THEN 6
        END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.get_device_commands_stats IS 
'Статистика MDM команд: количество по статусам, средняя latency, старейшая команда';
