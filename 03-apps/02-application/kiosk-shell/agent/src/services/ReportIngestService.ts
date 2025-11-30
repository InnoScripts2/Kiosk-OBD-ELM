import { randomUUID } from 'node:crypto';
import type { DeploymentEnvironment } from '../config/environment.js';
import type {
  SupabaseAgentClient,
  ReportIngestRecordInput,
  ReportIngestStatus,
  ReportIngestType,
  ReportIngestStatePatch,
} from '../integrations/supabase/agent-client.js';
import type { Json } from '../integrations/supabase/types.js';

export interface ReportIntakePayload {
  type?: unknown;
  sessionId?: unknown;
  reportHtml?: unknown;
  reportPdfBase64?: unknown;
  metadata?: unknown;
  contact?: {
    email?: unknown;
    phone?: unknown;
  };
}

export interface ReportIntakeResult {
  ingestId: string;
  status: ReportIngestStatus;
  receivedAt: string;
  slaDeadlineMs: number;
}

export interface ReportStatePatchInput {
  status?: unknown;
  lastError?: unknown;
  slaDeadlineMs?: unknown;
  retries?: unknown;
}

export class ReportIngestValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ReportIngestValidationError';
  }
}

export interface ReportIngestServiceOptions {
  supabase: SupabaseAgentClient;
  kioskId?: string;
  environment: DeploymentEnvironment;
  generationTimeoutMs: number;
  deliverySlaMs: number;
}

export class ReportIngestService {
  private readonly supabase: SupabaseAgentClient;
  private readonly kioskId?: string;
  private readonly environment: DeploymentEnvironment;
  private readonly generationTimeoutMs: number;
  private readonly deliverySlaMs: number;

  constructor(options: ReportIngestServiceOptions) {
    this.supabase = options.supabase;
    this.kioskId = options.kioskId;
    this.environment = options.environment;
    this.generationTimeoutMs = options.generationTimeoutMs;
    this.deliverySlaMs = options.deliverySlaMs;
  }

  async intake(payload: ReportIntakePayload): Promise<ReportIntakeResult> {
    const normalized = this.normalizePayload(payload);
    const receivedAtMs = Date.now();
    const slaDeadlineMs = receivedAtMs + this.deliverySlaMs;

    const record: ReportIngestRecordInput = {
      ingestId: randomUUID(),
      reportType: normalized.type,
      sessionId: normalized.sessionId,
      status: 'received',
      payload: {
        report_html: normalized.reportHtml,
        report_pdf_base64: normalized.reportPdfBase64,
        metadata: normalized.metadata,
        contact: normalized.contact,
        generation_timeout_ms: this.generationTimeoutMs,
      },
      contactEmail: normalized.contact.email,
      contactPhone: normalized.contact.phone,
      priority: 0,
      retries: 0,
      lastError: null,
      receivedAtMs,
      slaDeadlineMs,
      kioskId: this.kioskId,
      environment: this.environment,
      traceId: randomUUID(),
    };

    const { ingestId } = await this.supabase.enqueueReportIngest(record);
    await this.supabase.recordMetric({
      metricType: 'report_intake',
      recordedAt: new Date(receivedAtMs),
      valueText: normalized.type,
      payload: {
        sessionId: normalized.sessionId,
        ingestId: record.ingestId,
        contactProvided: Boolean(normalized.contact.email || normalized.contact.phone),
      },
    });

    return {
      ingestId,
      status: record.status,
      receivedAt: new Date(receivedAtMs).toISOString(),
      slaDeadlineMs,
    };
  }

  async updateState(ingestId: string, patch: ReportStatePatchInput): Promise<void> {
    const normalizedIngestId = this.normalizeIngestId(ingestId);
    const normalizedPatch = this.normalizeStatePatch(patch);
    await this.supabase.updateReportIngestState(normalizedIngestId, normalizedPatch);
    await this.recordStatePatchMetric(normalizedIngestId, normalizedPatch);
  }

  private normalizePayload(payload: ReportIntakePayload): NormalizedPayload {
    const type = resolveReportType(payload.type);
    const sessionId = normalizeString(payload.sessionId, 'sessionId');
    const reportHtml = normalizeString(payload.reportHtml, 'reportHtml');
    const reportPdfBase64 = normalizeString(payload.reportPdfBase64, 'reportPdfBase64');

    const metadata = normalizeMetadata(payload.metadata);

    const contact = {
      email: normalizeOptionalEmail(payload.contact?.email),
      phone: normalizeOptionalPhone(payload.contact?.phone),
    };

    return {
      type,
      sessionId,
      reportHtml,
      reportPdfBase64,
      metadata,
      contact,
    };
  }

  private normalizeStatePatch(patch: ReportStatePatchInput): ReportIngestStatePatch {
    const normalized: ReportIngestStatePatch = {};

    if (typeof patch.status !== 'undefined') {
      normalized.status = resolveReportStatus(patch.status);
    }
    if (typeof patch.lastError !== 'undefined') {
      normalized.lastError = normalizeNullableText(patch.lastError);
    }
    if (typeof patch.slaDeadlineMs !== 'undefined') {
      normalized.slaDeadlineMs = normalizePositiveInt(patch.slaDeadlineMs, 'slaDeadlineMs');
    }
    if (typeof patch.retries !== 'undefined') {
      normalized.retries = normalizeNonNegativeInt(patch.retries, 'retries');
    }

    if (Object.keys(normalized).length === 0) {
      throw new ReportIngestValidationError('empty_state_patch');
    }

    return normalized;
  }

  private normalizeIngestId(value: unknown): string {
    if (typeof value !== 'string') {
      throw new ReportIngestValidationError('invalid_ingest_id');
    }
    const trimmed = value.trim();
    if (!trimmed) {
      throw new ReportIngestValidationError('invalid_ingest_id');
    }
    return trimmed;
  }

  private async recordStatePatchMetric(ingestId: string, patch: ReportIngestStatePatch): Promise<void> {
    await this.supabase.recordMetric({
      metricType: 'agent_heartbeat',
      recordedAt: new Date(),
      valueText: patch.status ?? 'report_state_patch',
      payload: {
        component: 'report_ingest_state',
        ingestId,
        status: patch.status ?? null,
        lastError: typeof patch.lastError === 'string' ? patch.lastError : null,
        slaDeadlineMs: typeof patch.slaDeadlineMs === 'number' ? patch.slaDeadlineMs : null,
        retries: typeof patch.retries === 'number' ? patch.retries : null,
        appliedFields: Object.keys(patch),
      },
      source: 'report_state_patch',
    });
  }
}

type JsonRecord = Record<string, Json>;

interface NormalizedPayload {
  type: ReportIngestType;
  sessionId: string;
  reportHtml: string;
  reportPdfBase64: string;
  metadata: JsonRecord | null;
  contact: {
    email: string | null;
    phone: string | null;
  };
}

function normalizeMetadata(value: unknown): JsonRecord | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  const result: JsonRecord = {};
  for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
    result[key] = toJson(entry);
  }
  return result;
}

function toJson(value: unknown): Json {
  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => toJson(item));
  }
  if (typeof value === 'object') {
    const nested: JsonRecord = {};
    for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
      nested[key] = toJson(entry);
    }
    return nested;
  }
  return String(value);
}

function resolveReportType(value: unknown): ReportIngestType {
  if (value === 'diagnostics' || value === 'thickness') {
    return value;
  }
  throw new ReportIngestValidationError('invalid_report_type');
}

function resolveReportStatus(value: unknown): ReportIngestStatus {
  if (value === 'received' || value === 'queued' || value === 'processing' || value === 'completed' || value === 'failed' || value === 'cancelled') {
    return value;
  }
  throw new ReportIngestValidationError('invalid_report_status');
}

function normalizeString(value: unknown, field: string): string {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  throw new ReportIngestValidationError(`invalid_${field}`);
}

function normalizeOptionalEmail(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim().toLowerCase();
  if (!trimmed) {
    return null;
  }
  if (!trimmed.includes('@')) {
    throw new ReportIngestValidationError('invalid_contact_email');
  }
  return trimmed;
}

function normalizeOptionalPhone(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const normalized = value.replace(/\s+/g, '');
  if (!normalized) {
    return null;
  }
  if (!/^\+?[0-9]{6,15}$/.test(normalized)) {
    throw new ReportIngestValidationError('invalid_contact_phone');
  }
  return normalized;
}

function normalizeNullableText(value: unknown): string | null {
  if (value === null || typeof value === 'undefined') {
    return null;
  }
  if (typeof value !== 'string') {
    throw new ReportIngestValidationError('invalid_last_error');
  }
  const trimmed = value.trim();
  return trimmed.length ? trimmed : null;
}

function normalizePositiveInt(value: unknown, field: string): number {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return Math.floor(value);
  }
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
  }
  throw new ReportIngestValidationError(`invalid_${field}`);
}

function normalizeNonNegativeInt(value: unknown, field: string): number {
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
    return Math.floor(value);
  }
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed) && parsed >= 0) {
      return parsed;
    }
  }
  throw new ReportIngestValidationError(`invalid_${field}`);
}
