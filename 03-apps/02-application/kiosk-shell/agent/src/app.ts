import express from 'express';
import type { PaymentService } from './services/PaymentService.js';
import type { LockController } from './services/LockController.js';
import type { ReportService } from './services/ReportService.js';
import { createPaymentsRouter } from './routes/payments.js';
import { createLocksRouter } from './routes/locks.js';
import { createHealthRouter } from './routes/health.js';

export interface AgentAppOptions {
  paymentService: PaymentService;
  lockController: LockController;
  reportService: ReportService;
}

export function createAgentApp(options: AgentAppOptions) {
  const app = express();

  app.disable('x-powered-by');
  app.use(express.json({ limit: '1mb' }));

  app.get('/', (req, res) => {
    res.json({ status: 'ok' });
  });

  app.use('/health', createHealthRouter(options));
  app.use('/payments', createPaymentsRouter(options.paymentService));
  app.use('/locks', createLocksRouter(options.lockController));

  app.use((req, res) => {
    res.status(404).json({ error: 'not_found' });
  });

  app.use((err: Error, req: express.Request, res: express.Response, next: express.NextFunction) => {
    console.error('[agent] Unhandled error', err);
    if (res.headersSent) {
      next(err);
      return;
    }
    res.status(500).json({ error: 'internal_error' });
  });

  return app;
}