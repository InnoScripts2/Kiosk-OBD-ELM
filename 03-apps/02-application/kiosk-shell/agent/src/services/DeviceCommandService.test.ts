import { afterEach, beforeEach, describe, expect, jest, test } from '@jest/globals';
import { rm } from 'node:fs/promises';
import path from 'node:path';
import type { DeviceCommandConfig } from '../config/agent-config.js';
import type { DeviceCommandRecord, DeviceCommandStatus, SupabaseAgentClient } from '../integrations/supabase/agent-client.js';
import { DeviceCommandService, type DeviceCommandServiceOptions } from './DeviceCommandService.js';
import type { DeviceType, LockStatus } from './LockController.js';

const baseCommand: DeviceCommandRecord = {
  command_id: 'cmd-test',
  command_type: 'PING',
  status: 'received',
  requested_at: new Date('2025-11-30T10:00:00.000Z').toISOString(),
  acknowledged_at: null,
  started_at: null,
  completed_at: null,
  latency_ms: null,
  attempt: 0,
  payload: {},
  result_payload: null,
  error_message: null,
  source: 'admin',
  kiosk_id: 'KIOSK-77',
  environment: 'dev',
  mdm_device_id: null,
  metadata: null,
  created_at: new Date('2025-11-30T10:00:00.000Z').toISOString(),
  updated_at: new Date('2025-11-30T10:00:00.000Z').toISOString(),
};

const defaultConfig: DeviceCommandConfig = {
  pollIntervalMs: 500,
  activePollIntervalMs: 100,
  maxBatchSize: 5,
  includeBroadcast: true,
  rebootGracePeriodMs: 1200,
};

type SupabaseMock = jest.Mocked<
  Pick<SupabaseAgentClient, 'fetchDeviceCommands' | 'updateDeviceCommand' | 'recordDeviceEvent'>
>;

const createSupabaseMock = (): SupabaseMock => {
  return {
    fetchDeviceCommands: jest
      .fn<SupabaseAgentClient['fetchDeviceCommands']>()
      .mockResolvedValue([] as DeviceCommandRecord[]),
    updateDeviceCommand: jest
      .fn<SupabaseAgentClient['updateDeviceCommand']>()
      .mockResolvedValue(true),
    recordDeviceEvent: jest.fn<SupabaseAgentClient['recordDeviceEvent']>().mockResolvedValue(undefined),
  } as SupabaseMock;
};

const createLockStatus = (): LockStatus => ({
  thickness: 'closed',
  adapter: 'open',
  connected: true,
});

type LockControllerMock = jest.Mocked<Required<NonNullable<DeviceCommandServiceOptions['lockController']>>>;

const createLockControllerMock = (): LockControllerMock => ({
  getStatus: jest.fn<() => Promise<LockStatus>>().mockResolvedValue(createLockStatus()),
  openSlot: jest.fn<(deviceType: DeviceType) => Promise<void>>().mockResolvedValue(undefined),
  closeSlot: jest.fn<(deviceType: DeviceType) => Promise<void>>().mockResolvedValue(undefined),
});

const artifactDirectories: string[] = [];

const createArtifactsDirectory = (): string => {
  const dir = path.join(
    process.cwd(),
    'tmp',
    'device-command-tests',
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`,
  );
  artifactDirectories.push(dir);
  return dir;
};

const waitForServiceCycle = async (): Promise<void> => {
  await jest.advanceTimersByTimeAsync(100);
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
};

type UpdateCommandCall = Parameters<SupabaseMock['updateDeviceCommand']>;

const waitForCommandUpdate = async (
  supabase: SupabaseMock,
  status: DeviceCommandStatus,
): Promise<UpdateCommandCall | undefined> => {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const call = supabase.updateDeviceCommand.mock.calls.find(([, payload]) => payload.status === status);
    if (call) {
      return call as UpdateCommandCall;
    }
    await Promise.resolve();
    await jest.advanceTimersByTimeAsync(0);
  }
  return undefined;
};

const invokeProcessCommand = async (
  service: DeviceCommandService,
  command: DeviceCommandRecord,
): Promise<void> => {
  const processor = service as unknown as {
    processCommand(cmd: DeviceCommandRecord): Promise<void>;
  };
  await processor.processCommand(command);
};

describe('DeviceCommandService', () => {
  beforeEach(() => {
    jest.useFakeTimers({ now: new Date('2025-11-30T10:00:01.000Z') });
  });

  afterEach(async () => {
    jest.useRealTimers();
    jest.restoreAllMocks();
    await Promise.all(artifactDirectories.map((dir) => rm(dir, { recursive: true, force: true })));
    artifactDirectories.length = 0;
  });

  test('processes PING command end-to-end and records success event', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = { ...baseCommand };
    const lockController = createLockControllerMock();
    const runtimeProvider = jest
      .fn<() => Promise<Record<string, unknown>>>()
      .mockResolvedValue({ uptimeMs: 1234 });

    supabase.fetchDeviceCommands
      .mockResolvedValueOnce([command])
      .mockResolvedValue([]);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
      kioskId: 'KIOSK-77',
      lockController,
      runtimePayloadProvider: runtimeProvider,
    });

    service.start();
    await waitForServiceCycle();

    expect(supabase.fetchDeviceCommands).toHaveBeenCalledWith(
      expect.objectContaining({ kioskId: 'KIOSK-77', includeBroadcast: true }),
    );

    expect(supabase.updateDeviceCommand).toHaveBeenCalledWith(
      'cmd-test',
      expect.objectContaining({ status: 'acknowledged' }),
      expect.objectContaining({ matchStatuses: ['received'] }),
    );

    expect(supabase.updateDeviceCommand).toHaveBeenCalledWith(
      'cmd-test',
      expect.objectContaining({ status: 'in_progress' }),
      expect.objectContaining({ matchStatuses: ['acknowledged', 'in_progress'] }),
    );

    const successCall = await waitForCommandUpdate(supabase, 'succeeded');
    expect(successCall?.[1]).toEqual(expect.objectContaining({ status: 'succeeded' }));

    expect(supabase.recordDeviceEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'device_command_completed',
        commandStatus: 'succeeded',
      }),
    );

    expect(runtimeProvider).toHaveBeenCalledTimes(1);
    expect(lockController.getStatus).toHaveBeenCalledTimes(1);

    service.stop();
  });

  test('unsupported command results in failed completion event', async () => {
    const supabase = createSupabaseMock();
    const unknownCommand: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-unsupported',
      command_type: 'DO_SOMETHING',
    };

    supabase.fetchDeviceCommands.mockResolvedValueOnce([unknownCommand]).mockResolvedValue([]);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
    });

    service.start();
    await waitForServiceCycle();

    const failedCall = await waitForCommandUpdate(supabase, 'failed');
    expect(failedCall?.[1]).toEqual(expect.objectContaining({ status: 'failed' }));

    expect(supabase.recordDeviceEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'device_command_completed',
        commandStatus: 'failed',
      }),
    );

    service.stop();
  });

  test('LOCK_CONTROL command opens slot and returns lock snapshot', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-lock-open',
      command_type: 'LOCK_CONTROL',
      payload: { action: 'open', deviceType: 'adapter', sessionId: 'session-55' },
    };
    const lockController = createLockControllerMock();
    lockController.getStatus.mockResolvedValue({ thickness: 'closed', adapter: 'open', connected: true });

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
      kioskId: 'KIOSK-LOCK',
      lockController,
    });

    service.start();
    await waitForServiceCycle();

    expect(lockController.openSlot).toHaveBeenCalledWith('adapter');

    const completionCall = await waitForCommandUpdate(supabase, 'succeeded');
    expect(completionCall?.[1].resultPayload).toEqual(
      expect.objectContaining({
        action: 'open',
        deviceType: 'adapter',
        sessionId: 'session-55',
        kioskId: 'KIOSK-LOCK',
      }),
    );

    service.stop();
  });

  test('LOCK_CONTROL fails when lock controller unavailable', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-lock-fail',
      command_type: 'LOCK_CONTROL',
      payload: { action: 'close', deviceType: 'thickness' },
    };

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
    });

    service.start();
    await waitForServiceCycle();

    const completionCall = await waitForCommandUpdate(supabase, 'failed');
    expect(completionCall?.[1].errorMessage).toBe('lock_controller_unavailable');

    service.stop();
  });

  test('LOCK_CONTROL rejects invalid payload', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-lock-invalid',
      command_type: 'LOCK_CONTROL',
      payload: { action: 'open' },
    };
    const lockController = createLockControllerMock();

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
      lockController,
    });

    service.start();
    await waitForServiceCycle();

    expect(lockController.openSlot).not.toHaveBeenCalled();
    const completionCall = await waitForCommandUpdate(supabase, 'failed');
    expect(completionCall?.[1].errorMessage).toBe('lock_control_invalid_payload:deviceType');

    service.stop();
  });

  test('REBOOT command schedules agent restart', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-reboot',
      command_type: 'REBOOT',
      payload: { delayMs: 700, reason: 'manual_test' },
    };

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);
    const killSpy = jest.spyOn(process, 'kill').mockReturnValue(true);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: { ...defaultConfig, rebootGracePeriodMs: 600 },
    });

    service.start();
    await waitForServiceCycle();

    const completionCall = await waitForCommandUpdate(supabase, 'succeeded');
    expect(completionCall?.[1].resultPayload).toEqual(
      expect.objectContaining({ delayMs: 700, reason: 'manual_test' }),
    );

    await jest.advanceTimersByTimeAsync(700);
    expect(killSpy).toHaveBeenCalledWith(process.pid, 'SIGTERM');

    service.stop();
    killSpy.mockRestore();
  });

  test('SYNC_CONFIG command reloads config and records summary', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-sync-config',
      command_type: 'SYNC_CONFIG',
      payload: {
        requestedBy: 'ops@example.com',
        sections: ['deviceCommands', 'heartbeat'],
        reason: 'manual refresh',
      },
    };

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);

    const loader = jest.fn<() => DeviceCommandConfig>().mockReturnValue({
      pollIntervalMs: 800,
      activePollIntervalMs: 150,
      maxBatchSize: 9,
      includeBroadcast: false,
      rebootGracePeriodMs: 2500,
    });

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
      kioskId: 'KIOSK-SYNC',
      deviceCommandConfigLoader: loader,
      artifactsDirectory: createArtifactsDirectory(),
    });

    await invokeProcessCommand(service, command);

    expect(loader).toHaveBeenCalledTimes(1);

    const completionCall = await waitForCommandUpdate(supabase, 'succeeded');
    expect(completionCall).toBeDefined();
    expect(completionCall?.[1].resultPayload).toEqual(
      expect.objectContaining({
        kioskId: 'KIOSK-SYNC',
        requestedBy: 'ops@example.com',
        deviceCommandConfig: expect.objectContaining({ pollIntervalMs: 800, includeBroadcast: false }),
        changedFields: expect.arrayContaining(['pollIntervalMs', 'includeBroadcast']),
        artifactPath: expect.any(String),
      }),
    );

    service.stop();
  });

  test('UPDATE_APP command stores request and schedules restart when requested', async () => {
    const supabase = createSupabaseMock();
    const command: DeviceCommandRecord = {
      ...baseCommand,
      command_id: 'cmd-update-app',
      command_type: 'UPDATE_APP',
      payload: {
        version: '2.0.1',
        channel: 'beta',
        artifactUrl: 'https://cdn.example.com/build.zip',
        checksum: 'abc123',
        notes: 'nightly rollout',
        requestedBy: 'ops@example.com',
        forceRestart: true,
        restartDelayMs: 900,
      },
    };

    supabase.fetchDeviceCommands.mockResolvedValueOnce([command]).mockResolvedValue([]);
    const killSpy = jest.spyOn(process, 'kill').mockReturnValue(true);

    const service = new DeviceCommandService({
      supabase: supabase as unknown as SupabaseAgentClient,
      config: defaultConfig,
      kioskId: 'KIOSK-UPDATE',
      artifactsDirectory: createArtifactsDirectory(),
    });

    await invokeProcessCommand(service, command);

    const completionCall = await waitForCommandUpdate(supabase, 'succeeded');
    expect(completionCall?.[1].resultPayload).toEqual(
      expect.objectContaining({
        version: '2.0.1',
        channel: 'beta',
        artifactUrl: 'https://cdn.example.com/build.zip',
        checksum: 'abc123',
        requestedBy: 'ops@example.com',
        forceRestart: true,
        restartDelayMs: 900,
        artifactPath: expect.any(String),
      }),
    );

    await jest.advanceTimersByTimeAsync(900);
    expect(killSpy).toHaveBeenCalledWith(process.pid, 'SIGTERM');

    service.stop();
    killSpy.mockRestore();
  });
});
