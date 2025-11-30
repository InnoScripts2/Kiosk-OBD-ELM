// @ts-check
import { KioskConfig } from '@core/kiosk-config.js';
import { ensureSupabaseClient, getSupabaseClient } from '@core/supabase.js';
import { validateContact } from '../utils/validators.js';

/**
 * @typedef {import('../types/global.d.ts').SessionState} SessionState
 * @typedef {import('../types/global.d.ts').SessionLifecycleStatus} SessionLifecycleStatus
 * @typedef {import('../types/global.d.ts').SessionServiceType} SessionServiceType
 * @typedef {import('../types/supabase-overview').KioskSessionOverview} KioskSessionOverview
 * @typedef {import('../types/supabase-overview').SupabaseSessionMetadata} SupabaseSessionMetadata
 * @typedef {import('../types/supabase-overview').SupabaseContactChannel} SupabaseContactChannel
 * @typedef {import('../types/supabase-overview').SupabaseContactChannels} SupabaseContactChannels
 */

/**
 * @typedef {Object} SessionSyncPayload
 * @property {string} sessionId
 * @property {SessionLifecycleStatus} status
 * @property {SessionServiceType} serviceType
 * @property {string | null | undefined} [stage]
 * @property {Record<string, unknown> | null | undefined} [metadata]
 */

/**
 * @typedef {Object} SessionEventPayload
 * @property {string} sessionId
 * @property {string} eventType
 * @property {string | null | undefined} [eventStatus]
 * @property {string | null | undefined} [message]
 * @property {number | null | undefined} [deviceTimestamp]
 * @property {Record<string, unknown> | null | undefined} [payload]
 */

/** @type {{ ok: boolean; at: number; status?: string | null; message?: string } | null} */
let lastSyncResult = null;

function resolveKioskId() {
    return KioskConfig.getKioskId() || 'kiosk-dev';
}

function resolveEnvironment() {
    return KioskConfig.getEnvironment() || 'dev';
}

function hasRuntimeSupabaseConfig() {
    const envUrl = (import.meta.env?.VITE_SUPABASE_URL ?? '').trim();
    const envKey = (import.meta.env?.VITE_SUPABASE_ANON_KEY ?? '').trim();
    if (envUrl && envKey) {
        return true;
    }
    if (typeof window === 'undefined') {
        return false;
    }
    const runtimeUrl = (window.__supabaseConfig?.url ?? window.SUPABASE_URL ?? '').trim();
    const runtimeAnon = (window.__supabaseConfig?.anonKey ?? window.SUPABASE_ANON_KEY ?? '').trim();
    return Boolean(runtimeUrl && (runtimeAnon || envKey));
}

function describeContactChannel(value) {
    const trimmed = typeof value === 'string' ? value.trim() : '';
    if (!trimmed) {
        return null;
    }
    const result = validateContact(trimmed);
    if (!result.valid) {
        return {
            value: trimmed,
            type: null,
        };
    }
    return {
        value: result.normalized ?? trimmed,
        type: result.type ?? null,
    };
}

function normalizeContactChannel(channel) {
    if (!channel) {
        return null;
    }
    if (typeof channel === 'string') {
        return describeContactChannel(channel);
    }
    if (typeof channel === 'object') {
        const cast = /** @type {SupabaseContactChannel | null} */ (channel);
        const value = typeof cast?.value === 'string' && cast.value.trim() ? cast.value.trim() : null;
        if (!value) {
            return null;
        }
        return {
            value,
            type: typeof cast?.type === 'string' ? cast?.type : null,
        };
    }
    return null;
}

function normalizeContactChannels(channels) {
    if (!channels || typeof channels !== 'object') {
        return null;
    }
    /** @type {Record<string, SupabaseContactChannel>} */
    const normalized = {};
    Object.entries(channels).forEach(([key, value]) => {
        const channel = normalizeContactChannel(value);
        if (channel) {
            normalized[key] = channel;
        }
    });
    return Object.keys(normalized).length ? normalized : null;
}

function normalizeSessionRefs(refs) {
    if (!refs || typeof refs !== 'object') {
        return null;
    }
    const { kiosk = null, thicknessId = null, obdId = null } = /** @type {Record<string, unknown>} */ (refs);
    if (!kiosk && !thicknessId && !obdId) {
        return null;
    }
    return {
        kiosk: typeof kiosk === 'string' ? kiosk : null,
        thicknessId: typeof thicknessId === 'string' ? thicknessId : null,
        obdId: typeof obdId === 'string' ? obdId : null,
    };
}

function normalizeMetadata(metadata) {
    if (!metadata || typeof metadata !== 'object') {
        return null;
    }
    const cast = /** @type {SupabaseSessionMetadata} */ (metadata);
    const normalizedContactChannels = normalizeContactChannels(cast.contactChannels);
    const normalizedContact = cast.contact && typeof cast.contact === 'object' ? cast.contact : null;
    return {
        ...cast,
        contact: normalizedContact,
        contactChannels: normalizedContactChannels,
        sessionRefs: normalizeSessionRefs(cast.sessionRefs),
    };
}

function normalizeOverview(raw) {
    if (!raw || typeof raw !== 'object') {
        return null;
    }
    const cast = /** @type {KioskSessionOverview} */ (raw);
    return {
        ...cast,
        metadata: normalizeMetadata(cast.metadata),
        contact_channels: normalizeContactChannels(cast.contact_channels) ?? undefined,
    };
}

/**
 * @returns {boolean}
 */
export function hasSupabaseSessionBridge() {
    return Boolean(getSupabaseClient() || hasRuntimeSupabaseConfig());
}

function mapMetadata(sessionState, overrides) {
    /** @type {Record<string, unknown>} */
    const metadata = {
        contact: sessionState.contact,
        contactChannels: {
            thickness: describeContactChannel(sessionState.contact?.thickness ?? null),
            diagnostics: describeContactChannel(sessionState.contact?.diagnostics ?? null),
        },
        reportSent: sessionState.reportSent,
        thicknessType: sessionState.thicknessType,
        obdMode: sessionState.obdMode,
        obdMake: sessionState.obdMake,
        selectedService: sessionState.selectedService,
        serviceType: sessionState.serviceType,
        stage: sessionState.stage,
    };
    if (sessionState.session) {
        metadata.sessionRefs = {
            kiosk: sessionState.session.kiosk,
            thicknessId: sessionState.session.thicknessId,
            obdId: sessionState.session.obdId,
        };
    }
    if (overrides && typeof overrides === 'object') {
        return { ...metadata, ...overrides };
    }
    return metadata;
}

async function executeRpc(promise) {
    const response = await promise;
    if (response?.error) {
        throw new Error(`[supabase-sessions] ${response.error.message}`);
    }
    return response ? response.data : null;
}

/**
 * @param {SessionSyncPayload & { state?: SessionState }} payload
 * @returns {Promise<unknown | null>}
 */
export async function syncKioskSessionState(payload) {
    if (!payload.sessionId) {
        console.warn('[supabase-sessions] sessionId is required for sync');
        return null;
    }
    if (!hasSupabaseSessionBridge()) {
        return null;
    }
    try {
        const client = await ensureSupabaseClient();
        const metadata = payload.state ? mapMetadata(payload.state, payload.metadata) : payload.metadata;
        const data = await executeRpc(
            client
                .rpc('upsert_kiosk_session_state', {
                    p_session_id: payload.sessionId,
                    p_kiosk_id: resolveKioskId(),
                    p_environment: resolveEnvironment(),
                    p_service_type: payload.serviceType ?? 'thickness',
                    p_status: payload.status ?? 'created',
                    p_stage: payload.stage ?? null,
                    p_metadata: metadata ?? {},
                })
                .maybeSingle()
        );
        lastSyncResult = {
            ok: true,
            at: Date.now(),
            status: typeof data === 'object' && data && 'status' in data ? /** @type {any} */ (data).status : payload.status,
        };
        return data;
    } catch (error) {
        lastSyncResult = {
            ok: false,
            at: Date.now(),
            message: error instanceof Error ? error.message : String(error),
        };
        console.warn('[supabase-sessions] sync failed:', error);
        return null;
    }
}

/**
 * @param {SessionEventPayload} payload
 * @returns {Promise<void>}
 */
export async function appendSessionEvent(payload) {
    if (!payload.sessionId || !payload.eventType) {
        return;
    }
    if (!hasSupabaseSessionBridge()) {
        return;
    }
    try {
        const client = await ensureSupabaseClient();
        await executeRpc(
            client
                .rpc('append_kiosk_session_event', {
                    p_session_id: payload.sessionId,
                    p_event_type: payload.eventType,
                    p_event_status: payload.eventStatus ?? null,
                    p_message: payload.message ?? null,
                    p_device_timestamp_ms: payload.deviceTimestamp ?? null,
                    p_payload: payload.payload ?? {},
                    p_kiosk_id: resolveKioskId(),
                    p_environment: resolveEnvironment(),
                })
                .maybeSingle()
        );
    } catch (error) {
        console.warn('[supabase-sessions] append event failed:', error);
    }
}

/**
 * @param {string | null | undefined} sessionId
 * @returns {Promise<KioskSessionOverview | null>}
 */
export async function fetchSessionOverview(sessionId) {
    if (!sessionId || !hasSupabaseSessionBridge()) {
        return null;
    }
    try {
        const client = await ensureSupabaseClient();
        const data = await executeRpc(
            client.rpc('get_kiosk_session_overview', {
                p_session_id: sessionId,
            }).maybeSingle()
        );
        return normalizeOverview(data);
    } catch (error) {
        console.warn('[supabase-sessions] overview fetch failed:', error);
        return null;
    }
}

/**
 * @returns {{ ok: boolean; at: number; status?: string | null; message?: string } | null}
 */
export function getLastSessionSyncResult() {
    return lastSyncResult;
}
