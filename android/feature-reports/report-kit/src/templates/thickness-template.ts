/**
 * Шаблон отчёта толщиномера
 */

import { ThicknessReport } from '../types/index';
import { generateBaseStyles } from '../styles/base';
import { colors } from '../styles/design-tokens';
import {
  formatTimestamp,
  formatThickness,
  formatPrice,
  formatPercent,
  escapeHtml,
} from '../utils/formatters';
import {
  logoSVG,
  thicknessGaugeSVG,
  carBodyHeatmapSVG,
  checkIconSVG,
  warningIconSVG,
  errorIconSVG,
} from '../components/svg-icons';

/**
 * Генерирует HTML для отчёта толщиномера
 */
export function generateThicknessHTML(report: ThicknessReport, locale: string = 'ru-RU'): string {
  const baseStyles = generateBaseStyles();
  
  // Определяем акцентный цвет для толщиномера
  const accentColor = colors.accent.teal;
  const accentGradient = colors.gradients.teal;

  // Режим DEV badge
  const devModeBadge = report.mode === 'DEV' ? '<div class="dev-mode-badge">[МОК-РЕЖИМ]</div>' : '';

  // Генерируем строки таблицы измерений (сетка 6×10 = 60 зон)
  const measurementRows = report.measurements.map((m, index) => {
    let statusIcon = '';
    let statusClass = '';
    
    switch (m.status) {
      case 'ok':
        statusIcon = checkIconSVG(16);
        statusClass = 'status-ok';
        break;
      case 'warning':
        statusIcon = warningIconSVG(16);
        statusClass = 'status-warning';
        break;
      case 'critical':
        statusIcon = errorIconSVG(16);
        statusClass = 'status-critical';
        break;
      default:
        statusIcon = '<span style="color: ' + colors.status.empty + '">—</span>';
        statusClass = 'status-empty';
    }

    return `
      <tr>
        <td class="font-mono">${index + 1}</td>
        <td>${escapeHtml(m.zone)}</td>
        <td class="font-mono text-right">${formatThickness(m.value)}</td>
        <td class="text-center">${statusIcon}</td>
        <td class="text-center"><span class="status-badge ${statusClass}">${m.status.toUpperCase()}</span></td>
        <td>${m.comment ? escapeHtml(m.comment) : '—'}</td>
      </tr>
    `;
  }).join('');

  // Генерируем карточки KPI
  const kpiCards = `
    <div class="col-3">
      <div class="card card-accent-teal kpi-card">
        <div class="kpi-value" style="color: ${accentColor};">${report.stats.total}</div>
        <div class="kpi-label">Всего замеров</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-teal kpi-card">
        <div class="kpi-value" style="color: ${accentColor};">${formatThickness(report.stats.average)}</div>
        <div class="kpi-label">Среднее значение</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-teal kpi-card">
        <div class="kpi-value" style="color: ${report.stats.deviations > 0 ? colors.status.warning : colors.status.ok};">
          ${report.stats.deviations}
        </div>
        <div class="kpi-label">Отклонений</div>
      </div>
    </div>
    <div class="col-3">
      <div class="card card-accent-teal kpi-card">
        <div class="kpi-value" style="color: ${report.stats.deviationPercent > 20 ? colors.status.critical : colors.status.ok};">
          ${formatPercent(report.stats.deviationPercent)}
        </div>
        <div class="kpi-label">% отклонений</div>
      </div>
    </div>
  `;

  // Генерируем тепловую карту
  const heatmap = carBodyHeatmapSVG(report.measurements, report.stats.min, report.stats.max);

  return `
<!DOCTYPE html>
<html lang="${locale}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Отчёт толщиномера - ${report.sessionId}</title>
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
        ${thicknessGaugeSVG(48)}
        Отчёт по толщиномеру
      </h1>
      <p class="report-subtitle">Диагностика лакокрасочного покрытия автомобиля</p>
    </header>

    <!-- Информация о сеансе -->
    <section class="report-section">
      <div class="card card-accent-teal">
        <div class="card-header">
          <h3>Информация о сеансе</h3>
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
              <span class="meta-label">Тип автомобиля</span>
              <span class="meta-value">${escapeHtml(report.vehicleType)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Стоимость услуги</span>
              <span class="meta-value">${formatPrice(report.price)}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- KPI карточки -->
    <section class="report-section">
      <h2>Сводка измерений</h2>
      <div class="grid">
        ${kpiCards}
      </div>
    </section>

    <!-- Симметричное размещение: таблица слева, визуализация справа -->
    <section class="report-section">
      <h2>Детальные результаты</h2>
      <div class="grid">
        <!-- Левая колонка: таблица измерений -->
        <div class="col-6">
          <div class="card">
            <div class="card-header">
              <h4>Таблица измерений (6×10 зон)</h4>
            </div>
            <div class="card-body" style="max-height: 600px; overflow-y: auto;">
              <table>
                <thead>
                  <tr>
                    <th style="width: 50px;">#</th>
                    <th>Зона кузова</th>
                    <th style="width: 100px;">Значение</th>
                    <th style="width: 50px;">Статус</th>
                    <th style="width: 80px;">Уровень</th>
                    <th>Комментарий</th>
                  </tr>
                </thead>
                <tbody>
                  ${measurementRows}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Правая колонка: визуализация -->
        <div class="col-6">
          <div class="card">
            <div class="card-header">
              <h4>Тепловая карта покрытия</h4>
            </div>
            <div class="card-body text-center">
              ${heatmap}
            </div>
          </div>

          <div class="card mt-md">
            <div class="card-header">
              <h4>Статистика</h4>
            </div>
            <div class="card-body">
              <div class="meta-info">
                <div class="meta-item">
                  <span class="meta-label">Минимальное значение</span>
                  <span class="meta-value">${formatThickness(report.stats.min)}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Максимальное значение</span>
                  <span class="meta-value">${formatThickness(report.stats.max)}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Среднее значение</span>
                  <span class="meta-value">${formatThickness(report.stats.average)}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Нормальный диапазон</span>
                  <span class="meta-value">${formatThickness(report.analysis.normalRange.min)} - ${formatThickness(report.analysis.normalRange.max)}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Заполнено измерений</span>
                  <span class="meta-value">${report.stats.completed} из ${report.stats.total}</span>
                  <div class="progress-bar">
                    <div class="progress-fill" style="width: ${(report.stats.completed / report.stats.total) * 100}%;"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Аналитика и рекомендации -->
    <section class="report-section">
      <div class="card card-accent-teal">
        <div class="card-header">
          <h3>Аналитика и рекомендации</h3>
        </div>
        <div class="card-body">
          <p style="font-size: 1.125rem; line-height: 1.75;">
            ${escapeHtml(report.analysis.recommendation)}
          </p>
          ${report.stats.deviations > 0 ? `
            <p class="mt-md text-muted">
              Обнаружено <strong>${report.stats.deviations}</strong> зон с отклонениями от нормального диапазона.
              Это составляет <strong>${formatPercent(report.stats.deviationPercent)}</strong> от всех замеров.
            </p>
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
              Все измерения выполнены автоматизированным оборудованием.
              Для более детальной диагностики рекомендуется обратиться к специалисту.
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
