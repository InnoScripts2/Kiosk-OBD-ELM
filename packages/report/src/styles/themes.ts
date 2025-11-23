/**
 * Темы оформления отчётов
 * Соответствует требованиям: единая 12-колоночная сетка, контрастность WCAG AA
 */

import { ReportTheme, AppMode } from '../types/index.js';

/**
 * Базовая тема отчётов
 */
export const baseTheme: ReportTheme = {
  backgroundColor: '#0B0D17',
  textColor: '#F7F7F8',
  accentColor: '#00C4B4', // Для толщиномера (голубой)
  secondaryAccent: '#FFC857', // Для OBD (янтарный)
  fontFamily: "'Inter', 'Roboto', sans-serif",
  fontSize: {
    h1: '32px',
    h2: '24px',
    body: '16px',
    small: '14px',
  },
  spacing: {
    page: { top: 32, bottom: 32, left: 40, right: 40 },
    section: 24,
    grid: 8,
  },
};

/**
 * Тема для толщиномера (голубые акценты)
 */
export const thicknessTheme: ReportTheme = {
  ...baseTheme,
  accentColor: '#00C4B4',
};

/**
 * Тема для OBD-II (янтарные акценты)
 */
export const obdTheme: ReportTheme = {
  ...baseTheme,
  accentColor: '#FFC857',
};

/**
 * Получить тему в зависимости от режима
 */
export function getThemeForMode(mode: AppMode, serviceType: 'thickness' | 'obd'): ReportTheme {
  const theme = serviceType === 'thickness' ? thicknessTheme : obdTheme;
  
  // В DEV режиме добавляем водяной знак через изменение фона
  if (mode === 'DEV') {
    return {
      ...theme,
      backgroundColor: '#1A1C2E', // Немного светлее для отличия DEV
    };
  }
  
  return theme;
}

/**
 * CSS переменные для темы
 */
export function getThemeCSS(theme: ReportTheme): string {
  return `
    :root {
      --bg-color: ${theme.backgroundColor};
      --text-color: ${theme.textColor};
      --accent-color: ${theme.accentColor};
      --secondary-accent: ${theme.secondaryAccent};
      --font-family: ${theme.fontFamily};
      --font-h1: ${theme.fontSize.h1};
      --font-h2: ${theme.fontSize.h2};
      --font-body: ${theme.fontSize.body};
      --font-small: ${theme.fontSize.small};
      --spacing-page-top: ${theme.spacing.page.top}px;
      --spacing-page-bottom: ${theme.spacing.page.bottom}px;
      --spacing-page-left: ${theme.spacing.page.left}px;
      --spacing-page-right: ${theme.spacing.page.right}px;
      --spacing-section: ${theme.spacing.section}px;
      --spacing-grid: ${theme.spacing.grid}px;
    }
  `.trim();
}
