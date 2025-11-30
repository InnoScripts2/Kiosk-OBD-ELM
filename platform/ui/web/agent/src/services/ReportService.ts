/**
 * ReportService - генерация и отправка отчётов
 * Формат: HTML/PDF
 * Доставка: Email/SMS (в production), локальный файл (в DEV)
 */

export interface ThicknessReport {
  sessionId: string;
  timestamp: Date;
  vehicleType: string;
  measurements: Array<{
    zone: string;
    value: number;
    status: string;
  }>;
  analysis: {
    avgValue: number;
    deviations: number;
    recommendation: string;
  };
}

export interface DiagnosticsReport {
  sessionId: string;
  timestamp: Date;
  vehicleBrand: string;
  dtcCodes: Array<{
    code: string;
    description: string;
    status: 'info' | 'warning' | 'critical';
  }>;
  clearedCount?: number;
}

export class ReportService {
  /**
   * Генерировать HTML из данных отчёта
   */
  toHTML(data: ThicknessReport | DiagnosticsReport): string {
    if ('measurements' in data) {
      return this.generateThicknessHTML(data);
    } else {
      return this.generateDiagnosticsHTML(data);
    }
  }

  /**
   * Отправить отчёт по email (в production через SendGrid/SMTP)
   */
  async sendEmail(email: string, reportHTML: string, sessionId: string): Promise<{ success: boolean; messageId?: string }> {
    console.log(`[REPORT] Sending email to ${email} for session ${sessionId}`);
    
    // TODO: Интеграция с email провайдером
    // В DEV режиме сохраняем локально
    const devMode = process.env.APP_MODE === 'DEV';
    
    if (devMode) {
      console.log('[REPORT-DEV] Email content:', reportHTML.substring(0, 200) + '...');
      return { success: true, messageId: `dev_${Date.now()}` };
    }

    // TODO: Реальная отправка через SMTP/SendGrid
    throw new Error('Email sending not implemented for production mode');
  }

  /**
   * Отправить SMS с кратким резюме (через Twilio/SMSAero)
   */
  async sendSMS(phone: string, summary: string): Promise<{ success: boolean }> {
    console.log(`[REPORT] Sending SMS to ${phone}`);
    
    const devMode = process.env.APP_MODE === 'DEV';
    
    if (devMode) {
      console.log('[REPORT-DEV] SMS content:', summary);
      return { success: true };
    }

    // TODO: Реальная отправка через SMS провайдера
    throw new Error('SMS sending not implemented for production mode');
  }

  private generateThicknessHTML(report: ThicknessReport): string {
    return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Отчёт толщиномера - ${report.sessionId}</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    h1 { color: #333; }
    table { width: 100%; border-collapse: collapse; margin: 20px 0; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #4CAF50; color: white; }
  </style>
</head>
<body>
  <h1>Отчёт толщиномера</h1>
  <p>Дата: ${report.timestamp.toLocaleString('ru-RU')}</p>
  <p>Тип авто: ${report.vehicleType}</p>
  <h2>Замеры</h2>
  <table>
    <tr><th>Зона</th><th>Значение (мм)</th><th>Статус</th></tr>
    ${report.measurements.map(m => `<tr><td>${m.zone}</td><td>${m.value}</td><td>${m.status}</td></tr>`).join('')}
  </table>
  <h2>Анализ</h2>
  <p>Среднее значение: ${report.analysis.avgValue.toFixed(2)} мм</p>
  <p>Отклонений: ${report.analysis.deviations}</p>
  <p>Рекомендация: ${report.analysis.recommendation}</p>
</body>
</html>
    `.trim();
  }

  private generateDiagnosticsHTML(report: DiagnosticsReport): string {
    return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Отчёт диагностики - ${report.sessionId}</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    h1 { color: #333; }
    table { width: 100%; border-collapse: collapse; margin: 20px 0; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #2196F3; color: white; }
    .critical { color: red; font-weight: bold; }
    .warning { color: orange; }
    .info { color: green; }
  </style>
</head>
<body>
  <h1>Отчёт диагностики OBD-II</h1>
  <p>Дата: ${report.timestamp.toLocaleString('ru-RU')}</p>
  <p>Марка: ${report.vehicleBrand}</p>
  <h2>Коды ошибок (DTC)</h2>
  <table>
    <tr><th>Код</th><th>Описание</th><th>Статус</th></tr>
    ${report.dtcCodes.map(dtc => `<tr><td>${dtc.code}</td><td>${dtc.description}</td><td class="${dtc.status}">${dtc.status}</td></tr>`).join('')}
  </table>
  ${report.clearedCount ? `<p>Сброшено ошибок: ${report.clearedCount}</p>` : ''}
</body>
</html>
    `.trim();
  }
}
