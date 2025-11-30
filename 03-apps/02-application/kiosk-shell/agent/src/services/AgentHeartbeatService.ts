import type { DeploymentEnvironment } from '../config/environment.js';
import type { SupabaseAgentClient } from '../integrations/supabase/agent-client.js';
import type { LockStatus } from './LockController.js';

export interface AgentHeartbeatServiceOptions {
  readonly supabase: SupabaseAgentClient;
  readonly lockController: { getStatus(): Promise<LockStatus>; };
  readonly intervalMs: number;
  readonly component: string;
  readonly kioskId?: string;
  readonly environment: DeploymentEnvironment;
  readonly payloadProvider?: () => Promise<Record<string, unknown> | null> | Record<string, unknown> | null;
}

const MIN_HEARTBEAT_INTERVAL_MS = 1000;

export class AgentHeartbeatService {
  private readonly supabase: SupabaseAgentClient;
  private readonly lockController: { getStatus(): Promise<LockStatus>; };
  private readonly intervalMs: number;
  private readonly component: string;
  private readonly kioskId?: string;
  private readonly environment: DeploymentEnvironment;
  private readonly payloadProvider?: () => Promise<Record<string, unknown> | null> | Record<string, unknown> | null;
  private timer: NodeJS.Timeout | null = null;
  private publishing = false;

  constructor(options: AgentHeartbeatServiceOptions) {
    this.supabase = options.supabase;
    this.lockController = options.lockController;
    this.intervalMs = Math.max(MIN_HEARTBEAT_INTERVAL_MS, options.intervalMs);
    this.component = options.component;
    this.kioskId = options.kioskId;
    this.environment = options.environment;
    this.payloadProvider = options.payloadProvider;
  }

  async start(): Promise<void> {
    if (this.timer) {
      return;
    }
    await this.publishHeartbeat();
    this.timer = setInterval(() => {
      this.publishHeartbeat().catch((error) => {
        console.warn('[HEARTBEAT] Failed to publish heartbeat metric', error);
      });
    }, this.intervalMs);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async publishHeartbeat(): Promise<void> {
    if (this.publishing) {
      return;
    }
    this.publishing = true;
    try {
      const [lockStatus, extraPayload] = await Promise.all([
        this.lockController.getStatus(),
        this.resolveExtraPayload(),
      ]);

      const payload: Record<string, unknown> = {
        component: this.component,
        environment: this.environment,
        kioskId: this.kioskId ?? null,
        locks: lockStatus,
        runtime: this.collectRuntimeSnapshot(),
      };

      if (extraPayload && Object.keys(extraPayload).length > 0) {
        payload.extra = extraPayload;
      }

      await this.supabase.recordMetric({
        metricType: 'agent_heartbeat',
        recordedAt: new Date(),
        valueText: 'ok',
        payload,
        source: 'agent-heartbeat-service',
      });
    } finally {
      this.publishing = false;
    }
  }

  private async resolveExtraPayload(): Promise<Record<string, unknown> | null> {
    if (!this.payloadProvider) {
      return null;
    }

    try {
      const result = await this.payloadProvider();
      if (!result || typeof result !== 'object') {
        return null;
      }
      return result;
    } catch (error) {
      console.warn('[HEARTBEAT] Failed to resolve extra payload', error);
      return null;
    }
  }

  private collectRuntimeSnapshot(): RuntimeSnapshot {
    const memory = process.memoryUsage();
    return {
      pid: process.pid,
      nodeVersion: process.version,
      platform: process.platform,
      uptimeMs: Math.round(process.uptime() * 1000),
      memory: {
        rssBytes: memory.rss,
        heapTotalBytes: memory.heapTotal,
        heapUsedBytes: memory.heapUsed,
        externalBytes: memory.external ?? 0,
        arrayBuffersBytes: memory.arrayBuffers ?? 0,
      },
    };
  }
}

interface RuntimeSnapshot {
  readonly pid: number;
  readonly nodeVersion: string;
  readonly platform: NodeJS.Platform;
  readonly uptimeMs: number;
  readonly memory: {
    readonly rssBytes: number;
    readonly heapTotalBytes: number;
    readonly heapUsedBytes: number;
    readonly externalBytes: number;
    readonly arrayBuffersBytes: number;
  };
}
