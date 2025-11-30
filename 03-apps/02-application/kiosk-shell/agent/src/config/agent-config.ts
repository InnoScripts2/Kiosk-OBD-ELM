import { resolveEnvironment, type DeploymentEnvironment } from './environment.js';

export interface SupabaseAgentConfig {
  readonly url: string;
  readonly serviceRoleKey: string;
  readonly environment: DeploymentEnvironment;
  readonly kioskId?: string;
}

export interface ReportingConfig {
  readonly generationTimeoutMs: number;
  readonly deliverySlaMs: number;
  readonly maxRetries: number;
  readonly outboxPath: string;
}

export interface AgentConfig {
  readonly supabase: SupabaseAgentConfig;
  readonly reporting: ReportingConfig;
  readonly heartbeat: HeartbeatConfig;
  readonly deviceCommands: DeviceCommandConfig;
}

const DEFAULT_GENERATION_TIMEOUT_MS = 5000;
const DEFAULT_DELIVERY_SLA_MS = 60000;
const DEFAULT_MAX_RETRIES = 5;
const DEFAULT_OUTBOX_PATH = './data/report-outbox.db';
const DEFAULT_HEARTBEAT_INTERVAL_MS = 60000;
const DEFAULT_HEARTBEAT_COMPONENT = 'agent_runtime';
const DEFAULT_DEVICE_COMMAND_POLL_MS = 5000;
const DEFAULT_DEVICE_COMMAND_ACTIVE_POLL_MS = 1000;
const DEFAULT_DEVICE_COMMAND_MAX_BATCH = 10;
const DEFAULT_DEVICE_COMMAND_REBOOT_GRACE_MS = 3000;

export interface HeartbeatConfig {
  readonly intervalMs: number;
  readonly component: string;
}

export interface DeviceCommandConfig {
  readonly pollIntervalMs: number;
  readonly activePollIntervalMs: number;
  readonly maxBatchSize: number;
  readonly includeBroadcast: boolean;
  readonly rebootGracePeriodMs: number;
}

export function loadAgentConfig(env: NodeJS.ProcessEnv = process.env): AgentConfig {
  const supabaseUrl = requireEnv(env, 'SUPABASE_URL');
  const serviceRoleKey = requireEnv(env, 'SUPABASE_SERVICE_ROLE_KEY');
  const environment = resolveEnvironment(env.KIOSK_ENVIRONMENT ?? env.APP_MODE);
  const kioskId = env.KIOSK_SERIAL_NUMBER?.trim() || undefined;

  return {
    supabase: {
      url: supabaseUrl,
      serviceRoleKey,
      environment,
      kioskId,
    },
    reporting: {
      generationTimeoutMs: parsePositiveInt(env.REPORT_GENERATION_TIMEOUT_MS, DEFAULT_GENERATION_TIMEOUT_MS),
      deliverySlaMs: parsePositiveInt(env.REPORT_DELIVERY_SLA_MS, DEFAULT_DELIVERY_SLA_MS),
      maxRetries: parsePositiveInt(env.REPORT_INGEST_MAX_RETRIES, DEFAULT_MAX_RETRIES),
      outboxPath: env.REPORT_OUTBOX_DB_PATH?.trim() || DEFAULT_OUTBOX_PATH,
    },
    heartbeat: {
      intervalMs: parsePositiveInt(
        env.AGENT_HEARTBEAT_INTERVAL_MS ?? resolveLegacyHeartbeatIntervalMs(env),
        DEFAULT_HEARTBEAT_INTERVAL_MS,
      ),
      component: env.AGENT_HEARTBEAT_COMPONENT?.trim() || DEFAULT_HEARTBEAT_COMPONENT,
    },
    deviceCommands: {
      pollIntervalMs: parsePositiveInt(env.DEVICE_COMMAND_POLL_MS, DEFAULT_DEVICE_COMMAND_POLL_MS),
      activePollIntervalMs: parsePositiveInt(
        env.DEVICE_COMMAND_ACTIVE_POLL_MS,
        DEFAULT_DEVICE_COMMAND_ACTIVE_POLL_MS,
      ),
      maxBatchSize: parsePositiveInt(env.DEVICE_COMMAND_MAX_BATCH, DEFAULT_DEVICE_COMMAND_MAX_BATCH),
      includeBroadcast: env.DEVICE_COMMAND_INCLUDE_BROADCAST !== 'false',
      rebootGracePeriodMs: parsePositiveInt(
        env.DEVICE_COMMAND_REBOOT_GRACE_MS,
        DEFAULT_DEVICE_COMMAND_REBOOT_GRACE_MS,
      ),
    },
  };
}

function requireEnv(env: NodeJS.ProcessEnv, key: string): string {
  const value = env[key];
  if (!value || !value.trim()) {
    throw new Error(`Environment variable ${key} is required`);
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

function resolveLegacyHeartbeatIntervalMs(env: NodeJS.ProcessEnv): string | undefined {
  const legacySecondsRaw = env.HEARTBEAT_INTERVAL_SEC;
  if (!legacySecondsRaw) {
    return undefined;
  }
  const seconds = Number.parseInt(legacySecondsRaw, 10);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return undefined;
  }
  return String(seconds * 1000);
}
