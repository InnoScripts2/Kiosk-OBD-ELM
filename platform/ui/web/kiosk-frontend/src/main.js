import '../styles.css';
import { loadConfig } from './core/config.js';
import { KioskConfig } from './core/kiosk-config.js';
import { initNavigation } from './core/navigation.js';
import { initDeviceStatus } from './core/device-status.js';
import { initPaymentClient } from './core/payment-client.js';
import { initSessionManager } from './core/session-manager.js';
import { initErrorHandler } from './core/error-handler.js';
import { initDevMode } from './core/dev-mode.js';
import { initSessionTelemetry } from './features/session-telemetry/session-telemetry.js';
import { initFlowController } from './core/flow-controller.js';
import { applyDeliveryMonitorDataset, shouldEnableDeliveryMonitor } from './core/delivery-monitor.js';

loadConfig();

const deliveryMonitorEnabled = applyDeliveryMonitorDataset();

initErrorHandler();
initNavigation();
initDeviceStatus();
initPaymentClient();
initSessionManager();
initDevMode();
if (deliveryMonitorEnabled || shouldEnableDeliveryMonitor()) {
  initSessionTelemetry();
}
initFlowController();

// Передаём kiosk_id и environment в глобальные метаданные
if (KioskConfig.getKioskId()) {
  window.kioskMetadata = {
    kiosk_id: KioskConfig.getKioskId(),
    environment: KioskConfig.getEnvironment(),
  };
  console.log('[main] Kiosk metadata set:', window.kioskMetadata);
}

document.addEventListener('contextmenu', (e) => e.preventDefault());
document.addEventListener('selectstart', (e) => e.preventDefault());

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/service-worker.js')
      .then(registration => {
        console.log('[SW] Service Worker registered:', registration.scope);
      })
      .catch(error => {
        console.error('[SW] Service Worker registration failed:', error);
      });
  });
}

console.log('[main] Kiosk frontend initialized');
