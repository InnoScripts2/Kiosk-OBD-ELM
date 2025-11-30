import { Router } from 'express';
import type { Request, Response } from 'express';
import { asyncHandler } from '../http/async-handler.js';
import type { PaymentService } from '../services/PaymentService.js';
import type { LockController } from '../services/LockController.js';
import type { ReportService } from '../services/ReportService.js';

interface HealthRouterOptions {
  paymentService: PaymentService;
  lockController: Pick<LockController, 'getStatus'>;
  reportService: ReportService;
  buildInfo?: Record<string, unknown>;
}

export function createHealthRouter(options: HealthRouterOptions): Router {
  const router = Router();

  router.get('/', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    const lockStatus = await options.lockController.getStatus();

    res.json({
      status: 'ok',
      timestamp: new Date().toISOString(),
      services: {
        payment: {
          mockMode: options.paymentService.isMockMode(),
        },
        lock: {
          ...lockStatus,
          error: lockStatus.error ?? null,
        },
        report: {
          available: typeof options.reportService.toHTML === 'function',
        },
      },
      build: options.buildInfo ?? null,
    });
  }));

  return router;
}