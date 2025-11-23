/**
 * @kiosk/report - модуль генерации отчётов
 * 
 * Предоставляет функциональность для создания подробных, визуально выверенных
 * отчётов в форматах HTML и PDF для услуг толщиномера и OBD-II диагностики.
 */

// Типы
export * from './types/index';

// Стили и дизайн-система
export * from './styles/design-tokens';
export * from './styles/base';

// Утилиты
export * from './utils/formatters';
export * from './utils/validators';

// Компоненты
export * from './components/svg-icons';

// Шаблоны
export * from './templates/thickness-template';
export * from './templates/diagnostics-template';

// Рендереры
export * from './renderers/pdf-renderer';

// Главный генератор
export { ReportGenerator } from './report-generator';
