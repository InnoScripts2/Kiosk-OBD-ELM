/**
 * Дизайн-система отчётов
 * Единая цветовая палитра, типографика и отступы
 */

/**
 * Цветовая палитра
 */
export const colors = {
  // Фоновые цвета
  background: {
    primary: '#0B0D17', // Основной тёмный фон
    secondary: '#1A1D2E', // Вторичный фон для карточек
    tertiary: '#262A3F', // Третичный для hover состояний
  },

  // Текстовые цвета
  text: {
    primary: '#F7F7F8', // Основной текст
    secondary: '#B8B9BF', // Вторичный текст
    muted: '#6B6C75', // Приглушённый текст
    inverse: '#0B0D17', // Инверсный (для светлого фона)
  },

  // Акцентные цвета
  accent: {
    teal: '#00C4B4', // Толщиномер (голубой/бирюзовый)
    amber: '#FFC857', // OBD-II (янтарный)
    tealDark: '#009B8E', // Тёмный оттенок для градиентов
    amberDark: '#E6A844', // Тёмный янтарный
  },

  // Статусные цвета
  status: {
    ok: '#10B981', // Зелёный - всё в порядке
    warning: '#F59E0B', // Оранжевый - предупреждение
    critical: '#EF4444', // Красный - критично
    info: '#3B82F6', // Синий - информация
    empty: '#4B5563', // Серый - нет данных
  },

  // Границы и разделители
  border: {
    default: '#2D3142',
    light: '#3D4254',
    accent: '#00C4B4',
  },

  // Градиенты
  gradients: {
    teal: 'linear-gradient(135deg, #00C4B4 0%, #009B8E 100%)',
    amber: 'linear-gradient(135deg, #FFC857 0%, #E6A844 100%)',
    dark: 'linear-gradient(180deg, #1A1D2E 0%, #0B0D17 100%)',
  },
};

/**
 * Типографика
 */
export const typography = {
  fonts: {
    primary: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif",
    mono: "'Roboto Mono', 'Courier New', monospace",
  },

  sizes: {
    // Заголовки
    h1: '2.5rem', // 40px
    h2: '2rem', // 32px
    h3: '1.5rem', // 24px
    h4: '1.25rem', // 20px
    h5: '1.125rem', // 18px

    // Тело
    body: '1rem', // 16px
    bodyLarge: '1.125rem', // 18px
    bodySmall: '0.875rem', // 14px

    // Специальные
    caption: '0.75rem', // 12px
    label: '0.875rem', // 14px
  },

  weights: {
    light: 300,
    regular: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },

  lineHeights: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
};

/**
 * Отступы и размеры
 */
export const spacing = {
  // Базовая единица: 8px
  xs: '0.5rem', // 8px
  sm: '1rem', // 16px
  md: '1.5rem', // 24px
  lg: '2rem', // 32px
  xl: '2.5rem', // 40px
  xxl: '3rem', // 48px

  // Сетка
  grid: {
    columns: 12,
    gutter: '1.5rem', // 24px между колонками
    margin: {
      horizontal: '2.5rem', // 40px по бокам
      vertical: '2rem', // 32px сверху/снизу
    },
  },
};

/**
 * Тени
 */
export const shadows = {
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.3)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.4)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.5)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.6)',
};

/**
 * Скругления углов
 */
export const borderRadius = {
  none: '0',
  sm: '0.25rem', // 4px
  md: '0.5rem', // 8px
  lg: '0.75rem', // 12px
  xl: '1rem', // 16px
  full: '9999px',
};

/**
 * Размеры иконок и элементов
 */
export const sizes = {
  icon: {
    sm: '1rem', // 16px
    md: '1.5rem', // 24px
    lg: '2rem', // 32px
    xl: '3rem', // 48px
  },

  avatar: {
    sm: '2rem', // 32px
    md: '3rem', // 48px
    lg: '4rem', // 64px
  },
};

/**
 * Контраст (WCAG AA минимум)
 */
export const accessibility = {
  minContrastRatio: 4.5, // WCAG AA для обычного текста
  minContrastRatioLarge: 3, // WCAG AA для крупного текста (18pt+ или 14pt+ bold)
  focusOutlineWidth: '2px',
  focusOutlineOffset: '2px',
};

/**
 * Анимации
 */
export const animations = {
  durations: {
    fast: '150ms',
    normal: '300ms',
    slow: '500ms',
  },

  easings: {
    default: 'cubic-bezier(0.4, 0, 0.2, 1)',
    in: 'cubic-bezier(0.4, 0, 1, 1)',
    out: 'cubic-bezier(0, 0, 0.2, 1)',
    inOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
  },
};

/**
 * Медиа-запросы для адаптивности (хотя отчёты в основном для печати/PDF)
 */
export const breakpoints = {
  mobile: '640px',
  tablet: '768px',
  desktop: '1024px',
  wide: '1280px',
};
