import { createServer } from 'http';
import { PaymentService } from './services/PaymentService.js';
import { LockController } from './services/LockController.js';
import { ReportService } from './services/ReportService.js';
import { createAgentApp } from './app.js';
import { DeviceStatusGateway } from './websocket/device-status-gateway.js';

async function bootstrap() {
  const paymentMock = process.env.PAYMENT_MOCK !== 'false';
  const appMode = process.env.APP_MODE || 'DEV';
  const port = Number.parseInt(process.env.NODE_AGENT_PORT || process.env.PORT || '7070', 10);

  console.log(`[AGENT] Starting kiosk-shell agent in ${appMode} mode`);
  console.log(`[AGENT] Payment mock: ${paymentMock}`);

  const paymentService = new PaymentService(paymentMock);
  const lockController = new LockController();
  const reportService = new ReportService();

  await lockController.initialize();

  const app = createAgentApp({ paymentService, lockController, reportService });
  const server = createServer(app);
  const wsGateway = new DeviceStatusGateway({ server, lockController });

  await new Promise<void>((resolve) => {
    server.listen(port, () => {
      console.log(`[AGENT] HTTP server listening on http://localhost:${port}`);
      resolve();
    });
  });

  const shutdown = async () => {
    console.log('[AGENT] Shutting down...');
    await wsGateway.stop().catch((error) => console.warn('[AGENT] Failed to stop WS gateway', error));
    await lockController.shutdown().catch((error) => console.warn('[AGENT] Failed to shutdown lock controller', error));
    await new Promise<void>((resolve) => server.close(() => resolve()));
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

bootstrap().catch((error) => {
  console.error('[AGENT] Failed to start kiosk-shell agent', error);
  process.exit(1);
});
