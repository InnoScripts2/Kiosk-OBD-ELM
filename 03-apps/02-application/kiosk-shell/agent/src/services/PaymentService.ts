/**
 * PaymentService - симулятор оплаты для DEV режима
 * В production будет интегрирован с реальным PSP (Yandex.Kassa, Stripe)
 */

export interface PaymentIntent {
  intentId: string;
  amount: number;
  sessionId: string;
  status: 'pending' | 'confirmed' | 'failed';
  qrCode?: string;
  createdAt: Date;
}

export interface PaymentConfirmation {
  intentId: string;
  confirmed: boolean;
  timestamp: Date;
}

export class PaymentService {
  private intents: Map<string, PaymentIntent> = new Map();
  private readonly mockMode: boolean;

  constructor(mockMode: boolean = true) {
    this.mockMode = mockMode;
  }

  isMockMode(): boolean {
    return this.mockMode;
  }

  /**
   * Создать платёжный интент
   */
  async createPaymentIntent(amount: number, sessionId: string): Promise<PaymentIntent> {
    const intentId = `intent_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    const intent: PaymentIntent = {
      intentId,
      amount,
      sessionId,
      status: 'pending',
      qrCode: this.mockMode ? this.generateMockQR(intentId, amount) : undefined,
      createdAt: new Date(),
    };

    this.intents.set(intentId, intent);
    
    if (this.mockMode) {
      console.log(`[PAYMENT-MOCK] Created intent ${intentId} for ${amount}₽, session ${sessionId}`);
    }

    return intent;
  }

  /**
   * Получить статус платежа
   */
  async getStatus(intentId: string): Promise<PaymentIntent | null> {
    return this.intents.get(intentId) || null;
  }

  /**
   * Подтвердить платёж (только для DEV/mock режима)
   */
  async confirmPayment(intentId: string): Promise<PaymentConfirmation> {
    const intent = this.intents.get(intentId);
    
    if (!intent) {
      throw new Error(`Intent ${intentId} not found`);
    }

    if (!this.mockMode) {
      throw new Error('Manual confirmation is only available in mock mode');
    }

    // Имитация задержки обработки
    await new Promise(resolve => setTimeout(resolve, 1000));

    intent.status = 'confirmed';
    this.intents.set(intentId, intent);

    console.log(`[PAYMENT-MOCK] Confirmed intent ${intentId}`);

    return {
      intentId,
      confirmed: true,
      timestamp: new Date(),
    };
  }

  /**
   * Обработать webhook от платёжного провайдера (для production)
   */
  async handleWebhook(payload: Record<string, unknown>): Promise<void> {
    if (this.mockMode) {
      console.warn('[PAYMENT-MOCK] Webhook received in mock mode, ignoring');
      return;
    }

    // TODO: Реализовать обработку webhook от реального PSP
    console.log('[PAYMENT] Webhook received:', payload);
  }

  private generateMockQR(intentId: string, amount: number): string {
    // Генерация простого QR payload (в production будет от PSP)
    const payload = {
      type: 'payment',
      intentId,
      amount,
      currency: 'RUB',
    };
    return Buffer.from(JSON.stringify(payload)).toString('base64');
  }
}
