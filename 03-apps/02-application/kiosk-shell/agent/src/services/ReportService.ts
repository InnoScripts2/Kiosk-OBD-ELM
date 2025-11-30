/**
 * ReportService - генерация и отправка отчётов
 * Формат: HTML/PDF
 * Доставка: Email/SMS (в production), локальный файл (в DEV)
 */

type AppMode = 'DEV' | 'QA' | 'PROD';

interface ContactInfo {
  email?: string;
  phone?: string;
}

interface BaseReport {
  sessionId: string;
  timestamp: Date;
  contact?: ContactInfo;
  mode?: AppMode;
}

export interface ThicknessReport extends BaseReport {
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

type DiagnosticsSeverity = 'info' | 'warning' | 'critical';

export interface DiagnosticsStats {
  totalCodes: number;
  critical: number;
  warnings: number;
  info: number;
  cleared: number;
}

export interface ClearDtcResult {
  timestamp: Date;
  clearedCodes: string[];
  success: boolean;
  confirmationMethod: string;
}

export interface DiagnosticsReport extends BaseReport {
  vehicleBrand: string;
  vin?: string;
  adapter: string;
  scanDuration: number;
  price: number;
  dtcCodes: Array<{
    code: string;
    description: string;
    severity: DiagnosticsSeverity;
    system?: string;
    recommendation?: string;
  }>;
  stats: DiagnosticsStats;
  milStatus: 'on' | 'off';
  clearResult?: ClearDtcResult;
}

const escapeHtml = (value: string): string =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(value);

const formatDuration = (seconds: number): string => {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '—';
  }

  const totalSeconds = Math.floor(seconds);
  const minutes = Math.floor(totalSeconds / 60);
  const restSeconds = totalSeconds % 60;

  if (minutes === 0) {
    return `${restSeconds} сек`;
  }

  return `${minutes} мин ${restSeconds.toString().padStart(2, '0')} сек`;
};

const formatTimestamp = (timestamp: Date): string =>
  new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(timestamp);

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
  async sendEmail(
    email: string,
    reportHTML: string,
    sessionId: string,
    options?: { subject?: string }
  ): Promise<{ success: boolean; messageId?: string }> {
    const subject = options?.subject;
    console.log(
      `[REPORT] Sending email to ${email} for session ${sessionId}${subject ? ` · subject="${subject}"` : ''}`
    );
    
    // TODO: Интеграция с email провайдером
    // В DEV режиме сохраняем локально
    const devMode = process.env.APP_MODE === 'DEV';
    
    if (devMode) {
      if (subject) {
        console.log('[REPORT-DEV] Email subject:', subject);
      }
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
    const severityMap: Record<DiagnosticsSeverity, { label: string; className: string }> = {
      info: { label: 'Инфо', className: 'info' },
      warning: { label: 'Предупреждение', className: 'warning' },
      critical: { label: 'Критично', className: 'critical' },
    };

    const hasCodes = report.dtcCodes.length > 0;
    const dtcRows = hasCodes
      ? report.dtcCodes
          .map((dtc, index) => {
            const severityInfo = severityMap[dtc.severity];
            return `
      <tr>
        <td>${index + 1}</td>
        <td class="font-mono">${escapeHtml(dtc.code)}</td>
        <td>${escapeHtml(dtc.description)}</td>
        <td>${dtc.system ? escapeHtml(dtc.system) : '—'}</td>
        <td><span class="badge ${severityInfo.className}">${severityInfo.label}</span></td>
        <td>${dtc.recommendation ? escapeHtml(dtc.recommendation) : '—'}</td>
      </tr>`;
          })
          .join('')
      : '<tr><td colspan="6" class="text-center text-muted">Ошибок не обнаружено</td></tr>';

    const milOn = report.milStatus === 'on';
    const contactLines: string[] = [];

    if (report.contact?.email) {
      contactLines.push(`Email: ${escapeHtml(report.contact.email)}`);
    }

    if (report.contact?.phone) {
      contactLines.push(`Телефон: ${escapeHtml(report.contact.phone)}`);
    }

    const contactValue = contactLines.join('<br/>');

    const metaItems = [
      { label: 'Дата и время', value: formatTimestamp(report.timestamp) },
      { label: 'Марка авто', value: escapeHtml(report.vehicleBrand) },
      report.vin ? { label: 'VIN', value: escapeHtml(report.vin) } : undefined,
      { label: 'Адаптер', value: escapeHtml(report.adapter) },
      { label: 'Длительность сканирования', value: formatDuration(report.scanDuration) },
      { label: 'Стоимость услуги', value: formatCurrency(report.price) },
      {
        label: 'MIL Status',
        value: `<span class="mil-status ${milOn ? 'on' : 'off'}">${milOn ? 'Check Engine включен' : 'Check Engine выключен'}</span>`,
      },
      contactValue ? { label: 'Контакты', value: contactValue } : undefined,
    ].filter((item): item is { label: string; value: string } => Boolean(item));

    const summaryCards = [
      { label: 'Всего кодов', value: report.stats.totalCodes, className: 'total' },
      { label: 'Критические', value: report.stats.critical, className: 'critical' },
      { label: 'Предупреждения', value: report.stats.warnings, className: 'warning' },
      { label: 'Информационные', value: report.stats.info, className: 'info' },
      { label: 'Сброшено', value: report.stats.cleared, className: 'cleared' },
    ];

    const devBadge = report.mode === 'DEV' ? '<div class="dev-badge">[МОК-РЕЖИМ]</div>' : '';

    const clearSection = report.clearResult
      ? `
    <section class="section clear-section">
      <h2>Результат сброса ошибок</h2>
      <div class="meta-grid">
        <div class="meta-item">
          <span class="meta-label">Время</span>
          <span class="meta-value">${formatTimestamp(report.clearResult.timestamp)}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Статус операции</span>
          <span class="meta-value">${report.clearResult.success ? 'Успешно' : 'Ошибка'}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Подтверждение</span>
          <span class="meta-value">${escapeHtml(report.clearResult.confirmationMethod)}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Сброшено кодов</span>
          <span class="meta-value">${report.clearResult.clearedCodes.length}</span>
        </div>
      </div>
      ${report.clearResult.clearedCodes.length ? `
        <div class="cleared-codes">
          ${report.clearResult.clearedCodes
            .map(code => `<span class="badge info font-mono">${escapeHtml(code)}</span>`)
            .join(' ')}
        </div>` : ''}
    </section>`
      : '';

    return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Отчёт диагностики - ${escapeHtml(report.sessionId)}</title>
  <style>
    body { font-family: 'Inter', Arial, sans-serif; margin: 0; padding: 24px; background: #f8fafc; color: #0f172a; }
    .report-container { max-width: 980px; margin: 0 auto; background: #fff; border-radius: 16px; padding: 32px; box-shadow: 0 20px 45px rgba(15, 23, 42, 0.08); }
    h1 { margin: 0; font-size: 28px; }
    h2 { margin-bottom: 12px; }
    .section { margin-top: 32px; }
    .meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
    .meta-item { background: #f4f6fb; border-radius: 12px; padding: 12px 16px; }
    .meta-label { font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: #6b7280; display: block; margin-bottom: 4px; }
    .meta-value { font-size: 16px; font-weight: 600; }
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }
    .kpi-card { border-radius: 12px; padding: 16px; }
    .kpi-card .kpi-label { text-transform: uppercase; font-size: 12px; letter-spacing: 0.08em; margin-bottom: 8px; display: block; }
    .kpi-card .kpi-value { font-size: 28px; font-weight: 700; }
    .kpi-card.total { background: #111827; color: #fff; }
    .kpi-card.critical { background: #fee2e2; color: #991b1b; }
    .kpi-card.warning { background: #fef3c7; color: #b45309; }
    .kpi-card.info { background: #dbeafe; color: #1d4ed8; }
    .kpi-card.cleared { background: #ecfdf5; color: #065f46; }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { background: #0f172a; color: #fff; padding: 12px; text-align: left; position: sticky; top: 0; }
    td { padding: 10px 12px; border-bottom: 1px solid #e5e7eb; }
    tr:nth-child(even) td { background: #f9fafb; }
    .badge { display: inline-flex; align-items: center; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; }
    .badge.info { background: #dbeafe; color: #1d4ed8; }
    .badge.warning { background: #fef3c7; color: #92400e; }
    .badge.critical { background: #fee2e2; color: #b91c1c; }
    .text-center { text-align: center; }
    .text-muted { color: #6b7280; }
    .mil-status.on { color: #b91c1c; font-weight: 600; }
    .mil-status.off { color: #059669; font-weight: 600; }
    .dev-badge { display: inline-flex; padding: 6px 12px; border-radius: 999px; background: #312e81; color: #fff; font-size: 12px; letter-spacing: 0.08em; margin-bottom: 16px; }
    .clear-section { background: #fffbeb; border-radius: 16px; padding: 24px; }
    .cleared-codes { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
    .font-mono { font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace; }
  </style>
</head>
<body>
  <div class="report-container">
    ${devBadge}
    <header>
      <h1>Отчёт диагностики OBD-II</h1>
      <p class="text-muted">Сеанс ${escapeHtml(report.sessionId)}</p>
      <p class="text-muted">${hasCodes ? `Обнаружено ${report.stats.totalCodes} кодов` : 'Ошибок не выявлено'}</p>
    </header>

    <section class="section">
      <h2>Параметры сеанса</h2>
      <div class="meta-grid">
        ${metaItems
          .map(
            item => `
        <div class="meta-item">
          <span class="meta-label">${item.label}</span>
          <span class="meta-value">${item.value}</span>
        </div>`
          )
          .join('')}
      </div>
    </section>

    <section class="section">
      <h2>Сводка диагностики</h2>
      <div class="kpi-grid">
        ${summaryCards
          .map(
            card => `
        <div class="kpi-card ${card.className}">
          <span class="kpi-label">${card.label}</span>
          <span class="kpi-value">${card.value}</span>
        </div>`
          )
          .join('')}
      </div>
    </section>

    <section class="section">
      <h2>Коды ошибок (DTC)</h2>
      <div style="max-height: 600px; overflow-y: auto;">
        <table>
          <thead>
            <tr>
              <th style="width: 48px;">#</th>
              <th style="width: 120px;">Код</th>
              <th>Описание</th>
              <th style="width: 140px;">Система</th>
              <th style="width: 140px;">Статус</th>
              <th>Рекомендация</th>
            </tr>
          </thead>
          <tbody>
            ${dtcRows}
          </tbody>
        </table>
      </div>
    </section>

    ${clearSection}
  </div>
</body>
</html>
    `.trim();
  }
}
