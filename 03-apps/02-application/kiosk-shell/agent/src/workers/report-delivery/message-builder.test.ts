import {
  composeDiagnosticsEmailSubject,
  composeDiagnosticsSmsBody,
  composeThicknessEmailSubject,
  composeThicknessSmsBody
} from './message-builder';

describe('report delivery message builder', () => {
  test('diagnostics subject uses vehicle label', () => {
    const metadata = {
      vehicle: {
        brand: 'Toyota',
        model: 'Camry'
      }
    };
    expect(composeDiagnosticsEmailSubject('sess-1', metadata)).toBe('Toyota Camry · отчёт sess-1');
  });

  test('diagnostics sms falls back to default when summary missing', () => {
    const message = composeDiagnosticsSmsBody('sess-2', null);
    expect(message).toContain('sess-2');
    expect(message).toContain('диагностики');
  });

  test('thickness sms uses summary text', () => {
    const metadata = {
      summary: {
        headline: 'Замеры готовы',
        status: 'OK'
      }
    };
    expect(composeThicknessSmsBody('sess-3', metadata)).toContain('Замеры готовы (OK)');
  });

  test('thickness email falls back to default title', () => {
    expect(composeThicknessEmailSubject('sess-4', null)).toBe('Толщиномер ЛКП · результаты sess-4');
  });
});
