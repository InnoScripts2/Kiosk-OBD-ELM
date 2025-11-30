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
      dtcCodes: [
        { code: 'P0420', description: 'Catalyst System Efficiency Below Threshold', status: 'warning' },
        { code: 'P0171', description: 'System Too Lean Bank 1', status: 'critical' },
      ],
      clearedCount: 1,
    };

    const html = service.toHTML(report);

    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('Отчёт диагностики');
    expect(html).toContain('test_session_2');
    expect(html).toContain('Toyota');
    expect(html).toContain('P0420');
    expect(html).toContain('P0171');
    expect(html).toContain('Сброшено ошибок: 1');
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
