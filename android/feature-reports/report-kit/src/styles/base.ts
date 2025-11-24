/**
 * Базовые стили для отчётов
 * Генерирует встраиваемый CSS на основе дизайн-токенов
 */

import { colors, typography, spacing, shadows, borderRadius } from './design-tokens';

/**
 * Генерирует базовые стили для отчёта
 */
export function generateBaseStyles(): string {
  return `
/* Сброс и базовые стили */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  font-size: 16px;
}

body {
  font-family: ${typography.fonts.primary};
  font-size: ${typography.sizes.body};
  font-weight: ${typography.weights.regular};
  line-height: ${typography.lineHeights.normal};
  color: ${colors.text.primary};
  background-color: ${colors.background.primary};
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Типографика */
h1 {
  font-size: ${typography.sizes.h1};
  font-weight: ${typography.weights.bold};
  line-height: ${typography.lineHeights.tight};
  margin-bottom: ${spacing.md};
}

h2 {
  font-size: ${typography.sizes.h2};
  font-weight: ${typography.weights.bold};
  line-height: ${typography.lineHeights.tight};
  margin-bottom: ${spacing.sm};
}

h3 {
  font-size: ${typography.sizes.h3};
  font-weight: ${typography.weights.semibold};
  line-height: ${typography.lineHeights.tight};
  margin-bottom: ${spacing.sm};
}

h4 {
  font-size: ${typography.sizes.h4};
  font-weight: ${typography.weights.semibold};
  margin-bottom: ${spacing.xs};
}

p {
  margin-bottom: ${spacing.sm};
}

/* Контейнеры */
.report-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: ${spacing.grid.margin.vertical} ${spacing.grid.margin.horizontal};
}

.report-section {
  margin-bottom: ${spacing.xl};
}

/* Сетка 12 колонок */
.grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: ${spacing.grid.gutter};
  margin-bottom: ${spacing.lg};
}

.col-1 { grid-column: span 1; }
.col-2 { grid-column: span 2; }
.col-3 { grid-column: span 3; }
.col-4 { grid-column: span 4; }
.col-5 { grid-column: span 5; }
.col-6 { grid-column: span 6; }
.col-7 { grid-column: span 7; }
.col-8 { grid-column: span 8; }
.col-9 { grid-column: span 9; }
.col-10 { grid-column: span 10; }
.col-11 { grid-column: span 11; }
.col-12 { grid-column: span 12; }

/* Карточки */
.card {
  background-color: ${colors.background.secondary};
  border-radius: ${borderRadius.lg};
  padding: ${spacing.md};
  box-shadow: ${shadows.md};
  border: 1px solid ${colors.border.default};
}

.card-header {
  margin-bottom: ${spacing.md};
  padding-bottom: ${spacing.sm};
  border-bottom: 1px solid ${colors.border.light};
}

.card-body {
  margin-bottom: ${spacing.sm};
}

.card-footer {
  margin-top: ${spacing.md};
  padding-top: ${spacing.sm};
  border-top: 1px solid ${colors.border.light};
  font-size: ${typography.sizes.bodySmall};
  color: ${colors.text.secondary};
}

/* Карточки с акцентом */
.card-accent-teal {
  border-top: 4px solid ${colors.accent.teal};
}

.card-accent-amber {
  border-top: 4px solid ${colors.accent.amber};
}

/* KPI карточки */
.kpi-card {
  text-align: center;
  padding: ${spacing.md};
}

.kpi-value {
  font-size: ${typography.sizes.h1};
  font-weight: ${typography.weights.bold};
  line-height: 1;
  margin-bottom: ${spacing.xs};
}

.kpi-label {
  font-size: ${typography.sizes.bodySmall};
  color: ${colors.text.secondary};
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Таблицы */
table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: ${spacing.md};
}

thead {
  background: ${colors.gradients.dark};
}

th {
  padding: ${spacing.sm};
  text-align: left;
  font-weight: ${typography.weights.semibold};
  border-bottom: 2px solid ${colors.border.accent};
  font-size: ${typography.sizes.bodySmall};
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

td {
  padding: ${spacing.sm};
  border-bottom: 1px solid ${colors.border.default};
}

tbody tr:hover {
  background-color: ${colors.background.tertiary};
}

/* Статусные индикаторы */
.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: ${borderRadius.full};
  font-size: ${typography.sizes.bodySmall};
  font-weight: ${typography.weights.medium};
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.status-ok {
  background-color: ${colors.status.ok};
  color: ${colors.text.inverse};
}

.status-warning {
  background-color: ${colors.status.warning};
  color: ${colors.text.inverse};
}

.status-critical {
  background-color: ${colors.status.critical};
  color: ${colors.text.primary};
}

.status-info {
  background-color: ${colors.status.info};
  color: ${colors.text.primary};
}

.status-empty {
  background-color: ${colors.status.empty};
  color: ${colors.text.secondary};
}

/* Индикаторы с точкой */
.status-dot {
  display: inline-block;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: ${borderRadius.full};
  margin-right: 0.5rem;
}

.status-dot.ok { background-color: ${colors.status.ok}; }
.status-dot.warning { background-color: ${colors.status.warning}; }
.status-dot.critical { background-color: ${colors.status.critical}; }
.status-dot.info { background-color: ${colors.status.info}; }
.status-dot.empty { background-color: ${colors.status.empty}; }

/* Прогресс-бары */
.progress-bar {
  width: 100%;
  height: 0.5rem;
  background-color: ${colors.background.tertiary};
  border-radius: ${borderRadius.full};
  overflow: hidden;
  margin: ${spacing.xs} 0;
}

.progress-fill {
  height: 100%;
  background: ${colors.gradients.teal};
  transition: width 0.3s ease;
}

/* Шапка отчёта */
.report-header {
  margin-bottom: ${spacing.xl};
  padding-bottom: ${spacing.lg};
  border-bottom: 2px solid ${colors.border.light};
}

.report-logo {
  margin-bottom: ${spacing.md};
}

.report-title {
  font-size: ${typography.sizes.h1};
  font-weight: ${typography.weights.bold};
  margin-bottom: ${spacing.xs};
}

.report-subtitle {
  font-size: ${typography.sizes.bodyLarge};
  color: ${colors.text.secondary};
}

/* Мета-информация */
.meta-info {
  display: flex;
  flex-wrap: wrap;
  gap: ${spacing.md};
  margin-bottom: ${spacing.md};
}

.meta-item {
  display: flex;
  flex-direction: column;
}

.meta-label {
  font-size: ${typography.sizes.bodySmall};
  color: ${colors.text.secondary};
  margin-bottom: 0.25rem;
}

.meta-value {
  font-size: ${typography.sizes.body};
  font-weight: ${typography.weights.medium};
}

/* Футер */
.report-footer {
  margin-top: ${spacing.xxl};
  padding-top: ${spacing.lg};
  border-top: 2px solid ${colors.border.light};
  font-size: ${typography.sizes.bodySmall};
  color: ${colors.text.secondary};
}

.footer-section {
  margin-bottom: ${spacing.md};
}

.footer-heading {
  font-weight: ${typography.weights.semibold};
  color: ${colors.text.primary};
  margin-bottom: ${spacing.xs};
}

/* Dev Mode Badge */
.dev-mode-badge {
  position: fixed;
  top: ${spacing.sm};
  right: ${spacing.sm};
  background-color: ${colors.status.warning};
  color: ${colors.text.inverse};
  padding: 0.5rem 1rem;
  border-radius: ${borderRadius.md};
  font-weight: ${typography.weights.bold};
  font-size: ${typography.sizes.bodySmall};
  box-shadow: ${shadows.lg};
  z-index: 1000;
}

/* Печать */
@media print {
  body {
    background-color: white;
    color: black;
  }

  .report-container {
    max-width: 100%;
    padding: 0;
  }

  .dev-mode-badge {
    display: none;
  }

  @page {
    margin: 2cm;
    size: A4 portrait;
  }
}

/* Утилиты */
.text-center { text-align: center; }
.text-right { text-align: right; }
.text-left { text-align: left; }

.text-muted { color: ${colors.text.muted}; }
.text-secondary { color: ${colors.text.secondary}; }
.text-primary { color: ${colors.text.primary}; }

.font-mono { font-family: ${typography.fonts.mono}; }
.font-bold { font-weight: ${typography.weights.bold}; }
.font-semibold { font-weight: ${typography.weights.semibold}; }

.mb-0 { margin-bottom: 0; }
.mb-xs { margin-bottom: ${spacing.xs}; }
.mb-sm { margin-bottom: ${spacing.sm}; }
.mb-md { margin-bottom: ${spacing.md}; }
.mb-lg { margin-bottom: ${spacing.lg}; }
.mb-xl { margin-bottom: ${spacing.xl}; }

.mt-0 { margin-top: 0; }
.mt-xs { margin-top: ${spacing.xs}; }
.mt-sm { margin-top: ${spacing.sm}; }
.mt-md { margin-top: ${spacing.md}; }
.mt-lg { margin-top: ${spacing.lg}; }
.mt-xl { margin-top: ${spacing.xl}; }
`;
}
