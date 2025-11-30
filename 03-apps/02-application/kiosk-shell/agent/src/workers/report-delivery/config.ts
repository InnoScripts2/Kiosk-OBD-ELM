import type { Json } from '../../integrations/supabase/types.js';
import type { DeploymentEnvironment } from '../../config/environment.js';
import { resolveEnvironment } from '../../config/environment.js';
export { resolveEnvironment } from '../../config/environment.js';

export interface ReportDeliveryWorkerConfig {
  readonly supabaseUrl: string;
  readonly supabaseServiceRoleKey: string;
  readonly environment: DeploymentEnvironment;
  readonly kioskId?: string;
  readonly pollIntervalMs: number;
  readonly activePollIntervalMs: number;
  readonly maxBatchSize: number;
  readonly staleProcessingThresholdMs: number;
  readonly maxAttempts: number;
}

const DEFAULT_POLL_INTERVAL_MS = 5000;
const DEFAULT_ACTIVE_POLL_INTERVAL_MS = 500;
const DEFAULT_MAX_BATCH_SIZE = 25;
const DEFAULT_STALE_PROCESSING_THRESHOLD_MS = 2 * 60 * 1000;
const DEFAULT_MAX_ATTEMPTS = 5;

export function loadReportDeliveryWorkerConfig(env: NodeJS.ProcessEnv = process.env): ReportDeliveryWorkerConfig {
  const supabaseUrl = requireEnv(env, 'SUPABASE_URL');
  const supabaseServiceRoleKey = requireEnv(env, 'SUPABASE_SERVICE_ROLE_KEY');
  const environment = resolveEnvironment(env.REPORT_WORKER_ENVIRONMENT ?? env.KIOSK_ENVIRONMENT ?? env.APP_MODE);
  const kioskId = env.KIOSK_SERIAL_NUMBER?.trim() || undefined;

  return {
    supabaseUrl,
    supabaseServiceRoleKey,
    environment,
    kioskId,
    pollIntervalMs: parsePositiveInt(env.REPORT_WORKER_POLL_MS, DEFAULT_POLL_INTERVAL_MS),
    activePollIntervalMs: parsePositiveInt(env.REPORT_WORKER_ACTIVE_POLL_MS, DEFAULT_ACTIVE_POLL_INTERVAL_MS),
    maxBatchSize: parsePositiveInt(env.REPORT_WORKER_BATCH_SIZE, DEFAULT_MAX_BATCH_SIZE),
    staleProcessingThresholdMs: parsePositiveInt(env.REPORT_WORKER_STALE_PROCESSING_MS, DEFAULT_STALE_PROCESSING_THRESHOLD_MS),
    maxAttempts: parsePositiveInt(env.REPORT_WORKER_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS)
  };
}

function requireEnv(env: NodeJS.ProcessEnv, key: string): string {
  const value = env[key];
  if (!value || !value.trim()) {
    throw new Error(`Environment variable ${key} is required for the report delivery worker`);
  }
  return value.trim();
}

function parsePositiveInt(raw: string | undefined, fallback: number): number {
  const parsed = raw ? Number.parseInt(raw, 10) : Number.NaN;
  if (Number.isFinite(parsed) && parsed > 0) {
    return parsed;
  }
  return fallback;
}

export type ReportMetadata = Json | null;
