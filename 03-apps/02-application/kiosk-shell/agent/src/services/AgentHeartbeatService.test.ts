import { jest, describe, expect, afterEach, test } from '@jest/globals';
import type { SupabaseAgentClient } from '../integrations/supabase/agent-client.js';
import type { LockStatus } from './LockController.js';
import { AgentHeartbeatService } from './AgentHeartbeatService.js';

describe('AgentHeartbeatService', () => {
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  type SupabaseMock = {
    recordMetric: jest.MockedFunction<SupabaseAgentClient['recordMetric']>;
  };

  type RuntimePayload = {
    runtime?: {
      nodeVersion?: string;
      platform?: string;
      uptimeMs?: number;
      memory?: {
        rssBytes?: number;
        heapUsedBytes?: number;
        heapTotalBytes?: number;
      };
    };
  };

  const createSupabaseMock = (): SupabaseMock => ({
    recordMetric: jest.fn<SupabaseAgentClient['recordMetric']>().mockResolvedValue(undefined),
  });

  const createLockControllerMock = (status: LockStatus = {
    thickness: 'closed',
    adapter: 'closed',
    connected: true,
  }): { getStatus: () => Promise<LockStatus> } => {
    const getStatusMock = jest.fn<() => Promise<LockStatus>>().mockResolvedValue(status);
    return { getStatus: getStatusMock };
  };

  test('publishes heartbeat immediately and on interval', async () => {
    jest.useFakeTimers();
    const supabase = createSupabaseMock();
    const lockController = createLockControllerMock();
    const service = new AgentHeartbeatService({
      supabase: supabase as unknown as SupabaseAgentClient,
      lockController,
      intervalMs: 1000,
      component: 'agent_runtime',
      environment: 'dev',
    });

    await service.start();
    expect(supabase.recordMetric).toHaveBeenCalledTimes(1);

    const firstCall = supabase.recordMetric.mock.calls[0]?.[0];
    const payload = firstCall?.payload as RuntimePayload | undefined;
    expect(payload?.runtime).toBeDefined();
    expect(payload?.runtime).toMatchObject({
      nodeVersion: process.version,
      platform: process.platform,
    });
    expect(typeof payload?.runtime?.uptimeMs).toBe('number');
    expect(payload?.runtime?.memory).toEqual(expect.objectContaining({
      rssBytes: expect.any(Number),
      heapUsedBytes: expect.any(Number),
    }));

    await jest.advanceTimersByTimeAsync(1000);
    expect(supabase.recordMetric).toHaveBeenCalledTimes(2);

    service.stop();
  });

  test('adds extra payload data and kiosk id', async () => {
    const supabase = createSupabaseMock();
    const lockController = createLockControllerMock();
    const service = new AgentHeartbeatService({
      supabase: supabase as unknown as SupabaseAgentClient,
      lockController,
      intervalMs: 5000,
      component: 'agent_runtime',
      kioskId: 'KIOSK-42',
      environment: 'prod',
      payloadProvider: (): Record<string, unknown> => ({ queueDepth: 3 }),
    });

    await service.start();

    expect(supabase.recordMetric).toHaveBeenCalledTimes(1);
    const call = supabase.recordMetric.mock.calls[0]?.[0];
    expect(call?.metricType).toBe('agent_heartbeat');
    expect(call?.payload).toMatchObject({
      component: 'agent_runtime',
      kioskId: 'KIOSK-42',
      extra: { queueDepth: 3 },
      locks: {
        thickness: 'closed',
        adapter: 'closed',
        connected: true,
      },
    });
    expect(call?.source).toBe('agent-heartbeat-service');
    const runtime = (call?.payload as RuntimePayload | undefined)?.runtime;
    expect(runtime).toBeDefined();
    expect(runtime?.memory).toEqual(expect.objectContaining({
      heapTotalBytes: expect.any(Number),
    }));

    service.stop();
  });

  test('stop prevents future heartbeats', async () => {
    jest.useFakeTimers();
    const supabase = createSupabaseMock();
    const lockController = createLockControllerMock();
    const service = new AgentHeartbeatService({
      supabase: supabase as unknown as SupabaseAgentClient,
      lockController,
      intervalMs: 1000,
      component: 'agent_runtime',
      environment: 'dev',
    });

    await service.start();
    expect(supabase.recordMetric).toHaveBeenCalledTimes(1);

    service.stop();
    await jest.advanceTimersByTimeAsync(3000);
    expect(supabase.recordMetric).toHaveBeenCalledTimes(1);
  });
});
