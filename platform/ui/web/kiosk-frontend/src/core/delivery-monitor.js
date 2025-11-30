import { KioskConfig } from './kiosk-config.js';

const DEBUG_QUERY_FLAG = 'deliveryMonitor';

function hasDebugQueryFlag() {
    if (typeof window === 'undefined') {
        return false;
    }
    try {
        const params = new URLSearchParams(window.location.search || '');
        if (params.has(DEBUG_QUERY_FLAG)) {
            const value = params.get(DEBUG_QUERY_FLAG);
            return value === null || value === '' || value === '1' || value?.toLowerCase() === 'true';
        }
    } catch {
        // ignore malformed URLSearchParams
    }
    return false;
}

function hasDisableOverride() {
    if (typeof window === 'undefined') {
        return false;
    }
    return Boolean(window.__DISABLE_DELIVERY_MONITOR__);
}

export function shouldEnableDeliveryMonitor() {
    if (hasDisableOverride()) {
        return false;
    }
    if (KioskConfig.isDev() || KioskConfig.isQA()) {
        return true;
    }
    if (KioskConfig.SHOW_DELIVERY_MONITOR) {
        return true;
    }
    return hasDebugQueryFlag();
}

export function applyDeliveryMonitorDataset() {
    if (typeof document === 'undefined') {
        return;
    }
    const enabled = shouldEnableDeliveryMonitor();
    if (document.body) {
        document.body.setAttribute('data-delivery-monitor', enabled ? 'true' : 'false');
    }
    return enabled;
}
