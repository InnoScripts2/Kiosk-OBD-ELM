import express from 'express';
import request from 'supertest';
import { createPaymentsRouter } from './payments.js';
import { PaymentService } from '../services/PaymentService.js';

function createApp(service: PaymentService) {
  const app = express();
  app.use(express.json());
  app.use('/payments', createPaymentsRouter(service));
  return app;
}

describe('payments routes', () => {
  test('creates payment intents and returns serialized payload', async () => {
    const service = new PaymentService(true);
    const app = createApp(service);

    const response = await request(app)
      .post('/payments/intent')
      .send({ amount: 350, sessionId: 'session-test' })
      .expect(201);

    expect(response.body.intent).toMatchObject({
      amount: 350,
      sessionId: 'session-test',
      status: 'pending',
    });
    expect(response.body.intent.intentId).toMatch(/^intent_/);
  });

  test('rejects invalid amount payloads', async () => {
    const service = new PaymentService(true);
    const app = createApp(service);

    await request(app)
      .post('/payments/intent')
      .send({ amount: -10 })
      .expect(400, { error: 'invalid_amount' });
  });

  test('returns stored status for known intents', async () => {
    const service = new PaymentService(true);
    const app = createApp(service);
    const intent = await service.createPaymentIntent(480, 'session-123');

    const response = await request(app)
      .get(`/payments/${intent.intentId}/status`)
      .expect(200);

    expect(response.body.intent).toMatchObject({
      intentId: intent.intentId,
      amount: 480,
      status: 'pending',
    });
  });

  test('returns 404 for unknown intents', async () => {
    const service = new PaymentService(true);
    const app = createApp(service);

    await request(app)
      .get('/payments/unknown/status')
      .expect(404, { error: 'payment_intent_not_found' });
  });

  test('confirms payments only in mock mode', async () => {
    const mockService = new PaymentService(true);
    const mockApp = createApp(mockService);
    const intent = await mockService.createPaymentIntent(500, 'session-mock');

    const confirmResponse = await request(mockApp)
      .post('/payments/confirm-dev')
      .send({ id: intent.intentId })
      .expect(200);

    expect(confirmResponse.body.confirmation).toMatchObject({
      intentId: intent.intentId,
      confirmed: true,
    });

    const prodService = new PaymentService(false);
    const prodApp = createApp(prodService);

    await request(prodApp)
      .post('/payments/confirm-dev')
      .send({ id: 'any' })
      .expect(400, { error: 'confirmation_not_available' });
  });
});