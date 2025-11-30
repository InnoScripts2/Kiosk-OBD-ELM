import { createClient } from '@supabase/supabase-js';
import type { Database } from '../integrations/supabase/types.js';
import { ReportService } from '../services/ReportService.js';
import { loadReportDeliveryWorkerConfig } from './report-delivery/config.js';
import { createQueueDefinitions, ReportDeliveryQueueProcessor } from './report-delivery/queue-processor.js';

async function main(): Promise<void> {
  const config = loadReportDeliveryWorkerConfig();
  const supabase = createClient<Database>(config.supabaseUrl, config.supabaseServiceRoleKey, {
    auth: { persistSession: false },
    global: {
      headers: {
        'x-client-info': 'kiosk-report-delivery-worker/1.0.0'
      }
    }
  });
  const reportService = new ReportService();
  const processors = createQueueDefinitions().map(
    (definition) =>
      new ReportDeliveryQueueProcessor({
        supabase,
        reportService,
        config,
        definition
      })
  );

  console.info(
    `[WORKER] Report delivery worker started · env=${config.environment}${config.kioskId ? ` · kiosk=${config.kioskId}` : ''}`
  );

  let stopped = false;
  const requestStop = (signal: NodeJS.Signals): void => {
    if (stopped) {
      return;
    }
    console.info(`[WORKER] Received ${signal}, stopping...`);
    stopped = true;
  };
  process.on('SIGINT', requestStop);
  process.on('SIGTERM', requestStop);

  while (!stopped) {
    let processed = 0;
    for (const processor of processors) {
      try {
        processed += await processor.processBatch();
      } catch (error) {
        console.error(`[WORKER][${processor.name}] Batch processing failed`, error);
      }
    }
    const delay = processed > 0 ? config.activePollIntervalMs : config.pollIntervalMs;
    await sleep(delay);
  }

  console.info('[WORKER] Report delivery worker stopped');
}

function sleep(durationMs: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, durationMs));
}

main().catch((error) => {
  console.error('[WORKER] Fatal error', error);
  process.exit(1);
});
