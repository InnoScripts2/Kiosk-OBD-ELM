import express from 'express';
import request from 'supertest';
import { createLocksRouter } from './locks.js';
import type { DeviceType, LockStatus } from '../services/LockController.js';

class MockLockController {
  private status: LockStatus = {
    thickness: 'closed',
    adapter: 'closed',
    connected: true,
  };

  async getStatus() {
    return { ...this.status };
  }

  async openSlot(device: DeviceType) {
    this.status = { ...this.status, [device]: 'open' };
  }

  async closeSlot(device: DeviceType) {
    this.status = { ...this.status, [device]: 'closed' };
  }
}

function createApp() {
  const controller = new MockLockController();
  const app = express();
  app.use(express.json());
  app.use('/locks', createLocksRouter(controller));
  return app;
}

describe('locks routes', () => {
  test('returns current lock status', async () => {
    const app = createApp();
    const response = await request(app).get('/locks/status').expect(200);
    expect(response.body.status).toMatchObject({
      thickness: 'closed',
      adapter: 'closed',
      connected: true,
      error: null,
    });
  });

  test('opens and closes slots by device type', async () => {
    const app = createApp();

    await request(app)
      .post('/locks/thickness/open')
      .expect(200)
      .expect(res => {
        expect(res.body.status.thickness).toBe('open');
      });

    await request(app)
      .post('/locks/thickness/close')
      .expect(200)
      .expect(res => {
        expect(res.body.status.thickness).toBe('closed');
      });
  });

  test('rejects unknown device identifiers', async () => {
    const app = createApp();
    await request(app)
      .post('/locks/unknown/open')
      .expect(400, { error: 'invalid_device' });
  });
});