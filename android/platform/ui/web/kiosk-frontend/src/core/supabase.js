// @ts-check
/**
 * @typedef {import('@/integrations/supabase/types.generated.ts').Database} Database
 * @typedef {import('@supabase/supabase-js').SupabaseClient<Database>} SupabaseDbClient
 */

const DEFAULT_WAIT_OPTIONS = { attempts: 20, delayMs: 250 };

/**
 * @returns {SupabaseDbClient | null}
 */
export function getSupabaseClient() {
    if (typeof window === 'undefined') {
        return null;
    }
    return window.supabase ?? null;
}

/**
 * @param {{ attempts?: number; delayMs?: number }} [options]
 * @returns {Promise<SupabaseDbClient | null>}
 */
export async function waitForSupabase(options = DEFAULT_WAIT_OPTIONS) {
    const attempts = typeof options.attempts === 'number' ? options.attempts : DEFAULT_WAIT_OPTIONS.attempts;
    const delayMs = typeof options.delayMs === 'number' ? options.delayMs : DEFAULT_WAIT_OPTIONS.delayMs;

    for (let i = 0; i < (attempts ?? DEFAULT_WAIT_OPTIONS.attempts); i += 1) {
        const client = getSupabaseClient();
        if (client) {
            return client;
        }
        await new Promise(resolve => setTimeout(resolve, delayMs));
    }
    return null;
}

/**
 * @param {{ attempts?: number; delayMs?: number }} [options]
 * @returns {Promise<SupabaseDbClient>}
 */
export async function ensureSupabaseClient(options) {
    const client = await waitForSupabase(options);
    if (!client) {
        throw new Error('Supabase клиент недоступен. Убедитесь, что заданы VITE_SUPABASE_URL/VITE_SUPABASE_ANON_KEY или window.__supabaseConfig.');
    }
    return client;
}

/**
 * @param {(client: SupabaseDbClient) => void} callback
 * @param {{ attempts?: number; delayMs?: number }} [options]
 */
export function onSupabaseReady(callback, options) {
    waitForSupabase(options).then(client => {
        if (client) {
            callback(client);
        }
    });
}
