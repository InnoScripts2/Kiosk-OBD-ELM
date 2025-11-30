import { Router } from 'express';
import type { Request, Response } from 'express';
import { asyncHandler } from '../http/async-handler.js';
import { ReportIngestValidationError, type ReportIngestService } from '../services/ReportIngestService.js';

export function createReportsRouter(service: ReportIngestService): Router {
  const router = Router();

  router.post('/intake', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    try {
      const ingest = await service.intake(req.body ?? {});
      res.status(202).json({ ingest });
    } catch (error) {
      if (error instanceof ReportIngestValidationError) {
        res.status(400).json({ error: 'invalid_payload', message: error.message });
        return;
      }
      console.error('[reports] failed to enqueue report ingest', error);
      res.status(502).json({ error: 'report_ingest_failed' });
    }
  }));

  router.put('/:id/state', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    try {
      await service.updateState(req.params.id, req.body ?? {});
      res.status(202).json({ status: 'accepted' });
    } catch (error) {
      if (error instanceof ReportIngestValidationError) {
        res.status(400).json({ error: 'invalid_state_patch', message: error.message });
        return;
      }
      console.error('[reports] failed to update ingest state', error);
      res.status(502).json({ error: 'report_state_update_failed' });
    }
  }));

  return router;
}
