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
 * Factory function for creating payment mock service.
 * Only for DEV mode; production uses real payment gateway.
 */
export function createPaymentMockService(): PaymentMockService {
  throw new Error('PaymentMockService not implemented.');
}
