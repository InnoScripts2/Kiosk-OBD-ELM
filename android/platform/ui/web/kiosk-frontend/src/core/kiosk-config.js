/* ============================================================================
 * Глобальные переменные для режима работы киоска
 * ============================================================================ */
let APP_MODE = import.meta.env.VITE_APP_MODE || 'DEV';
const KIOSK_ID = import.meta.env.VITE_KIOSK_ID || null;
let ENABLE_SKIP_BUTTON = import.meta.env.VITE_ENABLE_SKIP_BUTTON === 'true';
let DEVICE_MOCK_THICKNESS = import.meta.env.VITE_DEVICE_MOCK_THICKNESS === 'true';
let DEVICE_MOCK_OBD = import.meta.env.VITE_DEVICE_MOCK_OBD === 'true';
let PAYMENT_MOCK = import.meta.env.VITE_PAYMENT_MOCK === 'true';
const SESSION_TIMEOUT_MS = parseInt(import.meta.env.VITE_SESSION_TIMEOUT_MS || '300000', 10);
const AUTO_RESET_MS = parseInt(import.meta.env.VITE_AUTO_RESET_MS || '30000', 10);
const SHOW_DELIVERY_MONITOR = import.meta.env.VITE_SHOW_DELIVERY_MONITOR === 'true';
const ENABLE_CLEAR_DTC = import.meta.env.VITE_ENABLE_CLEAR_DTC === 'true';

// Валидация режима
const VALID_MODES = ['DEV', 'QA', 'PROD'];
if (!VALID_MODES.includes(APP_MODE)) {
    console.error(`[config] Invalid APP_MODE: ${APP_MODE}, defaulting to DEV`);
    APP_MODE = 'DEV';
}

// Защита PROD от небезопасных флагов
if (APP_MODE === 'PROD') {
    if (ENABLE_SKIP_BUTTON) {
        console.warn('[config] ENABLE_SKIP_BUTTON forced to false in PROD');
        ENABLE_SKIP_BUTTON = false;
    }
    if (DEVICE_MOCK_THICKNESS || DEVICE_MOCK_OBD) {
        console.warn('[config] Device mocks forced to false in PROD');
        DEVICE_MOCK_THICKNESS = false;
        DEVICE_MOCK_OBD = false;
    }
    if (PAYMENT_MOCK) {
        console.warn('[config] PAYMENT_MOCK forced to false in PROD');
        PAYMENT_MOCK = false;
    }
}

/**
 * Экспорт конфигурации киоска
 */
export const KioskConfig = {
    APP_MODE,
    KIOSK_ID,
    ENABLE_SKIP_BUTTON,
    DEVICE_MOCK_THICKNESS,
    DEVICE_MOCK_OBD,
    PAYMENT_MOCK,
    SESSION_TIMEOUT_MS,
    AUTO_RESET_MS,
    SHOW_DELIVERY_MONITOR,
    ENABLE_CLEAR_DTC,

    isProd() {
        return APP_MODE === 'PROD';
    },

    isDev() {
        return APP_MODE === 'DEV';
    },

    isQA() {
        return APP_MODE === 'QA';
    },

    shouldShowDevButton() {
        return !this.isProd() && ENABLE_SKIP_BUTTON;
    },

    getEnvironment() {
        return APP_MODE.toLowerCase();
    },

    getKioskId() {
        return KIOSK_ID;
    },
};

console.log('[config] Kiosk configuration loaded:', {
    APP_MODE,
    KIOSK_ID: KIOSK_ID || '(not set)',
    ENABLE_SKIP_BUTTON,
    DEVICE_MOCK_THICKNESS,
    DEVICE_MOCK_OBD,
    PAYMENT_MOCK,
    SESSION_TIMEOUT_MS,
    AUTO_RESET_MS,
});
