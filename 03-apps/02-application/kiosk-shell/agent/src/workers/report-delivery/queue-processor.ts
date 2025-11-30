import { SupabaseClient, type PostgrestError } from '@supabase/supabase-js';
import type { Database } from '../../integrations/supabase/types.js';
import type { ReportIngestStatus } from '../../integrations/supabase/agent-client.js';
import { ReportService } from '../../services/ReportService.js';
import type { ReportDeliveryWorkerConfig, ReportMetadata } from './config.js';
import {
  composeDiagnosticsEmailSubject,
  composeDiagnosticsSmsBody,
  composeThicknessEmailSubject,
  composeThicknessSmsBody
} from './message-builder.js';

const STATUS_QUEUED = 'queued';
const STATUS_PROCESSING = 'processing';
const STATUS_SENT = 'sent';
const STATUS_FAILED = 'failed';

const CHANNEL_EMAIL = 'email';
const CHANNEL_SMS = 'sms';

export type DiagnosticsDeliveryRow = Database['public']['Tables']['diagnostics_report_deliveries']['Row'];
export type ThicknessDeliveryRow = Database['public']['Tables']['thickness_report_deliveries']['Row'];
type DeliveryRow = DiagnosticsDeliveryRow | ThicknessDeliveryRow;

export type DiagnosticsReportRow = Database['public']['Tables']['diagnostics_reports']['Row'];
export type ThicknessReportRow = Database['public']['Tables']['thickness_reports']['Row'];
type ReportRow = DiagnosticsReportRow | ThicknessReportRow;
type ReportPayloadRow = Pick<ReportRow, 'report_id' | 'session_id' | 'report_html' | 'metadata'>;

type ClaimMode = 'queued' | 'stale-processing';

interface IngestStatePatch {
  readonly status?: ReportIngestStatus;
  readonly lastError?: string | null;
  readonly retries?: number;
}

export interface QueueDefinition {
  readonly name: 'diagnostics' | 'thickness';
  readonly deliveryTable: 'diagnostics_report_deliveries' | 'thickness_report_deliveries';
  readonly reportTable: 'diagnostics_reports' | 'thickness_reports';
  readonly composeEmailSubject: (sessionId: string, metadata: ReportMetadata) => string;
  readonly composeSmsBody: (sessionId: string, metadata: ReportMetadata) => string;
}

export interface QueueProcessorDeps {
  readonly supabase: SupabaseClient<Database>;
  readonly reportService: ReportService;
  readonly config: ReportDeliveryWorkerConfig;
  readonly definition: QueueDefinition;
}

export class ReportDeliveryQueueProcessor {
  private readonly supabase: SupabaseClient<Database>;
  private readonly reportService: ReportService;
  private readonly config: ReportDeliveryWorkerConfig;
  private readonly definition: QueueDefinition;

  constructor({ supabase, reportService, config, definition }: QueueProcessorDeps) {
    this.supabase = supabase;
    this.reportService = reportService;
    this.config = config;
    this.definition = definition;
  }

  get name(): string {
    return this.definition.name;
  }

  async processBatch(): Promise<number> {
    const candidates = await this.collectCandidates();
    if (!candidates.length) {
      return 0;
    }
    let processed = 0;
    for (const candidate of candidates) {
      const claimMode: ClaimMode = candidate.status === STATUS_PROCESSING ? 'stale-processing' : 'queued';
      const attempts = await this.claimDelivery(candidate, claimMode);
      if (!attempts) {
        continue;
      }
      const ingestId = this.extractIngestId(candidate.metadata ?? null);
      if (ingestId) {
        await this.updateIngestState(ingestId, {
          status: 'processing',
          lastError: null,
          retries: attempts,
        });
      }
      const report = await this.loadReport(candidate.report_id);
      if (!report) {
        await this.markFailure(candidate.delivery_id, attempts, 'Report payload not found', true);
        if (ingestId) {
          await this.updateIngestState(ingestId, {
            status: 'failed',
            lastError: 'Report payload not found',
            retries: attempts,
          });
        }
        continue;
      }
      const metadata = (report.metadata ?? null) as ReportMetadata;
      try {
        if (candidate.channel === CHANNEL_EMAIL) {
          const subject = this.definition.composeEmailSubject(report.session_id, metadata);
          await this.reportService.sendEmail(candidate.recipient, report.report_html, report.session_id, { subject });
        } else if (candidate.channel === CHANNEL_SMS) {
          const smsBody = this.definition.composeSmsBody(report.session_id, metadata);
          await this.reportService.sendSMS(candidate.recipient, smsBody);
        } else {
          throw new Error(`Unsupported delivery channel: ${candidate.channel}`);
        }
        await this.markSent(candidate.delivery_id);
        processed += 1;
        if (ingestId) {
          await this.updateIngestState(ingestId, {
            status: 'completed',
            lastError: null,
            retries: attempts,
          });
        }
      } catch (error) {
        const message = normalizeError(error);
        const shouldRetry = attempts < this.config.maxAttempts;
        await this.markFailure(candidate.delivery_id, attempts, message, shouldRetry);
        if (ingestId) {
          await this.updateIngestState(ingestId, {
            status: shouldRetry ? 'processing' : 'failed',
            lastError: message,
            retries: attempts,
          });
        }
      }
    }
    return processed;
  }

  private async collectCandidates(): Promise<DeliveryRow[]> {
    const queued = await this.fetchDeliveries(STATUS_QUEUED, this.config.maxBatchSize);
    if (queued.length >= this.config.maxBatchSize) {
      return queued;
    }
    const remaining = this.config.maxBatchSize - queued.length;
    const stale = await this.fetchStaleProcessingDeliveries(remaining);
    return queued.concat(stale);
  }

  private async fetchDeliveries(status: typeof STATUS_QUEUED | typeof STATUS_PROCESSING, limit: number): Promise<DeliveryRow[]> {
    if (limit <= 0) {
      return [];
    }
    let query = this.supabase
      .from(this.definition.deliveryTable)
      .select('*')
      .eq('environment', this.config.environment)
      .order('generated_at_ms', { ascending: true })
      .limit(limit);

    if (this.config.kioskId) {
      query = query.eq('kiosk_id', this.config.kioskId);
    }
    query = query.eq('status', status);

    const { data, error } = await query;
    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to fetch ${status} deliveries`, error);
      return [];
    }
    return data ?? [];
  }

  private async fetchStaleProcessingDeliveries(limit: number): Promise<DeliveryRow[]> {
    if (limit <= 0) {
      return [];
    }
    const fetchLimit = limit * 2;
    const { data, error } = await this.fetchProcessingRaw(fetchLimit);
    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to fetch processing deliveries`, error);
      return [];
    }
    const staleBoundary = Date.now() - this.config.staleProcessingThresholdMs;
    const stale = (data ?? []).filter((delivery) => this.isStaleProcessing(delivery, staleBoundary));
    return stale.slice(0, limit);
  }

  private async fetchProcessingRaw(
    limit: number
  ): Promise<{ data: DeliveryRow[] | null; error: PostgrestError | null }> {
    let query = this.supabase
      .from(this.definition.deliveryTable)
      .select('*')
      .eq('environment', this.config.environment)
      .eq('status', STATUS_PROCESSING)
      .order('updated_at', { ascending: true })
      .limit(limit);

    if (this.config.kioskId) {
      query = query.eq('kiosk_id', this.config.kioskId);
    }
    const { data, error } = await query;
    return { data: data ?? null, error };
  }

  private isStaleProcessing(delivery: DeliveryRow, staleBoundaryMillis: number): boolean {
    const lastAttemptMillis = delivery.last_attempt_at ? Date.parse(delivery.last_attempt_at) : undefined;
    if (!lastAttemptMillis || Number.isNaN(lastAttemptMillis)) {
      return true;
    }
    return lastAttemptMillis <= staleBoundaryMillis;
  }

  private async claimDelivery(delivery: DeliveryRow, mode: ClaimMode): Promise<number | null> {
    const nextAttempts = (delivery.attempts ?? 0) + 1;
    const nowIso = new Date().toISOString();

    let query = this.supabase
      .from(this.definition.deliveryTable)
      .update({
        status: STATUS_PROCESSING,
        last_attempt_at: nowIso,
        attempts: nextAttempts,
        updated_at: nowIso,
        last_error: null
      })
      .eq('delivery_id', delivery.delivery_id)
      .select('delivery_id')
      .limit(1);

    if (mode === 'queued') {
      query = query.eq('status', STATUS_QUEUED);
    } else {
      const staleBoundaryIso = new Date(Date.now() - this.config.staleProcessingThresholdMs).toISOString();
      query = query.eq('status', STATUS_PROCESSING).or(`last_attempt_at.is.null,last_attempt_at.lte.${staleBoundaryIso}`);
    }

    const { data, error } = await query.maybeSingle();
    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to claim delivery ${delivery.delivery_id}`, error);
      return null;
    }
    if (!data) {
      return null;
    }
    return nextAttempts;
  }

  private async loadReport(reportId: string): Promise<ReportPayloadRow | null> {
    const { data, error } = await this.supabase
      .from(this.definition.reportTable)
      .select('report_id, session_id, report_html, metadata')
      .eq('report_id', reportId)
      .maybeSingle();

    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to load report ${reportId}`, error);
      return null;
    }
    return data ?? null;
  }

  private async markSent(deliveryId: string): Promise<void> {
    const nowIso = new Date().toISOString();
    const { error } = await this.supabase
      .from(this.definition.deliveryTable)
      .update({
        status: STATUS_SENT,
        dispatched_at: nowIso,
        updated_at: nowIso,
        last_error: null
      })
      .eq('delivery_id', deliveryId);

    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to mark delivery ${deliveryId} as sent`, error);
    }
  }

  private async markFailure(deliveryId: string, attempts: number, message: string, shouldRetry: boolean): Promise<void> {
    const nowIso = new Date().toISOString();
    const nextStatus = shouldRetry ? STATUS_QUEUED : STATUS_FAILED;
    const { error } = await this.supabase
      .from(this.definition.deliveryTable)
      .update({
        status: nextStatus,
        last_error: message,
        updated_at: nowIso
      })
      .eq('delivery_id', deliveryId);

    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to update delivery ${deliveryId}`, error);
    } else {
      console.info(
        `[WORKER][${this.definition.name}] Delivery ${deliveryId} ${shouldRetry ? 're-queued' : 'failed'} after ${attempts} attempts: ${message}`
      );
    }
  }

  private extractIngestId(metadata: ReportMetadata): string | null {
    if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
      return null;
    }
    const record = metadata as Record<string, unknown>;
    const direct = record.ingest_id ?? record.ingestId ?? record.ingestID;
    if (typeof direct === 'string' && direct.trim()) {
      return direct.trim();
    }
    const nested = record.ingest;
    if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
      const nestedRecord = nested as Record<string, unknown>;
      const nestedValue = nestedRecord.id ?? nestedRecord.ingestId ?? nestedRecord.ingest_id;
      if (typeof nestedValue === 'string' && nestedValue.trim()) {
        return nestedValue.trim();
      }
    }
    return null;
  }

  private async updateIngestState(ingestId: string, patch: IngestStatePatch): Promise<void> {
    const update: Database['public']['Tables']['report_ingest_queue']['Update'] = { updated_at: new Date().toISOString() };
    let changed = false;

    if (patch.status) {
      update.status = patch.status;
      changed = true;
    }
    if (typeof patch.lastError !== 'undefined') {
      update.last_error = patch.lastError ?? null;
      changed = true;
    }
    if (typeof patch.retries === 'number' && patch.retries >= 0) {
      update.retries = patch.retries;
      changed = true;
    }

    if (!changed) {
      return;
    }

    const { error } = await this.supabase
      .from('report_ingest_queue')
      .update(update)
      .eq('ingest_id', ingestId)
      .eq('environment', this.config.environment)
      .limit(1);

    if (error) {
      console.warn(`[WORKER][${this.definition.name}] Failed to update ingest ${ingestId}`, error);
    }
  }
}

export function createQueueDefinitions(): QueueDefinition[] {
  return [
    {
      name: 'diagnostics',
      deliveryTable: 'diagnostics_report_deliveries',
      reportTable: 'diagnostics_reports',
      composeEmailSubject: composeDiagnosticsEmailSubject,
      composeSmsBody: composeDiagnosticsSmsBody
    },
    {
      name: 'thickness',
      deliveryTable: 'thickness_report_deliveries',
      reportTable: 'thickness_reports',
      composeEmailSubject: composeThicknessEmailSubject,
      composeSmsBody: composeThicknessSmsBody
    }
  ];
}

function normalizeError(error: unknown): string {
  if (!error) {
    return 'unknown error';
  }
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}
