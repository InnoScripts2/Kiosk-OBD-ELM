import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loadAgentConfig, type DeviceCommandConfig } from '../config/agent-config.js';
import type {
  DeviceCommandRecord,
  DeviceCommandStatus,
  SupabaseAgentClient,
} from '../integrations/supabase/agent-client.js';
import type { Json } from '../integrations/supabase/types.js';
import type { DeviceType, LockStatus } from './LockController.js';

export interface DeviceCommandServiceOptions {
  readonly supabase: SupabaseAgentClient;
  readonly config: DeviceCommandConfig;
  readonly kioskId?: string;
  readonly lockController?: {
    getStatus(): Promise<LockStatus>;
    openSlot(deviceType: DeviceType): Promise<void>;
    closeSlot(deviceType: DeviceType): Promise<void>;
  };
  readonly runtimePayloadProvider?: () => Promise<Record<string, unknown> | null> | Record<string, unknown> | null;
  readonly artifactsDirectory?: string;
  readonly deviceCommandConfigLoader?: () => Promise<DeviceCommandConfig> | DeviceCommandConfig;
}

interface DeviceCommandResult {
  readonly status: DeviceCommandStatus;
  readonly resultPayload?: Json | null;
  readonly errorMessage?: string | null;
}

const FINAL_STATUSES: DeviceCommandStatus[] = ['succeeded', 'failed', 'cancelled'];

export class DeviceCommandService {
  private readonly supabase: SupabaseAgentClient;
  private config: DeviceCommandConfig;
  private readonly kioskId?: string;
  private readonly lockController?: {
    getStatus(): Promise<LockStatus>;
    openSlot(deviceType: DeviceType): Promise<void>;
    closeSlot(deviceType: DeviceType): Promise<void>;
  };
  private readonly runtimePayloadProvider?: () => Promise<Record<string, unknown> | null> | Record<string, unknown> | null;
  private readonly artifactsDir: string;
  private readonly deviceCommandConfigLoader: () => Promise<DeviceCommandConfig> | DeviceCommandConfig;
  private pollTimer: NodeJS.Timeout | null = null;
  private rebootTimer: NodeJS.Timeout | null = null;
  private running = false;
  private processing = false;

  constructor(options: DeviceCommandServiceOptions) {
    this.supabase = options.supabase;
    this.config = { ...options.config };
    this.kioskId = options.kioskId;
    this.lockController = options.lockController;
    this.runtimePayloadProvider = options.runtimePayloadProvider;
    this.artifactsDir = options.artifactsDirectory ?? path.join(process.cwd(), 'tmp', 'device-commands');
    this.deviceCommandConfigLoader =
      options.deviceCommandConfigLoader ?? (() => loadAgentConfig().deviceCommands);
  }

  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    this.scheduleNext(0);
  }

  stop(): void {
    this.running = false;
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
    if (this.rebootTimer) {
      clearTimeout(this.rebootTimer);
      this.rebootTimer = null;
    }
  }

  private scheduleNext(delayMs: number): void {
    if (!this.running) {
      return;
    }
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
    }
    const delay = Math.max(50, delayMs);
    this.pollTimer = setTimeout(() => {
      this.pollCommands().catch((error) => {
        console.warn('[DEVICE-COMMANDS] Poll loop failed', error);
        this.scheduleNext(this.config.pollIntervalMs);
      });
    }, delay);
  }

  private async pollCommands(): Promise<void> {
    if (!this.running || this.processing) {
      this.scheduleNext(this.config.pollIntervalMs);
      return;
    }
    this.processing = true;
    try {
      const commands = await this.supabase.fetchDeviceCommands({
        limit: this.config.maxBatchSize,
        kioskId: this.kioskId,
        includeBroadcast: this.config.includeBroadcast,
      });
      if (!commands.length) {
        this.scheduleNext(this.config.pollIntervalMs);
        return;
      }

      for (const command of commands) {
        await this.processCommand(command).catch((error) => {
          console.warn(`[DEVICE-COMMANDS] Failed to process command ${command.command_id}`, error);
        });
      }

      this.scheduleNext(this.config.activePollIntervalMs);
    } catch (error) {
      console.warn('[DEVICE-COMMANDS] Failed to fetch device commands', error);
      this.scheduleNext(this.config.pollIntervalMs);
    } finally {
      this.processing = false;
    }
  }

  private async processCommand(command: DeviceCommandRecord): Promise<void> {
    const attempt = (command.attempt ?? 0) + 1;
    const acknowledged = await this.supabase.updateDeviceCommand(
      command.command_id,
      {
        status: 'acknowledged',
        acknowledgedAt: new Date(),
        attempt,
      },
      { matchStatuses: ['received'] },
    );

    if (!acknowledged) {
      console.warn(`[DEVICE-COMMANDS] Command ${command.command_id} could not be acknowledged`);
      return;
    }

    await this.supabase.recordDeviceEvent({
      eventType: 'device_command_acknowledged',
      severity: 'info',
      commandId: command.command_id,
      commandStatus: 'acknowledged',
      commandType: command.command_type,
      message: `Команда ${command.command_type} подтверждена агентом`,
    });

    const inProgress = await this.supabase.updateDeviceCommand(
      command.command_id,
      {
        status: 'in_progress',
        startedAt: new Date(),
      },
      { matchStatuses: ['acknowledged', 'in_progress'] },
    );

    if (!inProgress) {
      console.warn(`[DEVICE-COMMANDS] Command ${command.command_id} could not transition to in_progress`);
      return;
    }

    let result: DeviceCommandResult;
    try {
      result = await this.executeCommand(command);
    } catch (error) {
      result = {
        status: 'failed',
        errorMessage: normalizeError(error),
      };
    }

    if (!FINAL_STATUSES.includes(result.status)) {
      result = { status: 'failed', errorMessage: 'invalid_command_status' };
    }

    const completedAt = new Date();
    const requestedAtMs = parseTimestamp(command.requested_at);
    const latencyMs = typeof requestedAtMs === 'number' ? completedAt.getTime() - requestedAtMs : null;

    await this.supabase.updateDeviceCommand(
      command.command_id,
      {
        status: result.status,
        completedAt,
        resultPayload: result.resultPayload ?? null,
        errorMessage: result.errorMessage ?? null,
        latencyMs,
      },
      { matchStatuses: ['in_progress'] },
    );

    await this.supabase.recordDeviceEvent({
      eventType: 'device_command_completed',
      severity: result.status === 'succeeded' ? 'info' : 'error',
      commandId: command.command_id,
      commandStatus: result.status,
      commandType: command.command_type,
      message:
        result.status === 'succeeded'
          ? `Команда ${command.command_type} выполнена`
          : result.errorMessage ?? `Команда ${command.command_type} завершилась с ошибкой`,
      payload: result.resultPayload ?? null,
    });
  }

  private async executeCommand(command: DeviceCommandRecord): Promise<DeviceCommandResult> {
    const type = (command.command_type || '').trim().toUpperCase();
    switch (type) {
      case 'PING':
        return this.handlePing();
      case 'LOCK_CONTROL':
        return this.handleLockControl(command);
      case 'SYNC_CONFIG':
        return this.handleSyncConfig(command);
      case 'UPDATE_APP':
        return this.handleUpdateApp(command);
      case 'REBOOT':
        return this.handleReboot(command);
      default:
        return {
          status: 'failed',
          errorMessage: `unsupported_command:${command.command_type || 'unknown'}`,
        };
    }
  }

  private async handlePing(): Promise<DeviceCommandResult> {
    const payload: Record<string, unknown> = {
      respondedAt: new Date().toISOString(),
      kioskId: this.kioskId ?? null,
    };

    const runtime = await this.resolveRuntimePayload();
    if (runtime) {
      payload.runtime = runtime;
    }

    const locks = await this.resolveLockStatus();
    if (locks) {
      payload.locks = locks;
    }

    return {
      status: 'succeeded',
      resultPayload: payload as Json,
    };
  }

  private async handleLockControl(command: DeviceCommandRecord): Promise<DeviceCommandResult> {
    const controller = this.lockController;
    if (!controller) {
      return { status: 'failed', errorMessage: 'lock_controller_unavailable' };
    }

    const parsedPayload = this.parseLockControlPayload(command.payload);
    if (!parsedPayload.ok) {
      return { status: 'failed', errorMessage: parsedPayload.error };
    }

    const payload = parsedPayload.value;

    try {
      if (payload.action === 'open') {
        await controller.openSlot(payload.deviceType);
      } else {
        await controller.closeSlot(payload.deviceType);
      }
      const lockStatus = await controller.getStatus().catch(() => null);
      return {
        status: 'succeeded',
        resultPayload: this.buildLockControlResultPayload(payload, lockStatus),
      };
    } catch (error) {
      const lockStatus = await this.resolveLockStatus();
      return {
        status: 'failed',
        errorMessage: normalizeError(error),
        resultPayload: this.buildLockControlResultPayload(payload, lockStatus),
      };
    }
  }

  private async handleReboot(command: DeviceCommandRecord): Promise<DeviceCommandResult> {
    const payloadRecord = this.getJsonRecord(command.payload);
    const requestedDelay = this.parsePositiveNumber(payloadRecord?.delayMs);
    const delayMs = Math.max(500, requestedDelay ?? this.config.rebootGracePeriodMs);
    const scheduledRestartAt = new Date(Date.now() + delayMs).toISOString();

    this.scheduleAgentRestart(delayMs);

    return {
      status: 'succeeded',
      resultPayload: {
        kioskId: this.kioskId ?? null,
        delayMs,
        scheduledRestartAt,
        requestedBy: this.normalizeNullableString(payloadRecord?.requestedBy) ?? null,
        reason: this.normalizeNullableString(payloadRecord?.reason) ?? null,
      } as Json,
    };
  }

  private async handleSyncConfig(command: DeviceCommandRecord): Promise<DeviceCommandResult> {
    const parsed = this.parseSyncConfigPayload(command.payload);
    if (!parsed.ok) {
      return { status: 'failed', errorMessage: parsed.error };
    }

    const freshConfig = await this.collectDeviceCommandConfig();
    if (!freshConfig) {
      return { status: 'failed', errorMessage: 'config_sync_loader_unavailable' };
    }

    const previousConfig = { ...this.config };
    const updatedConfig = { ...freshConfig };
    this.config = updatedConfig;
    const diff = this.diffDeviceCommandConfig(previousConfig, updatedConfig);
    const syncedAt = new Date().toISOString();

    const artifactPath = await this.persistCommandArtifact('sync-config', {
      kioskId: this.kioskId ?? null,
      syncedAt,
      requestedBy: parsed.value.requestedBy ?? null,
      sections: parsed.value.sections ?? null,
      reason: parsed.value.reason ?? null,
      commandId: command.command_id,
      previousConfig,
      updatedConfig,
      changedFields: diff.changedFields,
    });

    if (parsed.value.forceRestart) {
      const restartDelay = parsed.value.restartDelayMs ?? this.config.rebootGracePeriodMs;
      this.scheduleAgentRestart(restartDelay);
    }

    return {
      status: 'succeeded',
      resultPayload: {
        kioskId: this.kioskId ?? null,
        syncedAt,
        requestedBy: parsed.value.requestedBy ?? null,
        sections: parsed.value.sections ?? null,
        reason: parsed.value.reason ?? null,
        changedFields: diff.changedFields,
        previousConfig,
        deviceCommandConfig: updatedConfig,
        artifactPath,
      } as Json,
    };
  }

  private async handleUpdateApp(command: DeviceCommandRecord): Promise<DeviceCommandResult> {
    const parsed = this.parseUpdateAppPayload(command.payload);
    if (!parsed.ok) {
      return { status: 'failed', errorMessage: parsed.error };
    }

    const requestedAt = new Date().toISOString();
    const summary = {
      kioskId: this.kioskId ?? null,
      version: parsed.value.version,
      channel: parsed.value.channel,
      artifactUrl: parsed.value.artifactUrl ?? null,
      checksum: parsed.value.checksum ?? null,
      notes: parsed.value.notes ?? null,
      requestedBy: parsed.value.requestedBy ?? null,
      requestedAt,
      forceRestart: parsed.value.forceRestart ?? false,
      restartDelayMs: parsed.value.restartDelayMs ?? null,
    } as const;

    const artifactPath = await this.persistCommandArtifact('update-app', {
      ...summary,
      commandId: command.command_id,
      source: command.source ?? null,
    });

    if (parsed.value.forceRestart) {
      const delayMs = parsed.value.restartDelayMs ?? this.config.rebootGracePeriodMs;
      this.scheduleAgentRestart(delayMs);
    }

    return {
      status: 'succeeded',
      resultPayload: {
        ...summary,
        artifactPath,
      } as Json,
    };
  }

  private buildLockControlResultPayload(payload: LockControlPayload, lockStatus: LockStatus | null): Json {
    return {
      action: payload.action,
      deviceType: payload.deviceType,
      sessionId: payload.sessionId ?? null,
      reason: payload.reason ?? null,
      requestedBy: payload.requestedBy ?? null,
      kioskId: this.kioskId ?? null,
      lockStatus: lockStatus ?? null,
      respondedAt: new Date().toISOString(),
    } as Json;
  }

  private parseLockControlPayload(raw: Json | null): LockControlParseResult {
    const record = this.getJsonRecord(raw);
    if (!record) {
      return { ok: false, error: 'lock_control_invalid_payload:object' };
    }

    const actionRaw = this.normalizeNullableString(record.action)?.toLowerCase();
    if (actionRaw !== 'open' && actionRaw !== 'close') {
      return { ok: false, error: 'lock_control_invalid_payload:action' };
    }

    const deviceRaw = this.normalizeNullableString(record.deviceType)?.toLowerCase();
    const deviceType = deviceRaw === 'thickness' || deviceRaw === 'adapter' ? (deviceRaw as DeviceType) : null;
    if (!deviceType) {
      return { ok: false, error: 'lock_control_invalid_payload:deviceType' };
    }

    return {
      ok: true,
      value: {
        action: actionRaw,
        deviceType,
        sessionId: this.normalizeNullableString(record.sessionId),
        reason: this.normalizeNullableString(record.reason),
        requestedBy: this.normalizeNullableString(record.requestedBy),
      },
    };
  }

  private scheduleAgentRestart(delayMs: number): void {
    if (this.rebootTimer) {
      clearTimeout(this.rebootTimer);
    }
    this.rebootTimer = setTimeout(() => {
      console.warn(`[DEVICE-COMMANDS] Initiating agent reboot (delay ${delayMs}ms)`);
      try {
        process.kill(process.pid, 'SIGTERM');
      } catch (error) {
        console.error('[DEVICE-COMMANDS] Failed to send SIGTERM for reboot, forcing exit', error);
        process.exit(0);
      }
    }, delayMs);
  }

  private getJsonRecord(value: Json | null): Record<string, unknown> | null {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null;
    }
    return value as Record<string, unknown>;
  }

  private normalizeNullableString(value: unknown): string | undefined {
    if (typeof value !== 'string') {
      return undefined;
    }
    const trimmed = value.trim();
    return trimmed.length ? trimmed : undefined;
  }

  private parsePositiveNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
      return value;
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) {
        return null;
      }
      const parsed = Number.parseInt(trimmed, 10);
      if (Number.isFinite(parsed) && parsed > 0) {
        return parsed;
      }
    }
    return null;
  }

  private async resolveLockStatus(): Promise<LockStatus | null> {
    if (!this.lockController) {
      return null;
    }
    try {
      return await this.lockController.getStatus();
    } catch (error) {
      console.warn('[DEVICE-COMMANDS] Failed to collect lock status', error);
      return null;
    }
  }

  private async resolveRuntimePayload(): Promise<Record<string, unknown> | null> {
    if (!this.runtimePayloadProvider) {
      return null;
    }
    try {
      const result = await this.runtimePayloadProvider();
      if (!result || typeof result !== 'object') {
        return null;
      }
      return result;
    } catch (error) {
      console.warn('[DEVICE-COMMANDS] Failed to resolve runtime payload', error);
      return null;
    }
  }

  private buildNotImplementedResult(command: string): DeviceCommandResult {
    return {
      status: 'failed',
      errorMessage: `command_not_implemented:${command}`,
    };
  }

  private async collectDeviceCommandConfig(): Promise<DeviceCommandConfig | null> {
    try {
      const config = await this.deviceCommandConfigLoader();
      return config ?? null;
    } catch (error) {
      console.warn('[DEVICE-COMMANDS] Failed to reload agent config', error);
      return null;
    }
  }

  private diffDeviceCommandConfig(
    previousConfig: DeviceCommandConfig,
    updatedConfig: DeviceCommandConfig,
  ): DeviceCommandConfigDiff {
    const trackedKeys: Array<keyof DeviceCommandConfig> = [
      'pollIntervalMs',
      'activePollIntervalMs',
      'maxBatchSize',
      'includeBroadcast',
      'rebootGracePeriodMs',
    ];

    const changedFields = trackedKeys.filter((key) => previousConfig[key] !== updatedConfig[key]);
    return { changedFields, previousConfig, updatedConfig };
  }

  private async persistCommandArtifact(command: string, payload: Record<string, unknown>): Promise<string | null> {
    try {
      const sanitizedCommand = command.trim().toLowerCase().replace(/[^a-z0-9-]+/g, '-');
      const targetDir = path.join(this.artifactsDir, sanitizedCommand);
      await mkdir(targetDir, { recursive: true });
      const timestamp = new Date().toISOString().replace(/[:]/g, '-');
      const fileName = `${timestamp}-${randomUUID()}.json`;
      const absolutePath = path.join(targetDir, fileName);
      await writeFile(absolutePath, JSON.stringify(payload, null, 2), 'utf-8');
      return path.relative(process.cwd(), absolutePath);
    } catch (error) {
      console.warn('[DEVICE-COMMANDS] Failed to persist command artifact', command, error);
      return null;
    }
  }

  private parseSyncConfigPayload(raw: Json | null): SyncConfigParseResult {
    const record = this.getJsonRecord(raw) ?? {};
    const sections = Array.isArray(record.sections)
      ? record.sections
          .map((item) => this.normalizeNullableString(item))
          .filter((item): item is string => Boolean(item))
      : undefined;
    const forceRestart = typeof record.forceRestart === 'boolean' ? record.forceRestart : false;
    const restartDelayMs = this.parsePositiveNumber(record.restartDelayMs);

    return {
      ok: true,
      value: {
        sections,
        reason: this.normalizeNullableString(record.reason),
        requestedBy: this.normalizeNullableString(record.requestedBy),
        forceRestart,
        restartDelayMs,
      },
    };
  }

  private parseUpdateAppPayload(raw: Json | null): UpdateAppParseResult {
    const record = this.getJsonRecord(raw);
    if (!record) {
      return { ok: false, error: 'update_app_invalid_payload:object' };
    }

    const version = this.normalizeNullableString(record.version);
    if (!version) {
      return { ok: false, error: 'update_app_invalid_payload:version' };
    }

    const channel = this.normalizeNullableString(record.channel) ?? 'stable';
    const restartDelayMs = this.parsePositiveNumber(record.restartDelayMs);
    const forceRestart = typeof record.forceRestart === 'boolean' ? record.forceRestart : false;

    return {
      ok: true,
      value: {
        version,
        channel,
        artifactUrl: this.normalizeNullableString(record.artifactUrl),
        checksum: this.normalizeNullableString(record.checksum),
        notes: this.normalizeNullableString(record.notes),
        requestedBy: this.normalizeNullableString(record.requestedBy),
        forceRestart,
        restartDelayMs,
      },
    };
  }
}

type LockControlAction = 'open' | 'close';

interface LockControlPayload {
  action: LockControlAction;
  deviceType: DeviceType;
  sessionId?: string;
  reason?: string;
  requestedBy?: string;
}

type LockControlParseResult =
  | { ok: true; value: LockControlPayload }
  | { ok: false; error: string };

interface SyncConfigPayload {
  sections?: string[];
  reason?: string;
  requestedBy?: string;
  forceRestart?: boolean;
  restartDelayMs?: number | null;
}

type SyncConfigParseResult =
  | { ok: true; value: SyncConfigPayload }
  | { ok: false; error: string };

interface UpdateAppPayload {
  version: string;
  channel: string;
  artifactUrl?: string;
  checksum?: string;
  notes?: string;
  requestedBy?: string;
  forceRestart?: boolean;
  restartDelayMs?: number | null;
}

type UpdateAppParseResult =
  | { ok: true; value: UpdateAppPayload }
  | { ok: false; error: string };

interface DeviceCommandConfigDiff {
  changedFields: Array<keyof DeviceCommandConfig>;
  previousConfig: DeviceCommandConfig;
  updatedConfig: DeviceCommandConfig;
}

function normalizeError(error: unknown): string {
  if (!error) {
    return 'unknown_error';
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

function parseTimestamp(value: string | null): number | null {
  if (!value) {
    return null;
  }
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? null : timestamp;
}
