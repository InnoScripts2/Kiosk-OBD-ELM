import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import request from 'supertest';
import { createAgentApp } from './app.js';
import type { PaymentService } from './services/PaymentService.js';
import type { LockController } from './services/LockController.js';
import type { ReportService } from './services/ReportService.js';
import type { ReportIngestService } from './services/ReportIngestService.js';

describe('createAgentApp static hosting', () => {
  let tempDir: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'agent-static-ui-'));
    fs.mkdirSync(path.join(tempDir, 'assets'), { recursive: true });
    fs.writeFileSync(path.join(tempDir, 'index.html'), '<!doctype html><html><body>kiosk</body></html>');
    fs.writeFileSync(path.join(tempDir, 'assets', 'main-ABCD1234.js'), 'console.log("kiosk")');
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  it('serves index file via /ui route with no-cache headers', async () => {
    const app = createTestAgent(tempDir);

    const response = await request(app).get('/ui/');

    expect(response.status).toBe(200);
    expect(response.text).toContain('kiosk');
    expect(response.headers['cache-control']).toMatch(/no-cache/);
  });

  it('marks hashed assets as immutable', async () => {
    const app = createTestAgent(tempDir);

    const response = await request(app).get('/ui/assets/main-ABCD1234.js');

    expect(response.status).toBe(200);
    expect(response.headers['cache-control']).toBe('public, max-age=31536000, immutable');
  });
});

function createTestAgent(rootDir: string) {
  const paymentService = {} as unknown as PaymentService;
  const lockController = {} as unknown as LockController;
  const reportService = {} as unknown as ReportService;
  const reportIngestService = {} as unknown as ReportIngestService;

  return createAgentApp({
    paymentService,
    lockController,
    reportService,
    reportIngestService,
    staticUi: {
      rootDir,
      routePrefix: '/ui',
      indexFile: 'index.html',
    },
  });
}
