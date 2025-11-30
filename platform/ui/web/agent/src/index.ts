/**
 * Главный входной файл для kiosk-shell agent
 */

import { PaymentService } from './services/PaymentService.js';
import { LockController } from './services/LockController.js';
import { ReportService } from './services/ReportService.js';

// Инициализация сервисов
const paymentMock = process.env.PAYMENT_MOCK === 'true';
const appMode = process.env.APP_MODE || 'DEV';

console.log(`[AGENT] Starting kiosk-shell agent in ${appMode} mode`);
console.log(`[AGENT] Payment mock: ${paymentMock}`);

const paymentService = new PaymentService(paymentMock);
const lockController = new LockController();
const reportService = new ReportService();

// Экспорт для использования в других модулях
export {
  paymentService,
  lockController,
  reportService,
};

console.log('[AGENT] Services initialized successfully');

// TODO: Настроить HTTP API для взаимодействия с Android приложением
// TODO: Настроить WebSocket для real-time обновлений
