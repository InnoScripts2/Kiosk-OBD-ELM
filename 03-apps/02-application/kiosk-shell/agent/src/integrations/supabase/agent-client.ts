import { randomUUID } from 'node:crypto';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import type { DeploymentEnvironment } from '../../config/environment.js';
import type { Database, Json } from './types.js';

export type ReportIngestStatus = 'received' | 'queued' | 'processing' | 'completed' | 'failed' | 'cancelled';
export type ReportIngestType = 'diagnostics' | 'thickness';
export type DeviceCommandStatus = 'received' | 'acknowledged' | 'in_progress' | 'succeeded' | 'failed' | 'cancelled';
export type DeviceCommandRecord = Database['public']['Tables']['device_commands']['Row'];

export interface ReportIngestRecordInput {
  ingestId?: string;
  reportType: ReportIngestType;
  sessionId: string;
  status: ReportIngestStatus;
  payload: Database['public']['Tables']['report_ingest_queue']['Insert']['payload'];
  contactEmail?: string | null;
  contactPhone?: string | null;
  priority?: number;
  retries?: number;
  lastError?: string | null;
  receivedAtMs: number;
  slaDeadlineMs: number;
  kioskId?: string;
  environment: DeploymentEnvironment;
  traceId?: string | null;
}

export interface ReportIngestStatePatch {
  status?: ReportIngestStatus;
  lastError?: string | null;
  slaDeadlineMs?: number;
  retries?: number;
}

export interface SupabaseAgentClientOptions {
  url: string;
  serviceRoleKey: string;
  environment: DeploymentEnvironment;
  kioskId?: string;
}

export interface DeviceCommandQueryOptions {
  limit?: number;
  statuses?: DeviceCommandStatus[];
  kioskId?: string;
  includeBroadcast?: boolean;
}

export interface DeviceCommandUpdateInput {
  status?: DeviceCommandStatus;
  acknowledgedAt?: Date;
  startedAt?: Date;
  completedAt?: Date;
  resultPayload?: Json | null;
  errorMessage?: string | null;
  latencyMs?: number | null;
  attempt?: number | null;
  metadata?: Json | null;
}

export interface DeviceCommandUpdateOptions {
  matchStatuses?: DeviceCommandStatus[];
}

export interface DeviceEventInput {
  eventType: string;
  severity: 'info' | 'warning' | 'error';
  message?: string | null;
  commandId?: string | null;
  commandStatus?: DeviceCommandStatus | null;
  commandType?: string | null;
  payload?: Json | null;
  metadata?: Json | null;
  recordedAt?: Date;
  source?: string | null;
}

export interface RecordMetricInput {
  metricType: string;
  recordedAt: Date;
  valueNumber?: number | null;
  valueText?: string | null;
  payload?: Record<string, unknown> | null;
  source?: string | null;
}

export class SupabaseAgentClient {
  private readonly client: SupabaseClient<Database>;
  private readonly environment: DeploymentEnvironment;
  private readonly kioskId?: string;

  constructor(options: SupabaseAgentClientOptions) {
    this.environment = options.environment;
    this.kioskId = options.kioskId;
    this.client = createClient<Database>(options.url, options.serviceRoleKey, {
      auth: { persistSession: false },
      global: {
        headers: {
          'x-client-info': 'kiosk-agent/1.0.0',
        },
      },
    });
  }

  async enqueueReportIngest(record: ReportIngestRecordInput): Promise<{ ingestId: string }> {
    const ingestId = record.ingestId ?? randomUUID();
    const { error } = await this.client.from('report_ingest_queue').insert({
      ingest_id: ingestId,
      report_type: record.reportType,
      session_id: record.sessionId,
      status: record.status,
      payload: record.payload,
      contact_email: normalizeNullable(record.contactEmail),
      contact_phone: normalizeNullable(record.contactPhone),
      priority: record.priority ?? 0,
      retries: record.retries ?? 0,
      last_error: normalizeNullable(record.lastError),
      received_at_ms: record.receivedAtMs,
      sla_deadline_ms: record.slaDeadlineMs,
      kiosk_id: record.kioskId ?? this.kioskId ?? null,
      environment: record.environment ?? this.environment,
      trace_id: normalizeNullable(record.traceId),
    });

    if (error) {
      throw new Error(`[SupabaseAgentClient] Failed to enqueue report ingest: ${error.message}`);
    }

    return { ingestId };
  }

  async updateReportIngestState(ingestId: string, patch: ReportIngestStatePatch): Promise<void> {
    const updatePayload: Database['public']['Tables']['report_ingest_queue']['Update'] = {};

    if (patch.status) {
      updatePayload.status = patch.status;
    }
    if (typeof patch.lastError !== 'undefined') {
      updatePayload.last_error = normalizeNullable(patch.lastError);
    }
    if (typeof patch.slaDeadlineMs === 'number' && Number.isFinite(patch.slaDeadlineMs)) {
      updatePayload.sla_deadline_ms = patch.slaDeadlineMs;
    }
    if (typeof patch.retries === 'number' && patch.retries >= 0) {
      updatePayload.retries = patch.retries;
    }

    if (Object.keys(updatePayload).length === 0) {
      return;
    }

    const { error } = await this.client
      .from('report_ingest_queue')
      .update(updatePayload)
      .eq('ingest_id', ingestId);

    if (error) {
      throw new Error(`[SupabaseAgentClient] Failed to update ingest ${ingestId}: ${error.message}`);
    }
  }

  async recordMetric(input: RecordMetricInput): Promise<void> {
    const { error } = await this.client.from('agent_metrics').insert({
      metric_type: input.metricType,
      recorded_at: input.recordedAt.toISOString(),
      value_number: typeof input.valueNumber === 'number' ? input.valueNumber : null,
      value_text: typeof input.valueText === 'string' ? input.valueText : null,
      payload: (input.payload as Database['public']['Tables']['agent_metrics']['Insert']['payload']) ?? null,
      source: normalizeNullable(input.source),
      kiosk_id: this.kioskId ?? null,
      environment: this.environment,
    });

    if (error) {
      console.warn('[SupabaseAgentClient] Failed to record metric', error);
    }
  }

  async fetchDeviceCommands(options: DeviceCommandQueryOptions = {}): Promise<DeviceCommandRecord[]> {
    const statuses = (options.statuses ?? ['received']).filter(Boolean);
    const limit = options.limit && options.limit > 0 ? options.limit : 10;
    const kioskFilter = options.kioskId ?? this.kioskId;
    let query = this.client
      .from('device_commands')
      .select('*')
      .eq('environment', this.environment)
      .order('requested_at', { ascending: true })
      .limit(limit);

    if (statuses.length) {
      query = query.in('status', statuses);
    }

    if (kioskFilter) {
      if (options.includeBroadcast === false) {
        query = query.eq('kiosk_id', kioskFilter);
      } else {
        query = query.or(`kiosk_id.eq.${kioskFilter},kiosk_id.is.null`);
      }
    } else {
      query = query.is('kiosk_id', null);
    }

    const { data, error } = await query;
    if (error) {
      throw new Error(`[SupabaseAgentClient] Failed to fetch device commands: ${error.message}`);
    }
    return data ?? [];
  }

  async updateDeviceCommand(
    commandId: string,
    patch: DeviceCommandUpdateInput,
    options: DeviceCommandUpdateOptions = {},
  ): Promise<boolean> {
    const update: Database['public']['Tables']['device_commands']['Update'] = {
      updated_at: new Date().toISOString(),
    };
    let hasChanges = false;

    if (patch.status) {
      update.status = patch.status;
      hasChanges = true;
    }
    if (patch.acknowledgedAt) {
      update.acknowledged_at = patch.acknowledgedAt.toISOString();
      hasChanges = true;
    }
    if (patch.startedAt) {
      update.started_at = patch.startedAt.toISOString();
      hasChanges = true;
    }
    if (patch.completedAt) {
      update.completed_at = patch.completedAt.toISOString();
      hasChanges = true;
    }
    if (typeof patch.latencyMs === 'number') {
      update.latency_ms = patch.latencyMs;
      hasChanges = true;
    }
    if (typeof patch.attempt === 'number') {
      update.attempt = patch.attempt;
      hasChanges = true;
    }
    if (typeof patch.resultPayload !== 'undefined') {
      update.result_payload = (patch.resultPayload ?? null) as Database['public']['Tables']['device_commands']['Update']['result_payload'];
      hasChanges = true;
    }
    if (typeof patch.errorMessage !== 'undefined') {
      update.error_message = patch.errorMessage ?? null;
      hasChanges = true;
    }
    if (typeof patch.metadata !== 'undefined') {
      update.metadata = (patch.metadata ?? null) as Database['public']['Tables']['device_commands']['Update']['metadata'];
      hasChanges = true;
    }

    if (!hasChanges) {
      return true;
    }

    let query = this.client
      .from('device_commands')
      .update(update)
      .eq('command_id', commandId)
      .eq('environment', this.environment)
      .select('command_id')
      .limit(1);

    if (options.matchStatuses && options.matchStatuses.length) {
      query = query.in('status', options.matchStatuses);
    }

    const { data, error } = await query.maybeSingle();
    if (error) {
      console.warn(`[SupabaseAgentClient] Failed to update device command ${commandId}`, error);
      return false;
    }
    return Boolean(data);
  }

  async recordDeviceEvent(event: DeviceEventInput): Promise<void> {
    const recordTime = event.recordedAt ?? new Date();
    const eventId = randomUUID();
    const { error } = await this.client.from('device_events').insert({
      event_id: eventId,
      event_type: event.eventType,
      severity: event.severity,
      message: event.message ?? null,
      recorded_at: recordTime.toISOString(),
      kiosk_id: this.kioskId ?? null,
      environment: this.environment,
      command_id: event.commandId ?? null,
      command_status: event.commandStatus ?? null,
      command_type: event.commandType ?? null,
      payload: (event.payload ?? null) as Database['public']['Tables']['device_events']['Insert']['payload'],
      metadata: (event.metadata ?? null) as Database['public']['Tables']['device_events']['Insert']['metadata'],
      source: event.source ?? 'kiosk-agent',
    });

    if (error) {
      console.warn('[SupabaseAgentClient] Failed to record device event', error);
    }
  }
}

function normalizeNullable(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length ? trimmed : null;
}
