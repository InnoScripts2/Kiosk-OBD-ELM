/**
 * Утилиты для работы с отчётами
 */

import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { createHash } from 'crypto';

/**
 * Форматировать дату для отчёта
 */
export function formatDate(date: Date, formatStr: string = 'dd MMMM yyyy, HH:mm'): string {
  return format(date, formatStr, { locale: ru });
}

/**
 * Форматировать число с единицами измерения
 */
export function formatValue(value: number, unit: 'μm' | 'mm' | '₽' | 'сек' | 'км'): string {
  const rounded = Math.round(value * 100) / 100;
  return `${rounded.toLocaleString('ru-RU')} ${unit}`;
}

/**
 * Маскировать VIN (звёздочки для середины)
 */
export function maskVIN(vin: string): string {
  if (!vin || vin.length < 10) return vin;
  const start = vin.substring(0, 4);
  const end = vin.substring(vin.length - 4);
  const middle = '*'.repeat(vin.length - 8);
  return `${start}${middle}${end}`;
}

/**
 * Вычислить SHA-256 hash строки
 */
export function calculateHash(content: string): string {
  return createHash('sha256').update(content).digest('hex');
}

/**
 * Валидация данных толщиномера
 */
export function validateThicknessData(data: unknown): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  if (!data || typeof data !== 'object') {
    errors.push('Данные должны быть объектом');
    return { valid: false, errors };
  }
  
  const d = data as Record<string, unknown>;
  
  if (!d.sessionId || typeof d.sessionId !== 'string') {
    errors.push('sessionId обязателен и должен быть строкой');
  }
  
  if (!d.timestamp || !(d.timestamp instanceof Date)) {
    errors.push('timestamp обязателен и должен быть Date');
  }
  
  if (!d.vehicleType || typeof d.vehicleType !== 'string') {
    errors.push('vehicleType обязателен');
  }
  
  if (!Array.isArray(d.measurements)) {
    errors.push('measurements должен быть массивом');
  } else if (d.measurements.length < 40 || d.measurements.length > 60) {
    errors.push('measurements должен содержать 40-60 замеров');
  }
  
  if (!d.analysis || typeof d.analysis !== 'object') {
    errors.push('analysis обязателен');
  }
  
  return { valid: errors.length === 0, errors };
}

/**
 * Валидация данных OBD-II
 */
export function validateOBDData(data: unknown): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  if (!data || typeof data !== 'object') {
    errors.push('Данные должны быть объектом');
    return { valid: false, errors };
  }
  
  const d = data as Record<string, unknown>;
  
  if (!d.sessionId || typeof d.sessionId !== 'string') {
    errors.push('sessionId обязателен и должен быть строкой');
  }
  
  if (!d.timestamp || !(d.timestamp instanceof Date)) {
    errors.push('timestamp обязателен и должен быть Date');
  }
  
  if (!d.vehicleBrand || typeof d.vehicleBrand !== 'string') {
    errors.push('vehicleBrand обязателен');
  }
  
  if (!Array.isArray(d.dtcCodes)) {
    errors.push('dtcCodes должен быть массивом');
  }
  
  if (typeof d.scanDuration !== 'number') {
    errors.push('scanDuration должен быть числом');
  }
  
  return { valid: errors.length === 0, errors };
}

/**
 * Определить категорию DTC по первому символу
 */
export function getDTCCategory(code: string): string {
  if (!code || code.length < 1) return 'Unknown';
  
  const prefix = code.charAt(0).toUpperCase();
  switch (prefix) {
    case 'P': return 'Powertrain (Трансмиссия)';
    case 'C': return 'Chassis (Шасси)';
    case 'B': return 'Body (Кузов)';
    case 'U': return 'Network (Сеть)';
    default: return 'Unknown';
  }
}

/**
 * Получить цвет для статуса измерения
 */
export function getStatusColor(status: string): string {
  switch (status) {
    case 'ok': return '#00C4B4';
    case 'warning': return '#FFC857';
    case 'critical': return '#FF5757';
    case 'info': return '#6496FF';
    default: return '#888';
  }
}

/**
 * Генерировать SVG иконку статуса
 */
export function getStatusIcon(status: string): string {
  const color = getStatusColor(status);
  
  switch (status) {
    case 'ok':
      return `<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" stroke="${color}" stroke-width="2" fill="none"/>
        <path d="M6 10L9 13L14 7" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>`;
    case 'warning':
      return `<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M10 2L2 17H18L10 2Z" stroke="${color}" stroke-width="2" stroke-linejoin="round" fill="none"/>
        <line x1="10" y1="8" x2="10" y2="12" stroke="${color}" stroke-width="2" stroke-linecap="round"/>
        <circle cx="10" cy="15" r="1" fill="${color}"/>
      </svg>`;
    case 'critical':
      return `<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" stroke="${color}" stroke-width="2" fill="none"/>
        <line x1="6" y1="6" x2="14" y2="14" stroke="${color}" stroke-width="2" stroke-linecap="round"/>
        <line x1="14" y1="6" x2="6" y2="14" stroke="${color}" stroke-width="2" stroke-linecap="round"/>
      </svg>`;
    default:
      return `<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" stroke="${color}" stroke-width="2" fill="none"/>
      </svg>`;
  }
}
