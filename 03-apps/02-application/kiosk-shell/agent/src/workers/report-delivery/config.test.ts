import { loadReportDeliveryWorkerConfig, resolveEnvironment } from './config';

describe('report delivery worker config', () => {
  test('resolveEnvironment maps known values', () => {
    expect(resolveEnvironment('PROD')).toBe('prod');
    expect(resolveEnvironment('production')).toBe('prod');
    expect(resolveEnvironment('qa')).toBe('qa');
    expect(resolveEnvironment('TEST')).toBe('qa');
    expect(resolveEnvironment('dev')).toBe('dev');
    expect(resolveEnvironment(undefined)).toBe('dev');
  });

  test('loadReportDeliveryWorkerConfig reads defaults and env', () => {
    const config = loadReportDeliveryWorkerConfig({
      SUPABASE_URL: 'https://example.supabase.co',
      SUPABASE_SERVICE_ROLE_KEY: 'service-key',
      APP_MODE: 'PROD',
      REPORT_WORKER_BATCH_SIZE: '10',
      REPORT_WORKER_POLL_MS: '1000',
      REPORT_WORKER_ACTIVE_POLL_MS: '200',
      REPORT_WORKER_STALE_PROCESSING_MS: '60000',
      REPORT_WORKER_MAX_ATTEMPTS: '2',
      KIOSK_SERIAL_NUMBER: 'K-01'
    });

    expect(config.environment).toBe('prod');
    expect(config.kioskId).toBe('K-01');
    expect(config.maxBatchSize).toBe(10);
    expect(config.pollIntervalMs).toBe(1000);
    expect(config.activePollIntervalMs).toBe(200);
    expect(config.staleProcessingThresholdMs).toBe(60000);
    expect(config.maxAttempts).toBe(2);
  });

  test('loadReportDeliveryWorkerConfig throws when supabase env missing', () => {
    expect(() => loadReportDeliveryWorkerConfig({ SUPABASE_URL: '', SUPABASE_SERVICE_ROLE_KEY: '' })).toThrow(
      /SUPABASE_URL/
    );
  });
});
