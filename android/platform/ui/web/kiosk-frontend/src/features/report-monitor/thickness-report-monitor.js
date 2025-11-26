// @ts-check
import { describeDeliveryStats, fetchReportSnapshot, hasSupabaseConfig, subscribeReportDeliveries } from '@services/supabase-reports.js';
import { waitForSupabase } from '@core/supabase.js';

/**
 * @typedef {import('../../types/global.d.ts').SessionState} SessionState
 */

const SCREEN_ID = 'screen-thk-done';
const REPORT_STATUS_ID = 'report-status-thk';
/** @type {(() => void) | null} */
let unsubscribe = null;
/** @type {string | null} */
let currentSessionId = null;
let observerStarted = false;

/**
 * @returns {SessionState | null}
 */
function getGlobalSessionState() {
    if (typeof window === 'undefined') {
        return null;
    }
    if (window.__kioskSessionState) {
        return window.__kioskSessionState;
    }
    try {
        const raw = window.sessionStorage?.getItem('sessionState');
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

/**
 * @returns {string | null}
 */
function resolveSessionId() {
    const state = getGlobalSessionState();
    return state?.session?.thicknessId || null;
}

/**
 * @returns {HTMLElement | null}
 */
function ensureMetaHost() {
    const statusNode = document.getElementById(REPORT_STATUS_ID);
    if (!statusNode || !statusNode.parentElement) {
        return null;
    }
    let meta = document.getElementById('report-status-thk-meta');
    if (!meta) {
        meta = document.createElement('div');
        meta.id = 'report-status-thk-meta';
        meta.className = 'report-status-meta';
        statusNode.parentElement.insertBefore(meta, statusNode.nextSibling);
    }
    return meta;
}

/**
 * @param {string} message
 * @param {'muted' | 'positive' | 'warn' | 'error'} [tone]
 * @returns {void}
 */
function renderMeta(message, tone = 'muted') {
    const host = ensureMetaHost();
    if (!host) {
        return;
    }
    host.dataset.tone = tone;
    host.textContent = message;
}

async function refreshSnapshot() {
    const sessionId = currentSessionId || resolveSessionId();
    if (!sessionId) {
        renderMeta('Сессия толщиномера ещё не создана.');
        return;
    }
    try {
        const snapshot = await fetchReportSnapshot('thickness', sessionId);
        if (!snapshot) {
            renderMeta('Supabase ожидает публикации отчёта.');
            return;
        }
        const { report } = snapshot;
        if (!report) {
            renderMeta('Отчёт ещё не выгружен в Supabase.');
            return;
        }
        const generatedAt = report.generated_at_ms ? new Date(Number(report.generated_at_ms)).toLocaleTimeString('ru-RU') : null;
        const parts = [];
        parts.push(`ID: ${report.report_id.slice(0, 8)}…`);
        if (generatedAt) {
            parts.push(`готов: ${generatedAt}`);
        }
        if (report.environment) {
            parts.push(report.environment.toUpperCase());
        }
        renderMeta(`${parts.join(' · ')} · ${describeDeliveryStats(snapshot)}`, 'positive');
    } catch (error) {
        console.warn('[thk-report-monitor] Не удалось получить отчёт из Supabase', error);
        renderMeta('Ошибка чтения Supabase. Попробуйте позже.', 'error');
    }
}

/**
 * @param {string | null} sessionId
 * @returns {Promise<void>}
 */
async function attachSupabaseSubscription(sessionId) {
    if (!sessionId || !hasSupabaseConfig('thickness')) {
        renderMeta('Supabase не настроен — мониторинг недоступен.', 'warn');
        return;
    }
    const client = await waitForSupabase({ attempts: 40, delayMs: 250 });
    if (!client) {
        renderMeta('Нет подключения к Supabase.', 'warn');
        return;
    }
    renderMeta('Подключаем мониторинг Supabase…');
    await refreshSnapshot();
    unsubscribe = subscribeReportDeliveries({
        kind: 'thickness',
        sessionId,
        onChange: () => {
            refreshSnapshot();
        },
    });
}

function startMonitoring() {
    const sessionId = resolveSessionId();
    currentSessionId = sessionId;
    if (!sessionId) {
        renderMeta('Сессия толщиномера недоступна.', 'warn');
        return;
    }
    if (unsubscribe) {
        unsubscribe();
        unsubscribe = null;
    }
    attachSupabaseSubscription(sessionId);
}

function stopMonitoring() {
    if (unsubscribe) {
        unsubscribe();
        unsubscribe = null;
    }
}

function observeScreen() {
    if (observerStarted) {
        return;
    }
    observerStarted = true;
    const screen = document.getElementById(SCREEN_ID);
    if (!screen) {
        return;
    }
    const handleVisibility = () => {
        if (screen.classList.contains('active')) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    };
    const observer = new MutationObserver(handleVisibility);
    observer.observe(screen, { attributes: true, attributeFilter: ['class'] });
    handleVisibility();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', observeScreen, { once: true });
} else {
    observeScreen();
}
