import { createClient } from '@supabase/supabase-js';

/**
 * Инициализирует Supabase клиент и публикует его в window для
 * существующих inline-скриптов киоска.
 */
(function bootstrapSupabase() {
    const envUrl = (import.meta.env?.VITE_SUPABASE_URL ?? '').trim();
    const envAnonKey = (import.meta.env?.VITE_SUPABASE_ANON_KEY ?? '').trim();

    const runtimeUrl = (window.__supabaseConfig?.url ?? window.SUPABASE_URL ?? '').trim();
    const runtimeAnonKey = (window.__supabaseConfig?.anonKey ?? window.SUPABASE_ANON_KEY ?? '').trim();

    const supabaseUrl = envUrl || runtimeUrl;
    const supabaseAnonKey = envAnonKey || runtimeAnonKey;

    if (!supabaseUrl || !supabaseAnonKey) {
        console.warn('[supabase] Конфигурация не задана — облачный режим отключён');
        return;
    }

    try {
        const supabase = createClient(supabaseUrl, supabaseAnonKey, {
            auth: { persistSession: false },
            realtime: { params: { eventsPerSecond: 5 } },
        });

        window.supabase = supabase;
        window.supabaseConfig = { url: supabaseUrl };
        window.SUPABASE_URL = supabaseUrl;

        try {
            const { host } = new URL(supabaseUrl);
            console.info(`[supabase] Подключено к ${host}`);
        } catch {
            console.info('[supabase] Подключено');
        }
    } catch (error) {
        console.error('[supabase] Не удалось инициализировать клиента', error);
    }
})();
