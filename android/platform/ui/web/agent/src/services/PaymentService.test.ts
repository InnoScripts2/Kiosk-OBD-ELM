import { PaymentService } from '../services/PaymentService';

describe('PaymentService', () => {
  let service: PaymentService;

  beforeEach(() => {
    service = new PaymentService(true); // mock mode
  });

  test('создаёт платёжный интент с корректными данными', async () => {
    const intent = await service.createPaymentIntent(350, 'session_123');

    expect(intent.amount).toBe(350);
    expect(intent.sessionId).toBe('session_123');
    expect(intent.status).toBe('pending');
    expect(intent.intentId).toMatch(/^intent_/);
    expect(intent.qrCode).toBeDefined();
  });

  test('получает статус существующего интента', async () => {
    const created = await service.createPaymentIntent(400, 'session_456');
    const retrieved = await service.getStatus(created.intentId);

    expect(retrieved).toBeDefined();
    expect(retrieved?.intentId).toBe(created.intentId);
    expect(retrieved?.amount).toBe(400);
  });

  test('возвращает null для несуществующего интента', async () => {
    const retrieved = await service.getStatus('non_existent_intent');
    expect(retrieved).toBeNull();
  });

  test('подтверждает платёж в mock режиме', async () => {
    const intent = await service.createPaymentIntent(480, 'session_789');
    const confirmation = await service.confirmPayment(intent.intentId);

    expect(confirmation.confirmed).toBe(true);
    expect(confirmation.intentId).toBe(intent.intentId);

    const updated = await service.getStatus(intent.intentId);
    expect(updated?.status).toBe('confirmed');
  });

  test('выбрасывает ошибку при попытке подтверждения несуществующего интента', async () => {
    await expect(service.confirmPayment('non_existent')).rejects.toThrow('not found');
  });
});
