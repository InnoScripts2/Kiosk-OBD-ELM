import { ReportService, ThicknessReport, DiagnosticsReport } from '../services/ReportService';

describe('ReportService', () => {
  let service: ReportService;

  beforeEach(() => {
    service = new ReportService();
  });

  test('генерирует HTML для толщиномера', () => {
    const report: ThicknessReport = {
      sessionId: 'test_session_1',
      timestamp: new Date('2025-11-23T12:00:00Z'),
      vehicleType: 'Седан',
      measurements: [
        { zone: 'Капот', value: 120, status: 'OK' },
        { zone: 'Крыша', value: 115, status: 'OK' },
      ],
      analysis: {
        avgValue: 117.5,
        deviations: 0,
        recommendation: 'Состояние покрытия в норме',
      },
    };

    const html = service.toHTML(report);

    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('Отчёт толщиномера');
    expect(html).toContain('test_session_1');
    expect(html).toContain('Седан');
    expect(html).toContain('Капот');
    expect(html).toContain('120');
    expect(html).toContain('117.5');
  });

  test('генерирует HTML для диагностики', () => {
    const report: DiagnosticsReport = {
      sessionId: 'test_session_2',
      timestamp: new Date('2025-11-23T12:00:00Z'),
      vehicleBrand: 'Toyota',
      vin: 'JTDKB20U993456789',
      adapter: 'ELM327 BLE',
      scanDuration: 75,
      price: 480,
      dtcCodes: [
        {
          code: 'P0420',
          description: 'Catalyst System Efficiency Below Threshold',
          severity: 'warning',
          system: 'Powertrain',
          recommendation: 'Проверить состояние катализатора',
        },
        {
          code: 'P0171',
          description: 'System Too Lean Bank 1',
          severity: 'critical',
          system: 'Fuel system',
          recommendation: 'Диагностика впрыска топлива',
        },
      ],
      stats: {
        totalCodes: 2,
        critical: 1,
        warnings: 1,
        info: 0,
        cleared: 1,
      },
      milStatus: 'on',
      contact: { email: 'client@example.com' },
      mode: 'DEV',
      clearResult: {
        timestamp: new Date('2025-11-23T12:10:00Z'),
        clearedCodes: ['P0171'],
        success: true,
        confirmationMethod: 'client_consent',
      },
    };

    const html = service.toHTML(report);

    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('Отчёт диагностики OBD-II');
    expect(html).toContain('test_session_2');
    expect(html).toContain('Toyota');
    expect(html).toContain('ELM327 BLE');
    expect(html).toContain('Обнаружено 2 кодов');
    expect(html).toContain('Powertrain');
    expect(html).toContain('Результат сброса ошибок');
    expect(html).toContain('[МОК-РЕЖИМ]');
  });

  test('sendEmail в DEV режиме возвращает success', async () => {
    process.env.APP_MODE = 'DEV';
    
    const result = await service.sendEmail('test@example.com', '<html>Report</html>', 'session_123');

    expect(result.success).toBe(true);
    expect(result.messageId).toBeDefined();
  });

  test('sendSMS в DEV режиме возвращает success', async () => {
    process.env.APP_MODE = 'DEV';
    
    const result = await service.sendSMS('+79001234567', 'Отчёт готов');

    expect(result.success).toBe(true);
  });
});
