import { createServer } from 'http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PaymentService } from './services/PaymentService.js';
import { LockController } from './services/LockController.js';
import { ReportService } from './services/ReportService.js';
import { ReportIngestService } from './services/ReportIngestService.js';
import { createAgentApp, type StaticUiOptions } from './app.js';
import { DeviceStatusGateway } from './websocket/device-status-gateway.js';
import { loadAgentConfig } from './config/agent-config.js';
import { SupabaseAgentClient } from './integrations/supabase/agent-client.js';
import { AgentHeartbeatService } from './services/AgentHeartbeatService.js';
import { DeviceCommandService } from './services/DeviceCommandService.js';

async function bootstrap(): Promise<void> {
  const paymentMock = process.env.PAYMENT_MOCK !== 'false';
  const appMode = process.env.APP_MODE || 'DEV';
  const port = Number.parseInt(process.env.NODE_AGENT_PORT || process.env.PORT || '7070', 10);
  const agentConfig = loadAgentConfig();

  console.log(`[AGENT] Starting kiosk-shell agent in ${appMode} mode`);
  console.log(`[AGENT] Payment mock: ${paymentMock}`);

  const paymentService = new PaymentService(paymentMock);
  const lockController = new LockController();
  const reportService = new ReportService();
  const supabaseClient = new SupabaseAgentClient(agentConfig.supabase);
  const staticUi = resolveStaticUiOptions();
  const reportIngestService = new ReportIngestService({
    supabase: supabaseClient,
    kioskId: agentConfig.supabase.kioskId,
    environment: agentConfig.supabase.environment,
    generationTimeoutMs: agentConfig.reporting.generationTimeoutMs,
    deliverySlaMs: agentConfig.reporting.deliverySlaMs,
  });

  await lockController.initialize();

  const heartbeatService = new AgentHeartbeatService({
    supabase: supabaseClient,
    lockController,
    intervalMs: agentConfig.heartbeat.intervalMs,
    component: agentConfig.heartbeat.component,
    kioskId: agentConfig.supabase.kioskId,
    environment: agentConfig.supabase.environment,
  });

  await heartbeatService.start();

  const deviceCommandService = new DeviceCommandService({
    supabase: supabaseClient,
    config: agentConfig.deviceCommands,
    kioskId: agentConfig.supabase.kioskId,
    lockController,
    runtimePayloadProvider: collectRuntimeSnapshot,
    deviceCommandConfigLoader: () => loadAgentConfig().deviceCommands,
  });
  deviceCommandService.start();

  const app = createAgentApp({
    paymentService,
    lockController,
    reportService,
    reportIngestService,
    staticUi,
  });
  const server = createServer(app);
  const wsGateway = new DeviceStatusGateway({ server, lockController });

  await new Promise<void>((resolve) => {
    server.listen(port, () => {
      console.log(`[AGENT] HTTP server listening on http://localhost:${port}`);
      resolve();
    });
  });

  const shutdown = async (): Promise<void> => {
    console.log('[AGENT] Shutting down...');
    heartbeatService.stop();
    deviceCommandService.stop();
    await wsGateway.stop().catch((error) => console.warn('[AGENT] Failed to stop WS gateway', error));
    await lockController.shutdown().catch((error) => console.warn('[AGENT] Failed to shutdown lock controller', error));
    await new Promise<void>((resolve) => server.close(() => resolve()));
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

function resolveStaticUiOptions(): StaticUiOptions | undefined {
  const routePrefix = process.env.KIOSK_UI_ROUTE_PREFIX?.trim() || '/ui';
  const indexFile = process.env.KIOSK_UI_INDEX_FILE?.trim() || 'index.html';
  const candidates = collectStaticUiCandidates();

  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }
    const absolute = path.resolve(candidate);
    if (fs.existsSync(absolute) && fs.statSync(absolute).isDirectory()) {
      console.log(`[AGENT] Hosting kiosk UI from ${absolute} at ${routePrefix}`);
      return {
        rootDir: absolute,
        routePrefix,
        indexFile,
      };
    }
  }

  if (candidates.length > 0) {
    console.warn(`[AGENT] Static kiosk UI bundle not found. Looked in: ${candidates.join(', ')}`);
  }
  return undefined;
}

function collectStaticUiCandidates(): string[] {
  const candidates = new Set<string>();
  const envPath = process.env.KIOSK_UI_DIST_PATH?.trim();
  if (envPath) {
    candidates.add(envPath);
  }

  const runtimeDir = path.dirname(fileURLToPath(import.meta.url));
  candidates.add(path.resolve(runtimeDir, '../../../../../platform/ui/web/kiosk-frontend/dist'));
  candidates.add(path.resolve(process.cwd(), '../../../../platform/ui/web/kiosk-frontend/dist'));

  return Array.from(candidates);
}

function collectRuntimeSnapshot(): Record<string, unknown> {
  const memory = process.memoryUsage();
  return {
    nodeVersion: process.version,
    uptimeMs: Math.round(process.uptime() * 1000),
    pid: process.pid,
    memory: {
      rssBytes: memory.rss,
      heapTotalBytes: memory.heapTotal,
      heapUsedBytes: memory.heapUsed,
      externalBytes: memory.external ?? 0,
    },
  };
}

bootstrap().catch((error) => {
  console.error('[AGENT] Failed to start kiosk-shell agent', error);
  process.exit(1);
});
