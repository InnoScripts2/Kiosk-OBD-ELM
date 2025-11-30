import { Router } from 'express';
import type { Request, Response } from 'express';
import { asyncHandler } from '../http/async-handler.js';
import type { PaymentConfirmation, PaymentIntent, PaymentService } from '../services/PaymentService.js';

interface CreateIntentBody {
  amount?: unknown;
  sessionId?: unknown;
  meta?: Record<string, unknown>;
}

interface ConfirmDevBody {
  id?: unknown;
}

interface SerializedIntent {
  intentId: string;
  amount: number;
  sessionId: string;
  status: PaymentIntent['status'];
  qrCode: string | null;
  createdAt: string;
}

interface SerializedConfirmation {
  intentId: string;
  confirmed: boolean;
  timestamp: string;
}

function serializeIntent(intent: PaymentIntent): SerializedIntent {
  return {
    intentId: intent.intentId,
    amount: intent.amount,
    sessionId: intent.sessionId,
    status: intent.status,
    qrCode: intent.qrCode ?? null,
    createdAt: intent.createdAt.toISOString(),
  };
}

function serializeConfirmation(confirmation: PaymentConfirmation): SerializedConfirmation {
  return {
    intentId: confirmation.intentId,
    confirmed: confirmation.confirmed,
    timestamp: confirmation.timestamp.toISOString(),
  };
}

function resolveSessionId(raw: unknown): string {
  if (typeof raw === 'string' && raw.trim().length > 0) {
    return raw.trim();
  }
  return `session_${Date.now()}`;
}

export function createPaymentsRouter(paymentService: PaymentService): Router {
  const router = Router();

  router.post('/intent', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    const body: CreateIntentBody = req.body ?? {};
    const amountNumber = Number(body.amount);

    if (!Number.isFinite(amountNumber) || amountNumber <= 0) {
      res.status(400).json({ error: 'invalid_amount' });
      return;
    }

    const sessionId = resolveSessionId(body.sessionId);
    const intent = await paymentService.createPaymentIntent(amountNumber, sessionId);

    res.status(201).json({ intent: serializeIntent(intent) });
  }));

  router.get('/:intentId/status', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    const { intentId } = req.params;
    const intent = await paymentService.getStatus(intentId);

    if (!intent) {
      res.status(404).json({ error: 'payment_intent_not_found' });
      return;
    }

    res.json({ intent: serializeIntent(intent) });
  }));

  router.post('/confirm-dev', asyncHandler(async (req: Request, res: Response): Promise<void> => {
    if (!paymentService.isMockMode()) {
      res.status(400).json({ error: 'confirmation_not_available' });
      return;
    }

    const body: ConfirmDevBody = req.body ?? {};
    if (typeof body.id !== 'string' || body.id.trim().length === 0) {
      res.status(400).json({ error: 'invalid_intent_id' });
      return;
    }

    const confirmation = await paymentService.confirmPayment(body.id.trim());
    res.json({ confirmation: serializeConfirmation(confirmation) });
  }));

  return router;
}