// @ts-check
import { KioskConfig } from '@core/kiosk-config.js';
import { getSessionState, subscribeSessionState } from '@core/session-manager.js';
import { fetchSessionOverview, getLastSessionSyncResult, hasSupabaseSessionBridge } from '@services/supabase-sessions.js';
import { validateContact } from '../../utils/validators.js';
import { shouldEnableDeliveryMonitor } from '@core/delivery-monitor.js';

const PANEL_ID = 'session-telemetry-panel';
const REFRESH_BUTTON_ID = 'session-telemetry-refresh';
const OVERVIEW_ID = 'session-telemetry-overview';
const OVERVIEW_RAW_ID = 'session-telemetry-overview-raw';
const SUPABASE_STATUS_ID = 'session-telemetry-supabase';
const SESSION_ID_FIELD = 'session-telemetry-id';
const SESSION_STAGE_FIELD = 'session-telemetry-stage';
const SESSION_STATUS_FIELD = 'session-telemetry-status';
const SESSION_SERVICE_FIELD = 'session-telemetry-service';
const SESSION_KIOSK_FIELD = 'session-telemetry-kiosk';
const SESSION_ENV_FIELD = 'session-telemetry-env';
const SESSION_THK_FIELD = 'session-telemetry-thk';
const SESSION_OBD_FIELD = 'session-telemetry-obd';
const REFRESH_STATUS_FIELD = 'session-telemetry-refresh-status';
const COPY_BUTTON_CLASS = 'session-telemetry-copy';
const COPY_SUCCESS_TIMEOUT = 1800;
const CONTACT_THK_FIELD = 'session-telemetry-contact-thk';
const CONTACT_OBD_FIELD = 'session-telemetry-contact-obd';
const AUTO_REFRESH_DEBOUNCE_MS = 2000;
const AUTO_REFRESH_MIN_INTERVAL_MS = 8000;

let overviewLoading = false;
let autoRefreshTimer = null;
let lastAutoRefreshAt = 0;
let pendingAutoRefreshReason = null;
let pendingAutoRefreshRequestedAt = null;
let nextAutoRefreshPlannedAt = null;
let nextAutoRefreshReason = null;
let activeRefreshSource = null;
let activeRefreshReason = null;
let activeRefreshStartedAt = null;
let lastOverviewFetchAt = null;
let lastOverviewFetchSource = null;
let lastOverviewFetchReason = null;
let lastOverviewPayload = null;
const trackedStage = {
    stage: null,
    status: null,
    serviceType: null,
    thicknessId: null,
    obdId: null,
    sessionId: null,
};

function insertStyles() {
    if (document.getElementById('session-telemetry-style')) {
        return;
    }
    const style = document.createElement('style');
    style.id = 'session-telemetry-style';
    style.textContent = `
    #${PANEL_ID} {
      position: fixed;
      bottom: 16px;
      right: 16px;
      background: rgba(19, 23, 32, 0.92);
      color: #f4f4f4;
      padding: 12px 16px;
      border-radius: 12px;
      font-size: 13px;
      font-family: 'Inter', 'Segoe UI', sans-serif;
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
      max-width: 320px;
      z-index: 9999;
    }
    #${PANEL_ID} strong {
      display: block;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #8ec5ff;
      margin-bottom: 4px;
    }
    #${PANEL_ID} dl {
      margin: 0;
      padding: 0;
    }
    #${PANEL_ID} dt {
      font-weight: 600;
      margin-top: 6px;
      font-size: 12px;
      color: #a5b4c8;
    }
    #${PANEL_ID} dd {
      margin: 0;
      margin-bottom: 2px;
      font-size: 13px;
      word-break: break-all;
    }
    #${PANEL_ID} button {
      margin-top: 8px;
      width: 100%;
      padding: 6px 10px;
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.08);
      color: #f4f4f4;
      font-weight: 600;
      cursor: pointer;
    }
    #${PANEL_ID} button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
        #${PANEL_ID} .${COPY_BUTTON_CLASS} {
            width: auto;
            margin-top: 0;
            margin-left: 8px;
            padding: 2px 6px;
            font-size: 11px;
            border-radius: 6px;
        }
    #${OVERVIEW_ID} {
      margin-top: 8px;
      font-size: 12px;
      color: #d9e3ff;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      padding-top: 8px;
      white-space: pre-line;
    }
        #${OVERVIEW_RAW_ID} {
            margin-top: 6px;
            max-height: 160px;
            overflow: auto;
            font-size: 11px;
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 8px;
            padding: 6px;
            color: #cde3ff;
        }
  `;
    document.head.appendChild(style);
}

function createPanel() {
    let panel = document.getElementById(PANEL_ID);
    if (panel) {
        return panel;
    }
    panel = document.createElement('section');
    panel.id = PANEL_ID;
    panel.setAttribute('aria-live', 'polite');
    panel.innerHTML = `
    <strong>Supabase сессия</strong>
    <dl>
      <dt>ID</dt>
      <dd id="${SESSION_ID_FIELD}">—</dd>
      <dt>Этап</dt>
      <dd id="${SESSION_STAGE_FIELD}">—</dd>
      <dt>Статус</dt>
      <dd id="${SESSION_STATUS_FIELD}">—</dd>
      <dt>Услуга</dt>
      <dd id="${SESSION_SERVICE_FIELD}">—</dd>
    <dt>Киоск</dt>
    <dd id="${SESSION_KIOSK_FIELD}">—</dd>
    <dt>Окружение</dt>
    <dd id="${SESSION_ENV_FIELD}">—</dd>
    <dt>Сессия ЛКП</dt>
    <dd><span id="${SESSION_THK_FIELD}">—</span><button class="${COPY_BUTTON_CLASS}" type="button" data-copy-target="${SESSION_THK_FIELD}">Копировать</button></dd>
    <dt>Сессия OBD</dt>
    <dd><span id="${SESSION_OBD_FIELD}">—</span><button class="${COPY_BUTTON_CLASS}" type="button" data-copy-target="${SESSION_OBD_FIELD}">Копировать</button></dd>
    <dt>Контакт ЛКП</dt>
    <dd id="${CONTACT_THK_FIELD}">—</dd>
    <dt>Контакт OBD</dt>
    <dd id="${CONTACT_OBD_FIELD}">—</dd>
        <dt>Обновление</dt>
        <dd id="${REFRESH_STATUS_FIELD}">—</dd>
      <dt>Supabase</dt>
      <dd id="${SUPABASE_STATUS_ID}">Supabase недоступен</dd>
    </dl>
    <button id="${REFRESH_BUTTON_ID}" type="button">Обновить сводку</button>
    <div id="${OVERVIEW_ID}">Сводка ещё не загружена.</div>
        <div>
            <div style="margin-top:4px;font-size:11px;color:#8997b3;">Сырой ответ</div>
            <pre id="${OVERVIEW_RAW_ID}">—</pre>
        </div>
  `;
    document.body.appendChild(panel);
    attachCopyHandlers(panel);
    return panel;
}

function formatSupabaseStatus() {
    if (!hasSupabaseSessionBridge()) {
        return 'Supabase отключён';
    }
    const info = getLastSessionSyncResult();
    if (!info) {
        return 'Синхронизации ещё не выполнялись';
    }
    const time = new Date(info.at).toLocaleTimeString('ru-RU');
    if (info.ok) {
        return `OK · ${time}`;
    }
    return `Ошибка · ${time}${info.message ? ` · ${info.message}` : ''}`;
}

function describeRefreshReason(reason) {
    switch (reason) {
        case 'state-change':
            return 'смена состояния';
        case 'bootstrap':
            return 'инициализация';
        case 'manual':
            return 'ручное обновление';
        case 'auto-interval':
            return 'плановое автообновление';
        default:
            return reason || 'не указано';
    }
}

function describeRefreshSource(source) {
    if (source === 'auto') {
        return 'авто';
    }
    if (source === 'manual') {
        return 'ручное';
    }
    return source || 'неизв.';
}

function formatTimeLabel(timestamp) {
    if (!timestamp) {
        return '';
    }
    return new Date(timestamp).toLocaleTimeString('ru-RU');
}

function formatEtaSuffix(targetTimestamp) {
    if (!targetTimestamp) {
        return '';
    }
    const diffSeconds = Math.round((targetTimestamp - Date.now()) / 1000);
    if (diffSeconds <= 0) {
        return '';
    }
    return ` (через ${diffSeconds} с)`;
}

function updateRefreshStatus() {
    const node = document.getElementById(REFRESH_STATUS_FIELD);
    if (!node) {
        return;
    }
    const lines = [];
    if (overviewLoading) {
        const started = activeRefreshStartedAt ? ` с ${formatTimeLabel(activeRefreshStartedAt)}` : '';
        lines.push(
            `Запрос: ${describeRefreshSource(activeRefreshSource)} · ${describeRefreshReason(activeRefreshReason)}${started}`
        );
    }
    if (pendingAutoRefreshReason && pendingAutoRefreshRequestedAt) {
        lines.push(
            `В очереди: ${describeRefreshReason(pendingAutoRefreshReason)} · ${formatTimeLabel(pendingAutoRefreshRequestedAt)}`
        );
    }
    if (nextAutoRefreshPlannedAt && !overviewLoading) {
        const autoReason = nextAutoRefreshReason || 'auto-interval';
        lines.push(
            `Следующее авто: ${formatTimeLabel(nextAutoRefreshPlannedAt)}${formatEtaSuffix(nextAutoRefreshPlannedAt)} · ${describeRefreshReason(
                autoReason
            )}`
        );
    }
    if (lastOverviewFetchAt) {
        lines.push(
            `Последнее: ${formatTimeLabel(lastOverviewFetchAt)} · ${describeRefreshSource(lastOverviewFetchSource)} · ${describeRefreshReason(
                lastOverviewFetchReason
            )}`
        );
    }
    if (!lines.length) {
        node.textContent = '—';
        return;
    }
    node.textContent = lines.join('\n');
}

function attachCopyHandlers(panel) {
    const buttons = panel.querySelectorAll(`[data-copy-target]`);
    buttons.forEach(button => {
        if (!(button instanceof HTMLButtonElement)) {
            return;
        }
        button.addEventListener('click', () => {
            const targetId = button.getAttribute('data-copy-target');
            if (!targetId) {
                return;
            }
            const targetNode = document.getElementById(targetId);
            if (!targetNode) {
                return;
            }
            const text = targetNode.textContent?.trim();
            if (!text || text === '—') {
                return;
            }
            copyToClipboard(text, button);
        });
    });
}

function copyToClipboard(value, button) {
    if (!navigator?.clipboard?.writeText) {
        return;
    }
    button.disabled = true;
    navigator.clipboard
        .writeText(value)
        .then(() => {
            button.textContent = 'Скопировано';
            setTimeout(() => {
                button.textContent = 'Копировать';
                button.disabled = false;
            }, COPY_SUCCESS_TIMEOUT);
        })
        .catch(() => {
            button.textContent = 'Ошибка';
            setTimeout(() => {
                button.textContent = 'Копировать';
                button.disabled = false;
            }, COPY_SUCCESS_TIMEOUT);
        });
}

function handleSessionStateUpdate(state) {
    renderSessionSnapshot(state);
    const nextStage = state.stage || null;
    const nextStatus = state.status || null;
    const nextService = state.serviceType || state.selectedService || null;
    const nextThkId = state.session?.thicknessId || null;
    const nextObdId = state.session?.obdId || null;
    const nextSessionId = state.session?.kiosk || null;

    let shouldRefresh = false;
    if (trackedStage.stage !== nextStage) {
        shouldRefresh = true;
    } else if (trackedStage.status !== nextStatus) {
        shouldRefresh = true;
    } else if (trackedStage.serviceType !== nextService) {
        shouldRefresh = true;
    } else if (trackedStage.thicknessId !== nextThkId || trackedStage.obdId !== nextObdId) {
        shouldRefresh = true;
    } else if (!trackedStage.sessionId && nextSessionId) {
        shouldRefresh = true;
    }

    trackedStage.stage = nextStage;
    trackedStage.status = nextStatus;
    trackedStage.serviceType = nextService;
    trackedStage.thicknessId = nextThkId;
    trackedStage.obdId = nextObdId;
    trackedStage.sessionId = nextSessionId;

    if (shouldRefresh) {
        scheduleOverviewRefresh('state-change');
    }
}

function scheduleOverviewRefresh(reason = 'state-change') {
    if (!hasSupabaseSessionBridge()) {
        return;
    }
    if (overviewLoading) {
        pendingAutoRefreshReason = reason;
        pendingAutoRefreshRequestedAt = Date.now();
        updateRefreshStatus();
        return;
    }
    const now = Date.now();
    const elapsed = now - lastAutoRefreshAt;
    if (!autoRefreshTimer && elapsed >= AUTO_REFRESH_MIN_INTERVAL_MS) {
        lastAutoRefreshAt = now;
        nextAutoRefreshPlannedAt = null;
        nextAutoRefreshReason = null;
        updateRefreshStatus();
        void refreshOverview({ silent: true, source: 'auto', reason });
        return;
    }
    if (autoRefreshTimer) {
        clearTimeout(autoRefreshTimer);
        autoRefreshTimer = null;
    }
    nextAutoRefreshReason = reason;
    nextAutoRefreshPlannedAt = now + AUTO_REFRESH_DEBOUNCE_MS;
    updateRefreshStatus();
    autoRefreshTimer = setTimeout(() => {
        autoRefreshTimer = null;
        nextAutoRefreshPlannedAt = null;
        nextAutoRefreshReason = null;
        lastAutoRefreshAt = Date.now();
        updateRefreshStatus();
        void refreshOverview({ silent: true, source: 'auto', reason });
    }, AUTO_REFRESH_DEBOUNCE_MS);
}

function renderSessionSnapshot(state) {
    const idNode = document.getElementById(SESSION_ID_FIELD);
    const stageNode = document.getElementById(SESSION_STAGE_FIELD);
    const statusNode = document.getElementById(SESSION_STATUS_FIELD);
    const serviceNode = document.getElementById(SESSION_SERVICE_FIELD);
    const kioskNode = document.getElementById(SESSION_KIOSK_FIELD);
    const envNode = document.getElementById(SESSION_ENV_FIELD);
    const thicknessNode = document.getElementById(SESSION_THK_FIELD);
    const obdNode = document.getElementById(SESSION_OBD_FIELD);
    const contactThkNode = document.getElementById(CONTACT_THK_FIELD);
    const contactObdNode = document.getElementById(CONTACT_OBD_FIELD);
    const supabaseNode = document.getElementById(SUPABASE_STATUS_ID);

    if (idNode) {
        idNode.textContent = state.session.kiosk || '—';
    }
    if (stageNode) {
        stageNode.textContent = state.stage || 'не активен';
    }
    if (statusNode) {
        statusNode.textContent = state.status || '—';
    }
    if (serviceNode) {
        serviceNode.textContent = state.serviceType || state.selectedService || '—';
    }
    if (kioskNode) {
        kioskNode.textContent = KioskConfig.getKioskId() || '—';
    }
    if (envNode) {
        envNode.textContent = KioskConfig.getEnvironment() || 'dev';
    }
    if (thicknessNode) {
        thicknessNode.textContent = state.session?.thicknessId || '—';
    }
    if (obdNode) {
        obdNode.textContent = state.session?.obdId || '—';
    }
    if (contactThkNode) {
        contactThkNode.textContent = formatContact(state.contact?.thickness ?? null);
    }
    if (contactObdNode) {
        contactObdNode.textContent = formatContact(state.contact?.diagnostics ?? null);
    }
    if (supabaseNode) {
        supabaseNode.textContent = formatSupabaseStatus();
    }
}

function formatContact(contactValue) {
    if (!contactValue || typeof contactValue !== 'string') {
        return '—';
    }
    const result = validateContact(contactValue);
    if (!result.valid) {
        return contactValue;
    }
    if (result.type === 'phone') {
        const digits = (result.normalized || contactValue).replace(/\D/g, '');
        if (digits.length >= 10) {
            return `SMS ${formatPhoneForDisplay(digits)}`;
        }
    }
    if (result.type === 'email') {
        return `Email ${result.normalized || contactValue}`;
    }
    return result.normalized || contactValue;
}

function formatPhoneForDisplay(digits) {
    let normalized = digits;
    if (normalized.length === 11 && normalized.startsWith('8')) {
        normalized = `7${normalized.slice(1)}`;
    }
    if (normalized.length === 10) {
        normalized = `7${normalized}`;
    }
    const clean = normalized.replace(/\D/g, '').slice(0, 11);
    const parts = ['+7'];
    const region = clean.slice(1, 4);
    if (region) {
        parts.push(` (${region}`);
        if (region.length === 3) {
            parts[parts.length - 1] += ')';
        }
    }
    const block2 = clean.slice(4, 7);
    if (block2) {
        parts.push(` ${block2}`);
    }
    const block3 = clean.slice(7, 9);
    if (block3) {
        parts.push(`-${block3}`);
    }
    const block4 = clean.slice(9, 11);
    if (block4) {
        parts.push(`-${block4}`);
    }
    return parts.join('');
}

function describeOverview(overview) {
    if (!overview) {
        return 'Данные Supabase недоступны.';
    }
    const parts = [];
    if (overview.status) {
        parts.push(`сессия: ${overview.status}`);
    }
    if (overview.payment_status) {
        parts.push(`оплата: ${overview.payment_status}`);
    }
    if (overview.report_status) {
        parts.push(`отчёт: ${overview.report_status}`);
    }
    if (typeof overview.dtc_count === 'number') {
        parts.push(`DTC: ${overview.dtc_count}`);
    }
    if (typeof overview.measurement_completed === 'number' && typeof overview.measurement_total === 'number') {
        parts.push(`замеры: ${overview.measurement_completed}/${overview.measurement_total}`);
    }
    if (typeof overview.lock_failures === 'number' && overview.lock_failures > 0) {
        parts.push(`замок: ${overview.lock_failures} ошибок`);
    }
    if (overview.last_event) {
        parts.push(`посл. событие: ${new Date(overview.last_event).toLocaleTimeString('ru-RU')}`);
    }
    const metadata = typeof overview.metadata === 'object' && overview.metadata ? overview.metadata : null;
    const contactChannels = metadata?.contactChannels ?? overview.contact_channels ?? null;
    const channelText = describeOverviewContactChannels(contactChannels);
    if (channelText) {
        parts.push(channelText);
    }
    const metadataSegments = describeOverviewMetadata(metadata);
    if (metadataSegments.length) {
        parts.push(...metadataSegments);
    }
    return parts.join('\n');
}

function renderOverviewRaw(overview, options = {}) {
    const node = document.getElementById(OVERVIEW_RAW_ID);
    if (!node) {
        return;
    }
    const { error } = options;
    if (error) {
        node.textContent = error;
        return;
    }
    if (!overview) {
        node.textContent = '—';
        return;
    }
    try {
        node.textContent = JSON.stringify(overview, null, 2);
    } catch (serializationError) {
        node.textContent = `Ошибка сериализации: ${serializationError instanceof Error ? serializationError.message : serializationError}`;
    }
}

function describeOverviewMetadata(metadata) {
    if (!metadata) {
        return [];
    }
    /** @type {string[]} */
    const lines = [];
    const tags = [];
    if (metadata.stage) {
        tags.push(`стадия ${metadata.stage}`);
    }
    const service = metadata.serviceType || metadata.selectedService;
    if (service) {
        tags.push(`услуга ${service}`);
    }
    if (metadata.thicknessType) {
        tags.push(`ЛКП ${metadata.thicknessType}`);
    }
    if (metadata.obdMode || metadata.obdMake) {
        const obd = [metadata.obdMode, metadata.obdMake].filter(Boolean).join(' · ');
        if (obd) {
            tags.push(`OBD ${obd}`);
        }
    }
    if (tags.length) {
        lines.push(`метаданные: ${tags.join(', ')}`);
    }
    if (typeof metadata.reportSent === 'boolean') {
        lines.push(`отчёт отправлен: ${metadata.reportSent ? 'да' : 'нет'}`);
    }
    const refsLine = describeSessionRefs(metadata.sessionRefs);
    if (refsLine) {
        lines.push(refsLine);
    }
    return lines;
}

function describeSessionRefs(refs) {
    if (!refs) {
        return '';
    }
    const entries = [];
    if (refs.kiosk) {
        entries.push(`kiosk=${refs.kiosk}`);
    }
    if (refs.thicknessId) {
        entries.push(`ЛКП=${refs.thicknessId}`);
    }
    if (refs.obdId) {
        entries.push(`OBD=${refs.obdId}`);
    }
    if (!entries.length) {
        return '';
    }
    return `refs: ${entries.join(', ')}`;
}

function describeOverviewContactChannels(channels) {
    if (!channels) {
        return '';
    }
    const entries = [];
    if (Array.isArray(channels)) {
        channels.forEach(channel => {
            if (!channel) {
                return;
            }
            const label = typeof channel.service === 'string' ? channel.service : 'канал';
            const formatted = formatContactChannelValue(channel);
            if (formatted) {
                entries.push(`${label}: ${formatted}`);
            }
        });
    } else if (typeof channels === 'object') {
        Object.entries(channels).forEach(([key, value]) => {
            const label = key === 'thickness' ? 'ЛКП' : key === 'diagnostics' ? 'OBD' : key;
            const formatted = formatContactChannelValue(value);
            if (formatted) {
                entries.push(`${label}: ${formatted}`);
            }
        });
    }
    if (!entries.length) {
        return '';
    }
    return `контакты: ${entries.join(', ')}`;
}

function formatContactChannelValue(channel) {
    if (!channel) {
        return '';
    }
    if (typeof channel === 'string') {
        return formatContact(channel);
    }
    const rawValue = typeof channel.value === 'string' ? channel.value : '';
    const normalized = rawValue.trim();
    if (!normalized) {
        return '';
    }
    const type = typeof channel.type === 'string' ? channel.type : null;
    if (type === 'phone') {
        const digits = normalized.replace(/\D/g, '');
        if (digits) {
            return `SMS ${formatPhoneForDisplay(digits)}`;
        }
    }
    if (type === 'email') {
        return `Email ${normalized}`;
    }
    return normalized;
}

async function refreshOverview(options = {}) {
    const { silent = false, source = 'manual', reason = 'manual' } = options;
    if (overviewLoading) {
        if (source === 'auto') {
            pendingAutoRefreshReason = reason;
            pendingAutoRefreshRequestedAt = Date.now();
            updateRefreshStatus();
        }
        return;
    }
    overviewLoading = true;
    activeRefreshSource = source;
    activeRefreshReason = reason;
    activeRefreshStartedAt = Date.now();
    updateRefreshStatus();
    const button = document.getElementById(REFRESH_BUTTON_ID);
    const overviewNode = document.getElementById(OVERVIEW_ID);
    if (!silent && button) {
        button.disabled = true;
        button.textContent = 'Запрос…';
    }
    try {
        const state = getSessionState();
        const sessionId = state.session.kiosk;
        if (!sessionId) {
            if (overviewNode) {
                overviewNode.textContent = 'Сессия ещё не создана.';
            }
            lastOverviewPayload = null;
            renderOverviewRaw(null, { error: 'Нет активной сессии' });
            return;
        }
        const overview = await fetchSessionOverview(sessionId);
        if (overviewNode) {
            overviewNode.textContent = describeOverview(overview);
        }
        lastOverviewPayload = overview;
        renderOverviewRaw(overview);
    } catch (error) {
        if (overviewNode) {
            overviewNode.textContent = `Ошибка получения сводки: ${error instanceof Error ? error.message : error}`;
        }
        renderOverviewRaw(null, {
            error: `Ошибка: ${error instanceof Error ? error.message : error}`,
        });
    } finally {
        overviewLoading = false;
        if (!silent && button) {
            button.disabled = false;
            button.textContent = 'Обновить сводку';
        }
        const supabaseNode = document.getElementById(SUPABASE_STATUS_ID);
        if (supabaseNode) {
            supabaseNode.textContent = formatSupabaseStatus();
        }
        lastOverviewFetchAt = Date.now();
        lastOverviewFetchSource = source;
        lastOverviewFetchReason = reason;
        activeRefreshSource = null;
        activeRefreshReason = null;
        activeRefreshStartedAt = null;
        const queuedReason = pendingAutoRefreshReason;
        pendingAutoRefreshReason = null;
        pendingAutoRefreshRequestedAt = null;
        lastAutoRefreshAt = Date.now();
        updateRefreshStatus();
        if (queuedReason) {
            scheduleOverviewRefresh(queuedReason);
        }
    }
}

function attachHandlers() {
    const button = document.getElementById(REFRESH_BUTTON_ID);
    if (button) {
        button.addEventListener('click', () => {
            refreshOverview();
        });
    }
}

function bootstrapTelemetry() {
    insertStyles();
    createPanel();
    attachHandlers();
    renderOverviewRaw(null);
    updateRefreshStatus();
    const initialState = getSessionState();
    handleSessionStateUpdate(initialState);
    subscribeSessionState(handleSessionStateUpdate);
    scheduleOverviewRefresh('bootstrap');
}

export function initSessionTelemetry() {
    if (typeof document === 'undefined') {
        return;
    }
    if (!shouldEnableDeliveryMonitor()) {
        return;
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrapTelemetry, { once: true });
    } else {
        bootstrapTelemetry();
    }
}
