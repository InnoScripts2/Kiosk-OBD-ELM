import type { Server } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import type { LockController, LockStatus } from '../services/LockController.js';

interface DeviceStatusGatewayOptions {
  server: Server;
  lockController: Pick<LockController, 'getStatus'>;
  intervalMs?: number;
}

interface DeviceStatusPayload {
  status: 'ready' | 'disconnected';
  progress: number;
  message: string;
  locks: LockStatus;
}

interface DeviceStatusEnvelope {
  type: 'status-update';
  payload: DeviceStatusPayload;
}

export class DeviceStatusGateway {
  private readonly wss: WebSocketServer;
  private readonly lockController: Pick<LockController, 'getStatus'>;
  private readonly intervalMs: number;
  private broadcastTimer: NodeJS.Timeout | null = null;

  constructor(options: DeviceStatusGatewayOptions) {
    this.lockController = options.lockController;
    this.intervalMs = options.intervalMs ?? 5000;
    this.wss = new WebSocketServer({ server: options.server, path: '/ws/obd' });

    this.wss.on('connection', (socket) => {
      this.pushSnapshot(socket).catch((error) => {
        console.warn('[ws] Failed to push initial snapshot', error);
      });
    });

    this.startBroadcastLoop();
  }

  async pushSnapshot(socket: WebSocket): Promise<void> {
    const payload = await this.buildPayload();
    const envelope: DeviceStatusEnvelope = { type: 'status-update', payload };
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(envelope));
    }
  }

  private startBroadcastLoop(): void {
    if (this.broadcastTimer) {
      clearInterval(this.broadcastTimer);
    }
    this.broadcastTimer = setInterval(() => {
      this.broadcast().catch((error) => {
        console.warn('[ws] Failed to broadcast device status', error);
      });
    }, this.intervalMs);
  }

  private async broadcast(): Promise<void> {
    const payload = await this.buildPayload();
    const envelope: DeviceStatusEnvelope = { type: 'status-update', payload };
    const serialized = JSON.stringify(envelope);

    for (const client of this.wss.clients) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(serialized);
      }
    }
  }

  private async buildPayload(): Promise<DeviceStatusPayload> {
    const locks = await this.lockController.getStatus();
    const isReady = locks.connected && !locks.error;
    return {
      status: isReady ? 'ready' : 'disconnected',
      progress: isReady ? 100 : 0,
      message: locks.error ?? (isReady ? 'Устройства подключены' : 'Нет связи с контроллером'),
      locks,
    };
  }

  async stop(): Promise<void> {
    if (this.broadcastTimer) {
      clearInterval(this.broadcastTimer);
      this.broadcastTimer = null;
    }
    await new Promise<void>((resolve) => {
      this.wss.close(() => resolve());
    });
  }
}