import { describe, expect, jest, test } from '@jest/globals';
import express from 'express';
import request from 'supertest';
import { createReportsRouter } from './reports.js';
import { ReportIngestValidationError, type ReportIngestService } from '../services/ReportIngestService.js';

describe('reports routes', () => {
  const createApp = (service: ReportIngestService): express.Application => {
    const app = express();
    app.use(express.json());
    app.use('/reports', createReportsRouter(service));
    return app;
  };

  test('returns 202 when intake succeeds', async () => {
    const intakeResult = {
      ingestId: 'ingest-1',
      status: 'received' as const,
      receivedAt: new Date().toISOString(),
      slaDeadlineMs: Date.now() + 60000,
    };
    const service = {
      intake: jest.fn<ReportIngestService['intake']>().mockResolvedValue(intakeResult),
      updateState: jest.fn<ReportIngestService['updateState']>().mockResolvedValue(undefined),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .post('/reports/intake')
      .send({
        type: 'diagnostics',
        sessionId: 'session-test',
        reportHtml: '<html/>',
        reportPdfBase64: 'base64',
      })
      .expect(202);

    expect(response.body.ingest).toEqual(intakeResult);
    expect(service.intake).toHaveBeenCalledTimes(1);
  });

  test('returns 400 for validation errors', async () => {
    const service = {
      intake: jest
        .fn<ReportIngestService['intake']>()
        .mockRejectedValue(new ReportIngestValidationError('invalid_payload')),
      updateState: jest.fn<ReportIngestService['updateState']>().mockResolvedValue(undefined),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .post('/reports/intake')
      .send({})
      .expect(400);

    expect(response.body).toMatchObject({ error: 'invalid_payload' });
  });

  test('returns 502 for unexpected errors', async () => {
    const service = {
      intake: jest
        .fn<ReportIngestService['intake']>()
        .mockRejectedValue(new Error('supabase down')),
      updateState: jest.fn<ReportIngestService['updateState']>().mockResolvedValue(undefined),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .post('/reports/intake')
      .send({})
      .expect(502);

    expect(response.body).toEqual({ error: 'report_ingest_failed' });
  });

  test('PUT /reports/:ingestId/state returns 202 on success', async () => {
    const service = {
      intake: jest.fn<ReportIngestService['intake']>().mockResolvedValue(undefined as never),
      updateState: jest.fn<ReportIngestService['updateState']>().mockResolvedValue(undefined),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .put('/reports/ingest-123/state')
      .send({ status: 'processing' })
      .expect(202);

    expect(response.body).toEqual({ status: 'accepted' });
    expect(service.updateState).toHaveBeenCalledWith('ingest-123', { status: 'processing' });
  });

  test('PUT /reports/:ingestId/state returns 400 for validation errors', async () => {
    const service = {
      intake: jest.fn<ReportIngestService['intake']>().mockResolvedValue(undefined as never),
      updateState: jest
        .fn<ReportIngestService['updateState']>()
        .mockRejectedValue(new ReportIngestValidationError('bad patch')),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .put('/reports/ingest-999/state')
      .send({})
      .expect(400);

    expect(response.body).toMatchObject({ error: 'invalid_state_patch' });
  });

  test('PUT /reports/:ingestId/state returns 502 for unexpected errors', async () => {
    const service = {
      intake: jest.fn<ReportIngestService['intake']>().mockResolvedValue(undefined as never),
      updateState: jest
        .fn<ReportIngestService['updateState']>()
        .mockRejectedValue(new Error('supabase offline')),
    } as unknown as ReportIngestService;

    const app = createApp(service);

    const response = await request(app)
      .put('/reports/ingest-999/state')
      .send({ status: 'processing' })
      .expect(502);

    expect(response.body).toEqual({ error: 'report_state_update_failed' });
  });
});
