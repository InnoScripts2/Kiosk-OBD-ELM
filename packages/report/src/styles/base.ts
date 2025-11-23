/**
 * Базовые стили для отчётов
 * Включает 12-колоночную сетку, типографику, утилиты
 */

export const baseStyles = `
/* Reset и базовые стили */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--font-family);
  font-size: var(--font-body);
  line-height: 1.6;
  color: var(--text-color);
  background-color: var(--bg-color);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Типографика */
h1 {
  font-size: var(--font-h1);
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: calc(var(--spacing-section) * 1px);
}

h2 {
  font-size: var(--font-h2);
  font-weight: 600;
  line-height: 1.3;
  margin-bottom: calc(var(--spacing-section) * 0.75px);
}

p {
  margin-bottom: calc(var(--spacing-grid) * 2px);
}

/* 12-колоночная сетка */
.container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-page-top) var(--spacing-page-left) var(--spacing-page-bottom) var(--spacing-page-right);
}

.row {
  display: flex;
  flex-wrap: wrap;
  margin-left: calc(var(--spacing-grid) * -1px);
  margin-right: calc(var(--spacing-grid) * -1px);
}

.col {
  flex: 1;
  padding-left: calc(var(--spacing-grid) * 1px);
  padding-right: calc(var(--spacing-grid) * 1px);
}

.col-6 {
  flex: 0 0 50%;
  max-width: 50%;
  padding-left: calc(var(--spacing-grid) * 1px);
  padding-right: calc(var(--spacing-grid) * 1px);
}

.col-4 {
  flex: 0 0 33.333%;
  max-width: 33.333%;
  padding-left: calc(var(--spacing-grid) * 1px);
  padding-right: calc(var(--spacing-grid) * 1px);
}

.col-3 {
  flex: 0 0 25%;
  max-width: 25%;
  padding-left: calc(var(--spacing-grid) * 1px);
  padding-right: calc(var(--spacing-grid) * 1px);
}

.col-12 {
  flex: 0 0 100%;
  max-width: 100%;
  padding-left: calc(var(--spacing-grid) * 1px);
  padding-right: calc(var(--spacing-grid) * 1px);
}

/* Карточки */
.card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.02) 100%);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: calc(var(--spacing-section) * 1px);
  margin-bottom: calc(var(--spacing-section) * 1px);
}

.card-header {
  font-size: var(--font-h2);
  font-weight: 600;
  margin-bottom: calc(var(--spacing-grid) * 2px);
  color: var(--accent-color);
}

/* Таблицы */
table {
  width: 100%;
  border-collapse: collapse;
  margin: calc(var(--spacing-section) * 1px) 0;
}

th, td {
  padding: calc(var(--spacing-grid) * 1.5px);
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

th {
  background: rgba(255, 255, 255, 0.05);
  font-weight: 600;
  color: var(--accent-color);
}

tr:hover {
  background: rgba(255, 255, 255, 0.02);
}

/* Статусные индикаторы */
.status {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: var(--font-small);
  font-weight: 600;
  text-transform: uppercase;
}

.status-ok {
  background: rgba(0, 196, 180, 0.2);
  color: #00C4B4;
}

.status-warning {
  background: rgba(255, 200, 87, 0.2);
  color: #FFC857;
}

.status-critical {
  background: rgba(255, 87, 87, 0.2);
  color: #FF5757;
}

.status-info {
  background: rgba(100, 150, 255, 0.2);
  color: #6496FF;
}

/* Утилиты */
.text-center {
  text-align: center;
}

.text-right {
  text-align: right;
}

.mt-1 { margin-top: calc(var(--spacing-grid) * 1px); }
.mt-2 { margin-top: calc(var(--spacing-grid) * 2px); }
.mt-3 { margin-top: calc(var(--spacing-grid) * 3px); }
.mt-4 { margin-top: calc(var(--spacing-grid) * 4px); }

.mb-1 { margin-bottom: calc(var(--spacing-grid) * 1px); }
.mb-2 { margin-bottom: calc(var(--spacing-grid) * 2px); }
.mb-3 { margin-bottom: calc(var(--spacing-grid) * 3px); }
.mb-4 { margin-bottom: calc(var(--spacing-grid) * 4px); }

.section {
  margin-bottom: calc(var(--spacing-section) * 2px);
}

/* Header/Footer */
.header {
  border-bottom: 2px solid var(--accent-color);
  padding-bottom: calc(var(--spacing-section) * 1px);
  margin-bottom: calc(var(--spacing-section) * 2px);
}

.footer {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: calc(var(--spacing-section) * 1px);
  margin-top: calc(var(--spacing-section) * 3px);
  font-size: var(--font-small);
  color: rgba(247, 247, 248, 0.6);
}

/* DEV водяной знак */
.dev-watermark {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotate(-45deg);
  font-size: 120px;
  font-weight: 900;
  color: rgba(255, 200, 87, 0.1);
  pointer-events: none;
  z-index: -1;
}

/* Печать */
@media print {
  body {
    background: white;
    color: black;
  }
  
  .card {
    border-color: #ccc;
  }
  
  @page {
    margin: 20mm;
  }
}
`;
