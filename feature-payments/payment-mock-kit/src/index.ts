/**
 * @kiosk/payment-mock
 * 
 * Payment mock service for DEV mode.
 * Simulates payment confirmations for testing without real payment gateway.
 */

export interface PaymentIntent {
  /** Unique payment identifier */
  id: string;
  /** Amount in kopecks (smallest currency unit) */
  amount: number;
  /** Currency code (e.g., 'RUB') */
  currency: string;
  /** Payment status */
  status: 'pending' | 'confirmed' | 'failed' | 'cancelled';
  /** QR code URL for payment */
  qrCodeUrl?: string;
  /** Session ID associated with payment */
  sessionId: string;
  /** Creation timestamp */
  createdAt: Date;
}

export interface PaymentMockService {
  /** Create a mock payment intent */
  createIntent(amount: number, sessionId: string): Promise<PaymentIntent>;
  /** Get payment status */
  getStatus(intentId: string): Promise<PaymentIntent>;
  /** Simulate payment confirmation (DEV only) */
  simulateConfirmation(intentId: string): Promise<PaymentIntent>;
  /** Simulate payment failure (DEV only) */
  simulateFailure(intentId: string): Promise<PaymentIntent>;
}

/**
 * In-memory mock implementation of PaymentMockService for development.
 */
class InMemoryPaymentMockService implements PaymentMockService {
  private intents: Map<string, PaymentIntent> = new Map();

  async createIntent(amount: number, sessionId: string): Promise<PaymentIntent> {
    const id = `mock_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    const intent: PaymentIntent = {
      id,
      amount,
      currency: 'RUB',
      status: 'pending',
      qrCodeUrl: `https://example.com/qr/${id}`,
      sessionId,
      createdAt: new Date(),
    };
    this.intents.set(id, intent);
    return intent;
  }

  async getStatus(intentId: string): Promise<PaymentIntent> {
    const intent = this.intents.get(intentId);
    if (!intent) {
      throw new Error(`Payment intent not found: ${intentId}`);
    }
    return intent;
  }

  async simulateConfirmation(intentId: string): Promise<PaymentIntent> {
    const intent = this.intents.get(intentId);
    if (!intent) {
      throw new Error(`Payment intent not found: ${intentId}`);
    }
    intent.status = 'confirmed';
    return intent;
  }

  async simulateFailure(intentId: string): Promise<PaymentIntent> {
    const intent = this.intents.get(intentId);
    if (!intent) {
      throw new Error(`Payment intent not found: ${intentId}`);
    }
    intent.status = 'failed';
    return intent;
  }
}

/**
 * Factory function for creating payment mock service.
 * Returns in-memory implementation for DEV mode testing.
 */
export function createPaymentMockService(): PaymentMockService {
  return new InMemoryPaymentMockService();
}
