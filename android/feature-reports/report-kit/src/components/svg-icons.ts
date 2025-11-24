/**
 * SVG компоненты для отчётов
 * Векторные иконки и визуальные элементы
 */

import { colors } from '../styles/design-tokens';

/**
 * Логотип (placeholder - должен быть заменён реальным)
 */
export function logoSVG(width: number = 120, height: number = 40): string {
  return `
<svg width="${width}" height="${height}" viewBox="0 0 120 40" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect width="120" height="40" rx="8" fill="${colors.accent.teal}"/>
  <text x="60" y="25" font-family="Arial, sans-serif" font-size="18" font-weight="bold" fill="${colors.text.primary}" text-anchor="middle">
    KIOSK
  </text>
</svg>
  `.trim();
}

/**
 * Иконка галочки (OK)
 */
export function checkIconSVG(size: number = 24, color: string = colors.status.ok): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <circle cx="12" cy="12" r="10" fill="${color}"/>
  <path d="M7 12l3 3 7-7" stroke="${colors.text.primary}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
  `.trim();
}

/**
 * Иконка предупреждения
 */
export function warningIconSVG(size: number = 24, color: string = colors.status.warning): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 2L2 20h20L12 2z" fill="${color}"/>
  <path d="M12 9v5M12 16h.01" stroke="${colors.text.inverse}" stroke-width="2" stroke-linecap="round"/>
</svg>
  `.trim();
}

/**
 * Иконка ошибки
 */
export function errorIconSVG(size: number = 24, color: string = colors.status.critical): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <circle cx="12" cy="12" r="10" fill="${color}"/>
  <path d="M15 9l-6 6M9 9l6 6" stroke="${colors.text.primary}" stroke-width="2" stroke-linecap="round"/>
</svg>
  `.trim();
}

/**
 * Иконка информации
 */
export function infoIconSVG(size: number = 24, color: string = colors.status.info): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <circle cx="12" cy="12" r="10" fill="${color}"/>
  <path d="M12 8h.01M12 12v4" stroke="${colors.text.primary}" stroke-width="2" stroke-linecap="round"/>
</svg>
  `.trim();
}

/**
 * Иконка толщиномера
 */
export function thicknessGaugeSVG(size: number = 48): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect x="8" y="16" width="32" height="24" rx="4" fill="${colors.background.secondary}" stroke="${colors.accent.teal}" stroke-width="2"/>
  <circle cx="24" cy="28" r="6" fill="${colors.accent.teal}"/>
  <rect x="18" y="8" width="12" height="10" rx="2" fill="${colors.background.tertiary}" stroke="${colors.accent.teal}"/>
  <text x="24" y="32" font-family="monospace" font-size="6" fill="${colors.text.primary}" text-anchor="middle">123</text>
</svg>
  `.trim();
}

/**
 * Иконка OBD-II адаптера
 */
export function obdAdapterSVG(size: number = 48): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect x="12" y="8" width="24" height="32" rx="4" fill="${colors.background.secondary}" stroke="${colors.accent.amber}" stroke-width="2"/>
  <rect x="16" y="12" width="4" height="4" rx="1" fill="${colors.accent.amber}"/>
  <rect x="22" y="12" width="4" height="4" rx="1" fill="${colors.accent.amber}"/>
  <rect x="28" y="12" width="4" height="4" rx="1" fill="${colors.accent.amber}"/>
  <rect x="16" y="18" width="16" height="2" rx="1" fill="${colors.text.muted}"/>
  <rect x="16" y="22" width="12" height="2" rx="1" fill="${colors.text.muted}"/>
  <rect x="16" y="26" width="14" height="2" rx="1" fill="${colors.text.muted}"/>
  <path d="M20 36v4M28 36v4" stroke="${colors.accent.amber}" stroke-width="2" stroke-linecap="round"/>
</svg>
  `.trim();
}

/**
 * Иконка автомобиля
 */
export function carIconSVG(size: number = 48): string {
  return `
<svg width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M10 24l4-8h20l4 8v12H10V24z" fill="${colors.background.secondary}" stroke="${colors.text.secondary}" stroke-width="2"/>
  <circle cx="16" cy="32" r="3" fill="${colors.text.secondary}"/>
  <circle cx="32" cy="32" r="3" fill="${colors.text.secondary}"/>
  <rect x="14" y="18" width="6" height="4" rx="1" fill="${colors.background.tertiary}"/>
  <rect x="22" y="18" width="6" height="4" rx="1" fill="${colors.background.tertiary}"/>
  <rect x="30" y="18" width="4" height="4" rx="1" fill="${colors.background.tertiary}"/>
</svg>
  `.trim();
}

/**
 * Генерирует простую схему кузова автомобиля с тепловой раскраской
 * @param measurements - массив из 60 измерений
 * @param min - минимальное значение для расчёта цвета
 * @param max - максимальное значение для расчёта цвета
 */
export function carBodyHeatmapSVG(
  measurements: Array<{ zone: string; value: number; status: string }>,
  min: number,
  max: number
): string {
  // Упрощённая схема: вид сбоку автомобиля с зонами
  // 60 зон разделены на: капот (10), крыша (10), багажник (10), 4 двери (по 7-8 каждая)
  
  const width = 800;
  const height = 400;
  
  // Функция для получения цвета на основе значения
  const getColor = (value: number, status: string): string => {
    if (status === 'empty' || status === 'error') {
      return colors.status.empty;
    }
    if (status === 'critical') {
      return colors.status.critical;
    }
    if (status === 'warning') {
      return colors.status.warning;
    }
    return colors.status.ok;
  };

  // Группируем измерения по зонам
  const zones: Record<string, string> = {};
  measurements.forEach((m) => {
    zones[m.zone] = getColor(m.value, m.status);
  });

  return `
<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" fill="none" xmlns="http://www.w3.org/2000/svg">
  <!-- Контур автомобиля -->
  <rect x="50" y="100" width="700" height="200" rx="20" fill="${colors.background.tertiary}" stroke="${colors.border.light}" stroke-width="2"/>
  
  <!-- Капот (передняя часть) -->
  <rect x="50" y="100" width="150" height="200" rx="20" fill="${zones['Капот'] || colors.status.empty}" opacity="0.7"/>
  <text x="125" y="210" font-size="12" fill="${colors.text.primary}" text-anchor="middle">Капот</text>
  
  <!-- Крыша -->
  <rect x="200" y="80" width="400" height="60" rx="10" fill="${zones['Крыша'] || colors.status.empty}" opacity="0.7"/>
  <text x="400" y="115" font-size="12" fill="${colors.text.primary}" text-anchor="middle">Крыша</text>
  
  <!-- Передняя дверь -->
  <rect x="200" y="150" width="180" height="150" fill="${zones['Дверь передняя левая'] || colors.status.empty}" opacity="0.7"/>
  <text x="290" y="230" font-size="12" fill="${colors.text.primary}" text-anchor="middle">Передняя дверь</text>
  
  <!-- Задняя дверь -->
  <rect x="380" y="150" width="180" height="150" fill="${zones['Дверь задняя левая'] || colors.status.empty}" opacity="0.7"/>
  <text x="470" y="230" font-size="12" fill="${colors.text.primary}" text-anchor="middle">Задняя дверь</text>
  
  <!-- Багажник -->
  <rect x="600" y="100" width="150" height="200" rx="20" fill="${zones['Багажник'] || colors.status.empty}" opacity="0.7"/>
  <text x="675" y="210" font-size="12" fill="${colors.text.primary}" text-anchor="middle">Багажник</text>
  
  <!-- Легенда -->
  <g transform="translate(50, 320)">
    <rect x="0" y="0" width="20" height="20" fill="${colors.status.ok}" opacity="0.7"/>
    <text x="25" y="15" font-size="12" fill="${colors.text.secondary}">Норма</text>
    
    <rect x="100" y="0" width="20" height="20" fill="${colors.status.warning}" opacity="0.7"/>
    <text x="125" y="15" font-size="12" fill="${colors.text.secondary}">Отклонение</text>
    
    <rect x="240" y="0" width="20" height="20" fill="${colors.status.critical}" opacity="0.7"/>
    <text x="265" y="15" font-size="12" fill="${colors.text.secondary}">Критично</text>
    
    <rect x="360" y="0" width="20" height="20" fill="${colors.status.empty}" opacity="0.7"/>
    <text x="385" y="15" font-size="12" fill="${colors.text.secondary}">Нет данных</text>
  </g>
</svg>
  `.trim();
}

/**
 * Кольцевая диаграмма для распределения ошибок
 */
export function donutChartSVG(
  data: { label: string; value: number; color: string }[],
  size: number = 200
): string {
  const total = data.reduce((sum, item) => sum + item.value, 0);
  if (total === 0) {
    return `<svg width="${size}" height="${size}"><text x="${size/2}" y="${size/2}" text-anchor="middle">Нет данных</text></svg>`;
  }

  const radius = size / 2 - 20;
  const innerRadius = radius * 0.6;
  const cx = size / 2;
  const cy = size / 2;

  let currentAngle = -90; // Начинаем сверху
  const paths: string[] = [];

  data.forEach((item, index) => {
    const angle = (item.value / total) * 360;
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;

    const startRad = (startAngle * Math.PI) / 180;
    const endRad = (endAngle * Math.PI) / 180;

    const x1 = cx + radius * Math.cos(startRad);
    const y1 = cy + radius * Math.sin(startRad);
    const x2 = cx + radius * Math.cos(endRad);
    const y2 = cy + radius * Math.sin(endRad);

    const x3 = cx + innerRadius * Math.cos(endRad);
    const y3 = cy + innerRadius * Math.sin(endRad);
    const x4 = cx + innerRadius * Math.cos(startRad);
    const y4 = cy + innerRadius * Math.sin(startRad);

    const largeArcFlag = angle > 180 ? 1 : 0;

    const pathData = `
      M ${x1} ${y1}
      A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2}
      L ${x3} ${y3}
      A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${x4} ${y4}
      Z
    `;

    paths.push(`<path d="${pathData}" fill="${item.color}" stroke="${colors.background.primary}" stroke-width="2"/>`);

    currentAngle = endAngle;
  });

  return `
<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
  ${paths.join('\n  ')}
  <circle cx="${cx}" cy="${cy}" r="${innerRadius}" fill="${colors.background.primary}"/>
  <text x="${cx}" y="${cy - 10}" font-size="24" font-weight="bold" fill="${colors.text.primary}" text-anchor="middle">${total}</text>
  <text x="${cx}" y="${cy + 10}" font-size="12" fill="${colors.text.secondary}" text-anchor="middle">всего</text>
</svg>
  `.trim();
}
