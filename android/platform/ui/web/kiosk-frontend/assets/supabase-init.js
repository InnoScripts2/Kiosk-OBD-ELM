const CONFIG_EVENT = window.__kioskRuntimeConfig?.EVENT_NAME ?? 'supabase:config-change';
const PAYMENT_EVENT = window.__kioskRuntimeConfig?.PAYMENT_EVENT ?? 'supabase:payment-change';
const SESSION_EVENT = 'kiosk:session-id-changed';
const RETRY_DELAY_MS = 1200;
const INITIAL_DELAY_MS = 800;

let activeSessionId = null;
let activeChannels = [];
let resubscribeTimer = null;
let attaching = false;

function readSessionId() {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.SESSION_ID || null;
}

function hasSupabaseConfig() {
  if (window.supabaseBootstrap?.getCurrentConfig) {
    return Boolean(window.supabaseBootstrap.getCurrentConfig());
  }
  if (window.__supabaseConfig && window.__supabaseConfig.url && window.__supabaseConfig.anonKey) {
    return true;
  }
  return Boolean(window.SUPABASE_URL && window.SUPABASE_ANON_KEY);
}

function disposeChannels() {
  if (!activeChannels.length) {
    return;
  }
  activeChannels.forEach(channel => {
    try {
      channel.unsubscribe();
    } catch (error) {
      console.warn('[supabase-init] Не удалось отписаться от канала', error);
    }
  });
  activeChannels = [];
  activeSessionId = null;
  delete window.__supabaseChannels;
}

function normalizePaymentPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return null;
  }

  const record = (payload.new && typeof payload.new === 'object')
    ? payload.new
    : (payload.old && typeof payload.old === 'object')
      ? payload.old
      : null;

  if (!record) {
    return null;
  }

  const metadata = (record.metadata && typeof record.metadata === 'object') ? record.metadata : {};
  const amountMinorRaw = record.amount_minor ?? record.amountMinor ?? metadata.amount_minor ?? metadata.amountMinor;
  const amountMinor = Number(amountMinorRaw);
  const normalizedAmountMinor = Number.isFinite(amountMinor) ? amountMinor : null;
  const normalizedAmount = typeof record.amount === 'number'
    ? record.amount
    : (normalizedAmountMinor !== null ? normalizedAmountMinor / 100 : null);

  const serviceSource = metadata.service || metadata.service_type || record.service || record.service_type || null;

  const detail = {
    record,
    eventType: payload.eventType || payload.type || null,
    table: payload.table || null,
    schema: payload.schema || null,
    source: payload.origin || payload.table || null,
    amountMinor: normalizedAmountMinor,
    amount: typeof normalizedAmount === 'number' && Number.isFinite(normalizedAmount) ? normalizedAmount : null,
    currency: (record.currency || metadata.currency || 'RUB').toString().toUpperCase(),
    sessionId: record.session_id || record.sessionId || metadata.session_id || metadata.sessionId || null,
    intentId: record.intent_id || record.intentId || metadata.intent_id || metadata.intentId || record.id || null,
    service: serviceSource ? serviceSource.toString().toLowerCase() : null,
    status: (record.status || record.payment_status || metadata.status || payload.status || '').toString().toLowerCase() || null,
    gateway: record.gateway || metadata.gateway || null,
    recordedAt: record.recorded_at || record.updated_at || record.created_at || null,
  };

  return detail;
}

function emitPaymentEvent(payload) {
  const detail = normalizePaymentPayload(payload);
  if (!detail) {
    return;
  }
  detail.receivedAt = Date.now();
  try {
    window.dispatchEvent(new CustomEvent(PAYMENT_EVENT, { detail }));
  } catch (error) {
    console.warn('[supabase-init] Не удалось отправить событие оплаты', error);
  }
}

function updatePaymentInfo(payload) {
  const info = document.getElementById('obd-payment-info');
  if (!info) {
    return;
  }
  const detail = normalizePaymentPayload(payload);
  if (!detail) {
    return;
  }

  const statusLabel = detail.status ? detail.status.toUpperCase() : '—';
  if (typeof detail.amount === 'number') {
    const formatter = new Intl.NumberFormat('ru-RU', {
      style: 'currency',
      currency: detail.currency || 'RUB',
      maximumFractionDigits: 0,
    });
    info.textContent = `Статус платежа: ${statusLabel} • ${formatter.format(detail.amount)}`;
    return;
  }

  info.textContent = `Статус платежа: ${statusLabel}`;
}

function handlePaymentPayload(origin) {
  return (payload) => {
    const enriched = { ...payload, origin };
    console.log('[realtime] payments change', origin, payload);
    updatePaymentInfo(enriched);
    emitPaymentEvent(enriched);
  };
}

async function attachRealtime(force = false) {
  if (attaching) {
    if (!force) {
      return;
    }
  }
  const supabase = window.supabase;
  const sessionId = readSessionId();
  if (!supabase || !sessionId) {
    disposeChannels();
    if (!supabase && hasSupabaseConfig()) {
      scheduleRealtime(true, RETRY_DELAY_MS);
    }
    return;
  }
  if (!force && sessionId === activeSessionId && activeChannels.length) {
    return;
  }

  attaching = true;
  disposeChannels();

  try {
    const paymentsChannel = supabase
      .channel(`payments_session_${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'payments',
          filter: `session_id=eq.${sessionId}`,
        },
        handlePaymentPayload('payments'),
      );

    const paymentIntentsChannel = supabase
      .channel(`kiosk_payment_intents_${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'kiosk_payment_intents',
          filter: `session_id=eq.${sessionId}`,
        },
        handlePaymentPayload('kiosk_payment_intents'),
      );

    const sessionChannel = supabase
      .channel(`sessions_${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'sessions',
          filter: `id=eq.${sessionId}`,
        },
        (payload) => {
          console.log('[realtime] session change', payload);
        },
      );

    const channelEntries = [
      { key: 'payments', channel: paymentsChannel },
      { key: 'paymentIntents', channel: paymentIntentsChannel },
      { key: 'sessions', channel: sessionChannel },
    ];

    await Promise.all(channelEntries.map((entry) => entry.channel.subscribe()));

    activeSessionId = sessionId;
    activeChannels = channelEntries.map((entry) => entry.channel);
    window.__supabaseChannels = channelEntries.reduce((acc, entry) => {
      acc[entry.key] = entry.channel;
      return acc;
    }, {});
    console.info('[supabase-init] Realtime подключено для сессии', sessionId);
  } catch (error) {
    console.warn('[supabase-init] Не удалось подключить realtime', error);
    disposeChannels();
    scheduleRealtime(true, RETRY_DELAY_MS);
  } finally {
    attaching = false;
  }
}

function scheduleRealtime(force = false, delayMs = 400) {
  if (resubscribeTimer) {
    clearTimeout(resubscribeTimer);
  }
  resubscribeTimer = setTimeout(() => {
    resubscribeTimer = null;
    attachRealtime(force);
  }, delayMs);
}

document.addEventListener('visibilitychange', () => {
  if (!document.hidden) {
    scheduleRealtime(false, 200);
  }
});

scheduleRealtime(true, INITIAL_DELAY_MS);
window.addEventListener(CONFIG_EVENT, () => scheduleRealtime(true, 200));
window.addEventListener(SESSION_EVENT, () => scheduleRealtime(true, 0));

window.__supabaseRealtime = {
  refresh: () => scheduleRealtime(true, 0),
  dispose: () => disposeChannels(),
};
