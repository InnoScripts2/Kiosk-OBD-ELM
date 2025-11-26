// @ts-check
import { ensureSupabaseClient, getSupabaseClient } from '@core/supabase.js';

/**
 * @typedef {'diagnostics' | 'thickness'} ReportKind
 * @typedef {import('@/integrations/supabase/types.generated.ts').Database} Database
 * @typedef {import('@supabase/supabase-js').SupabaseClient<Database>} SupabaseDbClient
 * @typedef {Pick<Database['public']['Tables']['diagnostics_reports']['Row'], 'report_id' | 'session_id' | 'generated_at_ms' | 'kiosk_id' | 'environment' | 'metadata' | 'created_at'>} DiagnosticsReportSummaryRow
 * @typedef {Pick<Database['public']['Tables']['thickness_reports']['Row'], 'report_id' | 'session_id' | 'generated_at_ms' | 'kiosk_id' | 'environment' | 'metadata' | 'created_at'>} ThicknessReportSummaryRow
 * @typedef {Pick<Database['public']['Tables']['diagnostics_report_deliveries']['Row'], 'delivery_id' | 'session_id' | 'channel' | 'status' | 'attempts' | 'updated_at' | 'created_at' | 'recipient'>} DiagnosticsDeliverySummaryRow
 * @typedef {Pick<Database['public']['Tables']['thickness_report_deliveries']['Row'], 'delivery_id' | 'session_id' | 'channel' | 'status' | 'attempts' | 'updated_at' | 'created_at' | 'recipient'>} ThicknessDeliverySummaryRow
 * @typedef {(DiagnosticsDeliverySummaryRow | ThicknessDeliverySummaryRow)} DeliveryRow
 * @typedef {(DiagnosticsReportSummaryRow | ThicknessReportSummaryRow)} ReportRow
 */

/**
 * @typedef {'diagnostics_reports' | 'thickness_reports'} ReportTableName
 * @typedef {'diagnostics_report_deliveries' | 'thickness_report_deliveries'} DeliveryTableName
 */

/** @type {Record<ReportKind, ReportTableName>} */
const REPORT_TABLES = {
    diagnostics: 'diagnostics_reports',
    thickness: 'thickness_reports',
};

/** @type {Record<ReportKind, DeliveryTableName>} */
const DELIVERY_TABLES = {
    diagnostics: 'diagnostics_report_deliveries',
    thickness: 'thickness_report_deliveries',
};

/** @type {ReadonlyArray<'queued' | 'processing' | 'sent' | 'failed'>} */
const DELIVERY_STATUSES = ['queued', 'processing', 'sent', 'failed'];

/**
 * @param {DeliveryRow[]} deliveries
 */
function buildDeliveryStats(deliveries) {
    /** @type {Record<string, number>} */
    const counts = {};
    DELIVERY_STATUSES.forEach(status => {
        counts[status] = 0;
    });

    deliveries.forEach(row => {
        if (typeof row.status === 'string') {
            counts[row.status] = (counts[row.status] || 0) + 1;
        }
    });

    return {
        total: deliveries.length,
        byStatus: counts,
        lastEventAt: deliveries[0]?.updated_at ?? deliveries[0]?.created_at ?? null,
    };
}

/**
 * @param {ReportKind} kind
 * @param {string} sessionId
 */
/**
 * @param {ReportKind} kind
 * @param {string} sessionId
 * @returns {Promise<ReportRow | null>}
 */
async function fetchReportRow(kind, sessionId) {
    const client = await ensureSupabaseClient();
    const reportTable = REPORT_TABLES[kind];
    const { data, error } = await client
        .from(reportTable)
        .select('report_id, session_id, generated_at_ms, kiosk_id, environment, metadata, created_at')
        .eq('session_id', sessionId)
        .order('generated_at_ms', { ascending: false })
        .limit(1)
        .maybeSingle();

    if (error && error.code !== 'PGRST116') {
        console.warn(`[supabase-reports] Не удалось получить отчёт (${kind})`, error.message);
    }
    return data ?? null;
}

/**
 * @param {ReportKind} kind
 * @param {string} sessionId
 */
/**
 * @param {ReportKind} kind
 * @param {string} sessionId
 * @returns {Promise<DeliveryRow[]>}
 */
async function fetchDeliveryRows(kind, sessionId) {
    const client = await ensureSupabaseClient();
    const deliveryTable = DELIVERY_TABLES[kind];
    const { data, error } = await client
        .from(deliveryTable)
        .select('delivery_id, session_id, channel, status, attempts, updated_at, created_at, recipient')
        .eq('session_id', sessionId)
        .order('created_at', { ascending: false })
        .limit(25);

    if (error) {
        console.warn(`[supabase-reports] Не удалось получить deliveries (${kind})`, error.message);
        return [];
    }
    return data ?? [];
}

/**
 * @param {ReportKind} kind
 * @param {string | null | undefined} sessionId
 */
/**
 * @param {ReportKind} kind
 * @param {string | null | undefined} sessionId
 * @returns {Promise<{ report: ReportRow | null; deliveries: DeliveryRow[]; deliveryStats: ReturnType<typeof buildDeliveryStats> } | null>}
 */
export async function fetchReportSnapshot(kind, sessionId) {
    if (!sessionId) {
        return null;
    }
    const [report, deliveries] = await Promise.all([
        fetchReportRow(kind, sessionId),
        fetchDeliveryRows(kind, sessionId),
    ]);

    return {
        report,
        deliveries,
        deliveryStats: buildDeliveryStats(deliveries),
    };
}

/**
 * @param {ReportKind} kind
 */
export function hasSupabaseConfig(kind) {
    return Boolean(REPORT_TABLES[kind] && getSupabaseClient());
}

/**
 * @param {{ kind: ReportKind; sessionId?: string | null; onChange?: () => void }} options
 * @returns {() => void}
 */
export function subscribeReportDeliveries(options) {
    const { kind, sessionId, onChange } = options;
    if (!sessionId) {
        return () => { };
    }
    const client = getSupabaseClient();
    if (!client) {
        return () => { };
    }
    const table = DELIVERY_TABLES[kind];
    const channel = client
        .channel(`report-delivery-${kind}-${sessionId}`)
        .on('postgres_changes', {
            schema: 'public',
            table,
            event: '*',
            filter: `session_id=eq.${sessionId}`,
        }, () => {
            if (typeof onChange === 'function') {
                onChange();
            }
        })
        .subscribe();

    return () => {
        try {
            channel.unsubscribe();
        } catch (error) {
            console.warn('[supabase-reports] Ошибка отписки от канала', error);
        }
    };
}

/**
 * @param {{ deliveryStats: ReturnType<typeof buildDeliveryStats> | null }} snapshot
 */
export function describeDeliveryStats(snapshot) {
    if (!snapshot?.deliveryStats) {
        return 'Очередь доставок Supabase пока пуста';
    }
    const { total, byStatus, lastEventAt } = snapshot.deliveryStats;
    if (!total) {
        return 'Supabase ещё не получил доставок по этой сессии';
    }
    const parts = [
        `Всего ${total}`,
        `успешно: ${byStatus.sent ?? 0}`,
        `в очереди: ${(byStatus.processing ?? 0) + (byStatus.queued ?? 0)}`,
    ];
    if (byStatus.failed) {
        parts.push(`ошибок: ${byStatus.failed}`);
    }
    if (lastEventAt) {
        parts.push(`обновлено: ${new Date(lastEventAt).toLocaleTimeString('ru-RU')}`);
    }
    return parts.join(' · ');
}
