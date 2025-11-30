import { afterEach, describe, expect, jest, test } from '@jest/globals';
import type { SupabaseAgentClient } from '../integrations/supabase/agent-client.js';
import {
  ReportIngestService,
  ReportIngestValidationError,
  type ReportIntakePayload,
} from './ReportIngestService.js';

const createSupabaseMock = (): SupabaseAgentClient => {
  return {
    enqueueReportIngest: jest
      .fn<SupabaseAgentClient['enqueueReportIngest']>()
      .mockResolvedValue({ ingestId: 'ingest-test' }),
    recordMetric: jest.fn<SupabaseAgentClient['recordMetric']>().mockResolvedValue(undefined),
    updateReportIngestState: jest
      .fn<SupabaseAgentClient['updateReportIngestState']>()
      .mockResolvedValue(undefined),
  } as unknown as SupabaseAgentClient;
};

describe('ReportIngestService', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('intake enqueues payload and records metric', async () => {
    const supabase = createSupabaseMock();
    const service = new ReportIngestService({
      supabase,
      environment: 'dev',
      generationTimeoutMs: 5000,
      deliverySlaMs: 60000,
      kioskId: 'KIOSK-42',
    });
    const now = 1_735_000_000_000;
    jest.spyOn(Date, 'now').mockReturnValue(now);

    const payload: ReportIntakePayload = {
      type: 'diagnostics',
      sessionId: 'session-123',
      reportHtml: '<html>report</html>',
      reportPdfBase64: 'base64',
      metadata: { foo: 'bar' },
      contact: { email: 'client@example.com', phone: '+79998887766' },
    };

    const result = await service.intake(payload);

    expect(result).toEqual({
      ingestId: 'ingest-test',
      status: 'received',
      receivedAt: new Date(now).toISOString(),
      slaDeadlineMs: now + 60000,
    });

    expect(supabase.enqueueReportIngest).toHaveBeenCalledTimes(1);
    expect(supabase.enqueueReportIngest).toHaveBeenCalledWith(
      expect.objectContaining({
        sessionId: 'session-123',
        reportType: 'diagnostics',
        kioskId: 'KIOSK-42',
        payload: expect.objectContaining({
          report_html: '<html>report</html>',
          report_pdf_base64: 'base64',
        }),
      })
    );

    expect(supabase.recordMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricType: 'report_intake',
        valueText: 'diagnostics',
      })
    );
  });

  test('intake rejects invalid payloads with validation error', async () => {
    const supabase = createSupabaseMock();
    const service = new ReportIngestService({
      supabase,
      environment: 'dev',
      generationTimeoutMs: 5000,
      deliverySlaMs: 60000,
    });

    await expect(
      service.intake({
        type: 'diagnostics',
        sessionId: '',
        reportHtml: '<html></html>',
        reportPdfBase64: 'pdf',
      })
    ).rejects.toBeInstanceOf(ReportIngestValidationError);
  });

  test('updateState normalizes payload and forwards to supabase', async () => {
    const supabase = createSupabaseMock();
    const service = new ReportIngestService({
      supabase,
      environment: 'prod',
      generationTimeoutMs: 1000,
      deliverySlaMs: 2000,
    });

    await service.updateState('  ingest-123  ', {
      status: 'processing',
      lastError: '  temporary issue  ',
      slaDeadlineMs: 98765,
      retries: 2,
    });

    expect(supabase.updateReportIngestState).toHaveBeenCalledWith('ingest-123', {
      status: 'processing',
      lastError: 'temporary issue',
      slaDeadlineMs: 98765,
      retries: 2,
    });
    expect(supabase.recordMetric).toHaveBeenCalledTimes(1);
    expect(supabase.recordMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        metricType: 'agent_heartbeat',
        valueText: 'processing',
        payload: expect.objectContaining({
          component: 'report_ingest_state',
          ingestId: 'ingest-123',
          status: 'processing',
          retries: 2,
        }),
      })
    );
  });

  test('updateState rejects invalid or empty patches', async () => {
    const supabase = createSupabaseMock();
    const service = new ReportIngestService({
      supabase,
      environment: 'prod',
      generationTimeoutMs: 1000,
      deliverySlaMs: 2000,
    });

    await expect(service.updateState('', {})).rejects.toBeInstanceOf(ReportIngestValidationError);
    await expect(
      service.updateState('ingest-321', { status: 'unknown-status' })
    ).rejects.toBeInstanceOf(ReportIngestValidationError);
    expect(supabase.recordMetric).not.toHaveBeenCalled();
  });
});
