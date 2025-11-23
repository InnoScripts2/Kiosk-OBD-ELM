/**
 * @kiosk/report - модуль генерации отчётов
 * 
 * Предоставляет функциональность для создания подробных, визуально выверенных
 * отчётов в форматах HTML и PDF для услуг толщиномера и OBD-II диагностики.
 */

// Типы
export * from './types/index.js';

// Стили и дизайн-система
export * from './styles/design-tokens.js';
export * from './styles/base.js';

// Утилиты
export * from './utils/formatters.js';
export * from './utils/validators.js';

// Компоненты
export * from './components/svg-icons.js';

// Шаблоны
export * from './templates/thickness-template.js';
export * from './templates/diagnostics-template.js';

// Рендереры
export * from './renderers/pdf-renderer.js';

// Главный генератор
export { ReportGenerator } from './report-generator.js';
