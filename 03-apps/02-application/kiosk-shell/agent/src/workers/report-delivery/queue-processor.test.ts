import { describe, expect, jest, test } from '@jest/globals';
import type { SupabaseClient } from '@supabase/supabase-js';
import type { Database, Json } from '../../integrations/supabase/types.js';
import type { ReportService } from '../../services/ReportService.js';
import type { ReportDeliveryWorkerConfig } from './config.js';
import {
  ReportDeliveryQueueProcessor,
  createQueueDefinitions,
  type DiagnosticsDeliveryRow,
} from './queue-processor.js';

type ReportServiceSlice = Pick<ReportService, 'sendEmail' | 'sendSMS'>;

interface ReportServiceMock extends ReportServiceSlice {
  sendEmail: jest.MockedFunction<ReportServiceSlice['sendEmail']>;
  sendSMS: jest.MockedFunction<ReportServiceSlice['sendSMS']>;
}

interface ProcessorFixture {
  processor: ReportDeliveryQueueProcessor;
  config: ReportDeliveryWorkerConfig;
  reportService: ReportServiceMock;
}

type CollectCandidatesFn = () => Promise<DiagnosticsDeliveryRow[]>;
type FetchDeliveriesFn = (status: 'queued' | 'processing', limit: number) => Promise<DiagnosticsDeliveryRow[]>;
type FetchStaleFn = (limit: number) => Promise<DiagnosticsDeliveryRow[]>;
type ClaimDeliveryFn = (
  delivery: DiagnosticsDeliveryRow,
  mode: 'queued' | 'stale-processing',
) => Promise<number | null>;
type LoadReportFn = (
  reportId: string,
) => Promise<{ report_id: string; session_id: string; report_html: string; metadata: unknown } | null>;
type MarkSentFn = (deliveryId: string) => Promise<void>;
type MarkFailureFn = (
  deliveryId: string,
  attempts: number,
  message: string,
  shouldRetry: boolean,
) => Promise<void>;
type UpdateIngestStateFn = (ingestId: string, patch: Record<string, unknown>) => Promise<void>;

type ProcessorInternals = {
  collectCandidates(): Promise<DiagnosticsDeliveryRow[]>;
  fetchDeliveries: FetchDeliveriesFn;
  fetchStaleProcessingDeliveries: FetchStaleFn;
  isStaleProcessing(delivery: DiagnosticsDeliveryRow, boundary: number): boolean;
  claimDelivery: ClaimDeliveryFn;
  loadReport: LoadReportFn;
  markSent: MarkSentFn;
  markFailure: MarkFailureFn;
  updateIngestState: UpdateIngestStateFn;
};

function createReportServiceMock(overrides?: Partial<ReportServiceMock>): ReportServiceMock {
  const defaultSendEmail = jest
    .fn<ReportServiceSlice['sendEmail']>()
    .mockResolvedValue({ success: true, messageId: 'email-1' });
  const defaultSendSMS = jest
    .fn<ReportServiceSlice['sendSMS']>()
    .mockResolvedValue({ success: true });

  return {
    sendEmail: defaultSendEmail,
    sendSMS: defaultSendSMS,
    ...overrides,
  } as ReportServiceMock;
}

function createProcessorFixture(
  overrides?: Partial<{
    config: Partial<ReportDeliveryWorkerConfig>;
    reportService: Partial<ReportServiceMock>;
  }>,
): ProcessorFixture {
  const config: ReportDeliveryWorkerConfig = {
    supabaseUrl: 'https://example.supabase.co',
    supabaseServiceRoleKey: 'service-role-key',
    environment: 'dev',
    kioskId: 'KIOSK-001',
    pollIntervalMs: 1_000,
    activePollIntervalMs: 200,
    maxBatchSize: 5,
    staleProcessingThresholdMs: 60_000,
    maxAttempts: 3,
    ...overrides?.config,
  };

  const reportService = createReportServiceMock(overrides?.reportService);

  const processor = new ReportDeliveryQueueProcessor({
    supabase: {} as SupabaseClient<Database>,
    reportService: reportService as unknown as ReportService,
    config,
    definition: createQueueDefinitions()[0],
  });

  return { processor, config, reportService };
}

function createDelivery(overrides: Partial<DiagnosticsDeliveryRow> = {}): DiagnosticsDeliveryRow {
  return {
    attempts: overrides.attempts ?? 0,
    channel: overrides.channel ?? 'email',
    created_at: overrides.created_at ?? new Date().toISOString(),
    delivery_id: overrides.delivery_id ?? `delivery_${Math.random().toString(36).slice(2)}`,
    dispatched_at: overrides.dispatched_at ?? null,
    environment: overrides.environment ?? 'dev',
    generated_at_ms: overrides.generated_at_ms ?? Date.now(),
    kiosk_id: overrides.kiosk_id ?? 'KIOSK-001',
    last_attempt_at: overrides.last_attempt_at ?? null,
    last_error: overrides.last_error ?? null,
    metadata: overrides.metadata ?? null,
    recipient: overrides.recipient ?? 'client@example.com',
    report_id: overrides.report_id ?? 'report-123',
    session_id: overrides.session_id ?? 'session-456',
    status: overrides.status ?? 'queued',
    updated_at: overrides.updated_at ?? new Date().toISOString(),
  };
}

describe('ReportDeliveryQueueProcessor', () => {
  test('collectCandidates mixes queued rows with stale processing when capacity available', async () => {
    const { processor, config } = createProcessorFixture();
    const internals = processor as unknown as ProcessorInternals;
    const queued = createDelivery({ delivery_id: 'queued-1' });
    const staleProcessing = createDelivery({ delivery_id: 'stale-1', status: 'processing' });

    internals.fetchDeliveries = jest.fn<FetchDeliveriesFn>().mockResolvedValue([queued]);
    internals.fetchStaleProcessingDeliveries = jest.fn<FetchStaleFn>().mockResolvedValue([staleProcessing]);

    const candidates = await internals.collectCandidates();

    expect(candidates).toEqual([queued, staleProcessing]);
    expect(internals.fetchDeliveries).toHaveBeenCalledWith('queued', config.maxBatchSize);
    expect(internals.fetchStaleProcessingDeliveries).toHaveBeenCalledWith(config.maxBatchSize - 1);
  });

  test('isStaleProcessing respects last_attempt_at boundary', () => {
    const { processor, config } = createProcessorFixture();
    const internals = processor as unknown as ProcessorInternals;
    const boundary = Date.now() - config.staleProcessingThresholdMs;
    const stale = createDelivery({ last_attempt_at: new Date(boundary - 1_000).toISOString(), status: 'processing' });
    const fresh = createDelivery({ last_attempt_at: new Date(boundary + 1_000).toISOString(), status: 'processing' });

    expect(internals.isStaleProcessing(stale, boundary)).toBe(true);
    expect(internals.isStaleProcessing(fresh, boundary)).toBe(false);
  });

  test('processBatch dispatches email delivery and marks it as sent', async () => {
    const { processor, reportService } = createProcessorFixture();
    const internals = processor as unknown as ProcessorInternals;
    const delivery = createDelivery({ channel: 'email', metadata: { ingest_id: 'ing-1' } as Json });

    internals.collectCandidates = jest.fn<CollectCandidatesFn>().mockResolvedValue([delivery]);
    internals.claimDelivery = jest.fn<ClaimDeliveryFn>().mockResolvedValue(1);
    internals.loadReport = jest.fn<LoadReportFn>().mockResolvedValue({
      report_id: delivery.report_id,
      session_id: delivery.session_id,
      report_html: '<html>report</html>',
      metadata: null,
    });
    internals.markSent = jest.fn<MarkSentFn>().mockResolvedValue();
    internals.markFailure = jest.fn<MarkFailureFn>();
    internals.updateIngestState = jest.fn<UpdateIngestStateFn>().mockResolvedValue();

    const processed = await processor.processBatch();

    expect(processed).toBe(1);
    expect(reportService.sendEmail).toHaveBeenCalledWith(
      delivery.recipient,
      '<html>report</html>',
      delivery.session_id,
      expect.objectContaining({ subject: expect.stringContaining(delivery.session_id) }),
    );
    expect(internals.markSent).toHaveBeenCalledWith(delivery.delivery_id);
    expect(internals.markFailure).not.toHaveBeenCalled();
    expect(internals.updateIngestState).toHaveBeenCalledTimes(2);
    expect(internals.updateIngestState).toHaveBeenNthCalledWith(
      1,
      'ing-1',
      expect.objectContaining({ status: 'processing', lastError: null }),
    );
    expect(internals.updateIngestState).toHaveBeenNthCalledWith(
      2,
      'ing-1',
      expect.objectContaining({ status: 'completed', lastError: null }),
    );
  });

  test('processBatch marks failure without retry when attempts reached max', async () => {
    const sendEmailError = new Error('smtp down');
    const { processor, config, reportService } = createProcessorFixture({
      reportService: {
        sendEmail: jest.fn<ReportServiceSlice['sendEmail']>().mockRejectedValue(sendEmailError),
      },
    });
    const internals = processor as unknown as ProcessorInternals;
    const delivery = createDelivery({ channel: 'email', metadata: { ingestId: 'ing-2' } as Json });

    internals.collectCandidates = jest.fn<CollectCandidatesFn>().mockResolvedValue([delivery]);
    internals.claimDelivery = jest.fn<ClaimDeliveryFn>().mockResolvedValue(config.maxAttempts);
    internals.loadReport = jest.fn<LoadReportFn>().mockResolvedValue({
      report_id: delivery.report_id,
      session_id: delivery.session_id,
      report_html: '<html/>',
      metadata: null,
    });
    internals.markSent = jest.fn<MarkSentFn>();
    internals.markFailure = jest.fn<MarkFailureFn>().mockResolvedValue();
    internals.updateIngestState = jest.fn<UpdateIngestStateFn>().mockResolvedValue();

    const processed = await processor.processBatch();

    expect(processed).toBe(0);
    expect(reportService.sendEmail).toHaveBeenCalled();
    expect(internals.markSent).not.toHaveBeenCalled();
    expect(internals.markFailure).toHaveBeenCalledWith(
      delivery.delivery_id,
      config.maxAttempts,
      'smtp down',
      false,
    );
    expect(internals.updateIngestState).toHaveBeenCalledTimes(2);
    expect(internals.updateIngestState).toHaveBeenNthCalledWith(
      1,
      'ing-2',
      expect.objectContaining({ status: 'processing', lastError: null }),
    );
    expect(internals.updateIngestState).toHaveBeenNthCalledWith(
      2,
      'ing-2',
      expect.objectContaining({ status: 'failed', lastError: 'smtp down' }),
    );
  });
});
