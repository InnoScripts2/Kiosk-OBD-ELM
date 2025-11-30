import { Router } from 'express';
import type { Request, Response } from 'express';
import { asyncHandler } from '../http/async-handler.js';
import type { DeviceType, LockController, LockStatus } from '../services/LockController.js';

type LockControllerPort = Pick<LockController, 'openSlot' | 'closeSlot' | 'getStatus'>;

function isDeviceType(value: unknown): value is DeviceType {
  return value === 'thickness' || value === 'adapter';
}

function formatStatus(status: LockStatus): Omit<LockStatus, 'error'> & { error: string | null } {
  return {
    ...status,
    error: status.error ?? null,
  } as Omit<LockStatus, 'error'> & { error: string | null };
}

export function createLocksRouter(lockController: LockControllerPort): Router {
  const router = Router();

  router.get('/status', asyncHandler(async (_req: Request, res: Response): Promise<void> => {
    const status = await lockController.getStatus();
    res.json({ status: formatStatus(status) });
  }));

  router.post('/:device/open', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    const { device } = req.params;
    if (!isDeviceType(device)) {
      res.status(400).json({ error: 'invalid_device' });
      return;
    }

    await lockController.openSlot(device);
    const status = await lockController.getStatus();
    res.json({ status: formatStatus(status) });
  }));

  router.post('/:device/close', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    const { device } = req.params;
    if (!isDeviceType(device)) {
      res.status(400).json({ error: 'invalid_device' });
      return;
    }

    await lockController.closeSlot(device);
    const status = await lockController.getStatus();
    res.json({ status: formatStatus(status) });
  }));

  return router;
}