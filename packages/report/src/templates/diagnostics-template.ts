/**
 * Шаблон отчёта диагностики OBD-II
 */

import { DiagnosticsReport } from '../types/index.js';
import { generateBaseStyles } from '../styles/base.js';
import { colors } from '../styles/design-tokens.js';
import {
  formatTimestamp,
  formatDuration,
  formatPrice,
  escapeHtml,
  maskVIN,
} from '../utils/formatters.js';
import {
  logoSVG,
  obdAdapterSVG,
  donutChartSVG,
  checkIconSVG,
  warningIconSVG,
  errorIconSVG,
  infoIconSVG,
} from '../components/svg-icons.js';

/**
 * Генерирует HTML для отчёта диагностики OBD-II
 */
export function generateDiagnosticsHTML(report: DiagnosticsReport, locale: string = 'ru-RU'): string {
  const baseStyles = generateBaseStyles();
  
  // Определяем акцентный цвет для OBD-II
  const accentColor = colors.accent.amber;
  const accentGradient = colors.gradients.amber;

  // Режим DEV badge
  const devModeBadge = report.mode === 'DEV' ? '<div class="dev-mode-badge">[МОК-РЕЖИМ]</div>' : '';

  // Определяем общий статус
  let overallStatus = 'OK';
  let statusColor = colors.status.ok;
  let statusIcon = checkIconSVG(48);

  if (report.stats.critical > 0) {
    overallStatus = 'КРИТИЧЕСКИЕ ОШИБКИ';
    statusColor = colors.status.critical;
    statusIcon = errorIconSVG(48);
  } else if (report.stats.warnings > 0) {
    overallStatus = 'ОБНАРУЖЕНЫ ПРЕДУПРЕЖДЕНИЯ';
    statusColor = colors.status.warning;
    statusIcon = warningIconSVG(48);
  } else if (report.stats.totalCodes > 0) {
    overallStatus = 'ИНФОРМАЦИОННЫЕ КОДЫ';
    statusColor = colors.status.info;
    statusIcon = infoIconSVG(48);
  }

  // Генерируем строки таблицы DTC
  const dtcRows = report.dtcCodes.map((dtc, index) => {
    let severityIcon = '';
    let severityClass = '';
    
    switch (dtc.severity) {
      case 'critical':
        severityIcon = errorIconSVG(16);
        severityClass = 'status-critical';
        break;
      case 'warning':
        severityIcon = warningIconSVG(16);
        severityClass = 'status-warning';
        break;
      case 'info':
        severityIcon = infoIconSVG(16);
        severityClass = 'status-info';
        break;
    }

    return `
      <tr>
        <td class="font-mono">${index + 1}</td>
        <td class="font-mono font-bold">${escapeHtml(dtc.code)}</td>
        <td>${escapeHtml(dtc.description)}</td>
        <td>${dtc.system ? escapeHtml(dtc.system) : '—'}</td>
        <td class="text-center">${severityIcon}</td>
        <td class="text-center"><span class="status-badge ${severityClass}">${dtc.severity.toUpperCase()}</span></td>
        <td>${dtc.recommendation ? escapeHtml(dtc.recommendation) : '—'}</td>
      </tr>
    `;
  }).join('');

  // Генерируем карточки KPI
  const kpiCards = `
    <div class="col-3">
      <div class="card card-accent-amber kpi-card">
        <div class="kpi-value" style="color: ${accentColor};">${report.stats.totalCodes}</div>
        <div class="kpi-label">Всего кодов</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-amber kpi-card">
        <div class="kpi-value" style="color: ${colors.status.critical};">${report.stats.critical}</div>
        <div class="kpi-label">Критические</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-amber kpi-card">
        <div class="kpi-value" style="color: ${colors.status.warning};">${report.stats.warnings}</div>
        <div class="kpi-label">Предупреждения</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-amber kpi-card">
        <div class="kpi-value" style="color: ${report.clearResult ? colors.status.ok : colors.status.info};">
          ${report.stats.cleared}
        </div>
        <div class="kpi-label">Сброшено</div>
      </div>
    </div>
  `;

  // Генерируем кольцевую диаграмму
  const chartData = [
    { label: 'Критические', value: report.stats.critical, color: colors.status.critical },
    { label: 'Предупреждения', value: report.stats.warnings, color: colors.status.warning },
    { label: 'Информация', value: report.stats.info, color: colors.status.info },
  ].filter(item => item.value > 0);

  const donutChart = donutChartSVG(chartData, 200);

  // Секция сброса ошибок
  const clearResultSection = report.clearResult ? `
    <section class="report-section">
      <div class="card card-accent-amber">
        <div class="card-header">
          <h3>Результат сброса ошибок</h3>
        </div>
        <div class="card-body">
          <div class="meta-info">
            <div class="meta-item">
              <span class="meta-label">Время сброса</span>
              <span class="meta-value">${formatTimestamp(report.clearResult.timestamp, locale)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Статус операции</span>
              <span class="meta-value">
                ${report.clearResult.success ? checkIconSVG(16) : errorIconSVG(16)}
                ${report.clearResult.success ? 'Успешно' : 'Ошибка'}
              </span>
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
          ${report.clearResult.clearedCodes.length > 0 ? `
            <div class="mt-md">
              <strong>Сброшенные коды:</strong>
              <div class="font-mono mt-xs">
                ${report.clearResult.clearedCodes.map(code => `<span class="status-badge status-info">${escapeHtml(code)}</span>`).join(' ')}
              </div>
            </div>
          ` : ''}
        </div>
      </div>
    </section>
  ` : '';

  // Временная шкала (упрощённая)
  const timelineSection = `
    <div class="card mt-md">
      <div class="card-header">
        <h4>Временная шкала сканирования</h4>
      </div>
      <div class="card-body">
        <div style="position: relative; height: 100px;">
          <div style="position: absolute; left: 0; right: 0; top: 50%; height: 4px; background: ${colors.border.light};"></div>
          <div style="position: absolute; left: 0; top: 50%; transform: translateY(-50%);">
            <div style="width: 12px; height: 12px; border-radius: 50%; background: ${accentColor};"></div>
            <div style="margin-top: 8px; font-size: 0.75rem; color: ${colors.text.secondary};">Старт</div>
          </div>
          <div style="position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%);">
            <div style="width: 12px; height: 12px; border-radius: 50%; background: ${accentColor};"></div>
            <div style="margin-top: 8px; font-size: 0.75rem; color: ${colors.text.secondary}; text-align: center;">Сканирование</div>
          </div>
          <div style="position: absolute; right: 0; top: 50%; transform: translateY(-50%);">
            <div style="width: 12px; height: 12px; border-radius: 50%; background: ${accentColor};"></div>
            <div style="margin-top: 8px; font-size: 0.75rem; color: ${colors.text.secondary}; text-align: right;">Завершено</div>
          </div>
        </div>
        <div class="text-center mt-md text-muted">
          Длительность сканирования: <strong>${formatDuration(report.scanDuration)}</strong>
        </div>
      </div>
    </div>
  `;

  return `
<!DOCTYPE html>
<html lang="${locale}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Отчёт диагностики OBD-II - ${report.sessionId}</title>
  <style>
    ${baseStyles}
  </style>
</head>
<body>
  ${devModeBadge}
  
  <div class="report-container">
    <!-- Шапка отчёта -->
    <header class="report-header">
      <div class="report-logo">
        ${logoSVG(120, 40)}
      </div>
      <h1 class="report-title" style="color: ${accentColor};">
        ${obdAdapterSVG(48)}
        Отчёт диагностики OBD-II
      </h1>
      <p class="report-subtitle">Компьютерная диагностика систем автомобиля</p>
    </header>

    <!-- Статус диагностики -->
    <section class="report-section">
      <div class="card text-center" style="border-top: 4px solid ${statusColor}; padding: ${report.stats.totalCodes === 0 ? '3rem' : '2rem'};">
        <div style="margin-bottom: 1rem;">${statusIcon}</div>
        <h2 style="color: ${statusColor}; margin-bottom: 0.5rem;">${overallStatus}</h2>
        <p class="text-secondary">
          ${report.stats.totalCodes === 0 
            ? 'Система автомобиля в норме. Ошибок не обнаружено.'
            : `Обнаружено ${report.stats.totalCodes} кодов ошибок. Требуется внимание.`
          }
        </p>
      </div>
    </section>

    <!-- Информация о сеансе -->
    <section class="report-section">
      <div class="card card-accent-amber">
        <div class="card-header">
          <h3>Параметры сеанса</h3>
        </div>
        <div class="card-body">
          <div class="meta-info">
            <div class="meta-item">
              <span class="meta-label">Дата и время</span>
              <span class="meta-value">${formatTimestamp(report.timestamp, locale)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">ID сеанса</span>
              <span class="meta-value font-mono">${escapeHtml(report.sessionId)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Марка автомобиля</span>
              <span class="meta-value">${escapeHtml(report.vehicleBrand)}</span>
            </div>
            ${report.vin ? `
              <div class="meta-item">
                <span class="meta-label">VIN</span>
                <span class="meta-value font-mono">${maskVIN(report.vin)}</span>
              </div>
            ` : ''}
            <div class="meta-item">
              <span class="meta-label">Адаптер</span>
              <span class="meta-value">${escapeHtml(report.adapter)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Продолжительность</span>
              <span class="meta-value">${formatDuration(report.scanDuration)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Стоимость услуги</span>
              <span class="meta-value">${formatPrice(report.price)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">MIL Status</span>
              <span class="meta-value">
                ${report.milStatus === 'on' ? errorIconSVG(16) : checkIconSVG(16)}
                ${report.milStatus === 'on' ? 'Check Engine ON' : 'Check Engine OFF'}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- KPI карточки -->
    <section class="report-section">
      <h2>Сводка диагностики</h2>
      <div class="grid">
        ${kpiCards}
      </div>
    </section>

    <!-- Симметричное размещение: таблица слева, визуализация справа -->
    ${report.stats.totalCodes > 0 ? `
    <section class="report-section">
      <h2>Детальные результаты</h2>
      <div class="grid">
        <!-- Левая колонка: таблица DTC -->
        <div class="col-7">
          <div class="card">
            <div class="card-header">
              <h4>Таблица кодов DTC</h4>
            </div>
            <div class="card-body" style="max-height: 600px; overflow-y: auto;">
              <table>
                <thead>
                  <tr>
                    <th style="width: 50px;">#</th>
                    <th style="width: 100px;">Код</th>
                    <th>Описание</th>
                    <th style="width: 120px;">Система</th>
                    <th style="width: 50px;">Статус</th>
                    <th style="width: 100px;">Уровень</th>
                    <th>Рекомендация</th>
                  </tr>
                </thead>
                <tbody>
                  ${dtcRows}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Правая колонка: визуализация -->
        <div class="col-5">
          <div class="card">
            <div class="card-header">
              <h4>Распределение ошибок</h4>
            </div>
            <div class="card-body text-center">
              ${donutChart}
              <div class="mt-md">
                <div class="meta-info" style="justify-content: center;">
                  <div class="meta-item text-center">
                    <span class="meta-label">Критические</span>
                    <span class="meta-value" style="color: ${colors.status.critical};">${report.stats.critical}</span>
                  </div>
                  <div class="meta-item text-center">
                    <span class="meta-label">Предупреждения</span>
                    <span class="meta-value" style="color: ${colors.status.warning};">${report.stats.warnings}</span>
                  </div>
                  <div class="meta-item text-center">
                    <span class="meta-label">Информация</span>
                    <span class="meta-value" style="color: ${colors.status.info};">${report.stats.info}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          ${timelineSection}
        </div>
      </div>
    </section>
    ` : `
    <section class="report-section">
      <div class="card text-center" style="padding: 3rem; background: ${colors.gradients.dark};">
        ${checkIconSVG(64, colors.status.ok)}
        <h3 class="mt-md" style="color: ${colors.status.ok};">Ошибок не обнаружено</h3>
        <p class="text-secondary mt-sm">
          Все системы автомобиля функционируют в штатном режиме.
          Диагностика не выявила критических или предупредительных кодов.
        </p>
      </div>
    </section>
    `}

    ${clearResultSection}

    <!-- Дальнейшие действия -->
    <section class="report-section">
      <div class="card card-accent-amber">
        <div class="card-header">
          <h3>Дальнейшие действия и рекомендации</h3>
        </div>
        <div class="card-body">
          <p style="font-size: 1.125rem; line-height: 1.75;">
            ${report.stats.critical > 0 
              ? 'Обнаружены критические ошибки. Рекомендуется немедленно обратиться в сертифицированный автосервис для детальной диагностики и устранения неисправностей.'
              : report.stats.warnings > 0
              ? 'Обнаружены предупреждения. Рекомендуется провести профилактический осмотр систем автомобиля в ближайшее время.'
              : 'Система автомобиля в норме. Для поддержания надёжной работы рекомендуется проходить регулярную диагностику согласно регламенту производителя.'
            }
          </p>
          ${report.stats.totalCodes > 0 ? `
            <div class="mt-md" style="padding: 1rem; background: ${colors.background.tertiary}; border-radius: 0.5rem;">
              <strong>Важно:</strong> Данный отчёт предоставляет информацию о кодах ошибок, зарегистрированных системой управления автомобиля.
              Для точной диагностики причин и методов устранения неисправностей необходима консультация квалифицированного специалиста.
            </div>
          ` : ''}
        </div>
      </div>
    </section>

    <!-- Футер -->
    <footer class="report-footer">
      <div class="grid">
        <div class="col-6">
          <div class="footer-section">
            <div class="footer-heading">Контактная информация</div>
            <p>
              ${report.contact.email ? `Email: ${escapeHtml(report.contact.email)}<br>` : ''}
              ${report.contact.phone ? `Телефон: ${escapeHtml(report.contact.phone)}<br>` : ''}
            </p>
          </div>
        </div>
        <div class="col-6">
          <div class="footer-section">
            <div class="footer-heading">Важная информация</div>
            <p>
              Данный отчёт предназначен исключительно для информационных целей.
              Диагностика выполнена автоматизированным оборудованием в соответствии с протоколами OBD-II.
              Для получения гарантийного обслуживания обратитесь к официальному дилеру.
            </p>
            <p class="mt-sm">
              <strong>Срок хранения данных:</strong> 30 дней с момента создания отчёта.
            </p>
          </div>
        </div>
      </div>
      <div class="text-center mt-lg text-muted">
        <p>© 2025 Kiosk Self-Service System. Все права защищены.</p>
        <p class="font-mono" style="font-size: 0.75rem;">Generated: ${new Date().toISOString()}</p>
      </div>
    </footer>
  </div>
</body>
</html>
  `.trim();
}
