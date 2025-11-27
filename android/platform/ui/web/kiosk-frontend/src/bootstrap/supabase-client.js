import { createClient } from '@supabase/supabase-js';

(function bootstrapSupabase() {
    const EVENT_NAME = window.__kioskRuntimeConfig?.EVENT_NAME ?? 'supabase:config-change';
    let currentConfig = null;
    let warnedNoConfig = false;

    const sanitizeConfig = (input, fallbackSource) => {
        if (!input) {
            return null;
        }
        const url = typeof input.url === 'string' ? input.url.trim() : '';
        const anonKey = typeof input.anonKey === 'string' ? input.anonKey.trim() : '';
        if (!url || !anonKey) {
            return null;
        }
        return {
            url,
            anonKey,
            source: typeof input.source === 'string' ? input.source : (fallbackSource ?? 'runtime'),
        };
    };

    const resolveInitialConfig = () => {
        const runtimeConfig = sanitizeConfig(window.__supabaseConfig, 'runtime');
        if (runtimeConfig) {
            return runtimeConfig;
        }

        const envUrl = (import.meta.env?.VITE_SUPABASE_URL ?? '').trim();
        const envAnonKey = (import.meta.env?.VITE_SUPABASE_ANON_KEY ?? '').trim();
        if (envUrl && envAnonKey) {
            return { url: envUrl, anonKey: envAnonKey, source: 'env' };
        }

        const legacyUrl = (window.SUPABASE_URL ?? '').trim();
        const legacyAnonKey = (window.SUPABASE_ANON_KEY ?? '').trim();
        if (legacyUrl && legacyAnonKey) {
            return { url: legacyUrl, anonKey: legacyAnonKey, source: 'legacy' };
        }

        return null;
    };

    const configsEqual = (a, b) => {
        if (!a || !b) {
            return false;
        }
        return a.url === b.url && a.anonKey === b.anonKey;
    };

    const applyConfig = (nextConfig) => {
        if (!nextConfig) {
            if (currentConfig) {
                console.info('[supabase] Клиент отключён');
            } else if (!warnedNoConfig) {
                console.warn('[supabase] Конфигурация не задана — облачный режим отключён');
            }
            warnedNoConfig = true;
            currentConfig = null;
            delete window.supabase;
            delete window.supabaseConfig;
            delete window.SUPABASE_URL;
            return;
        }

        warnedNoConfig = false;

        if (configsEqual(currentConfig, nextConfig)) {
            return;
        }

        try {
            const supabase = createClient(nextConfig.url, nextConfig.anonKey, {
                auth: { persistSession: false },
                realtime: { params: { eventsPerSecond: 5 } },
            });

            currentConfig = { ...nextConfig };
            window.supabase = supabase;
            window.supabaseConfig = { url: nextConfig.url, source: nextConfig.source };
            window.SUPABASE_URL = nextConfig.url;

            try {
                const { host } = new URL(nextConfig.url);
                console.info(`[supabase] Подключено к ${host} (${nextConfig.source})`);
            } catch {
                console.info(`[supabase] Подключено (${nextConfig.source})`);
            }
        } catch (error) {
            console.error('[supabase] Не удалось инициализировать клиента', error);
        }
    };

    const handleConfigChange = (event) => {
        const detail = sanitizeConfig(event?.detail, 'settings');
        if (!detail) {
            applyConfig(null);
            return;
        }
        applyConfig(detail);
    };

    applyConfig(resolveInitialConfig());
    window.addEventListener(EVENT_NAME, handleConfigChange);

    window.supabaseBootstrap = {
        applyConfig: (config) => applyConfig(sanitizeConfig(config, 'manual')),
        getCurrentConfig: () => (currentConfig ? { ...currentConfig } : null),
    };
})();
