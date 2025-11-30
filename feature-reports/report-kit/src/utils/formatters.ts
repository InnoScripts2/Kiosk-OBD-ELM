/**
 * Утилиты для форматирования данных в отчётах
 */

/**
 * Форматирует дату и время для отчётов
 */
export function formatTimestamp(date: Date, locale: string = 'ru-RU'): string {
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

/**
 * Форматирует дату без времени
 */
export function formatDate(date: Date, locale: string = 'ru-RU'): string {
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date);
}

/**
 * Форматирует время без даты
 */
export function formatTime(date: Date, locale: string = 'ru-RU'): string {
  return new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

/**
 * Форматирует значение толщины с единицей измерения
 * @param value - значение в микронах (µm)
 */
export function formatThickness(value: number): string {
  return `${value.toFixed(1)} µm`;
}

/**
 * Форматирует цену
 */
export function formatPrice(price: number, currency: string = '₽'): string {
  return `${price.toLocaleString('ru-RU')} ${currency}`;
}

/**
 * Форматирует продолжительность в секундах
 */
export function formatDuration(seconds: number): string {
  if (seconds < 60) {
    return `${seconds} сек.`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes} мин. ${remainingSeconds} сек.`;
}

/**
 * Форматирует процент
 */
export function formatPercent(value: number, decimals: number = 1): string {
  return `${value.toFixed(decimals)}%`;
}

/**
 * Маскирует VIN код (оставляет первые 3 и последние 4 символа)
 */
export function maskVIN(vin: string): string {
  if (vin.length < 10) {
    return vin; // Слишком короткий, не маскируем
  }
  const prefix = vin.slice(0, 3);
  const suffix = vin.slice(-4);
  const middleLength = vin.length - 7;
  const masked = '*'.repeat(middleLength);
  return `${prefix}${masked}${suffix}`;
}

/**
 * Маскирует email (оставляет первый символ и домен)
 */
export function maskEmail(email: string): string {
  const [localPart, domain] = email.split('@');
  if (!domain) {
    return email; // Некорректный email
  }
  const masked = localPart[0] + '*'.repeat(Math.max(0, localPart.length - 1));
  return `${masked}@${domain}`;
}

/**
 * Маскирует телефон (оставляет код страны и последние 2 цифры)
 */
export function maskPhone(phone: string): string {
  // Убираем всё кроме цифр и +
  const cleaned = phone.replace(/[^\d+]/g, '');
  if (cleaned.length < 5) {
    return phone;
  }
  const prefix = cleaned.slice(0, 2); // +7 или +1 и т.д.
  const suffix = cleaned.slice(-2);
  const middleLength = cleaned.length - 4;
  const masked = '*'.repeat(middleLength);
  return `${prefix}${masked}${suffix}`;
}

/**
 * Экранирует HTML специальные символы
 */
export function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  };
  return text.replace(/[&<>"']/g, (char) => map[char]);
}

/**
 * Генерирует краткое описание для SMS
 */
export function generateSMSSummary(
  type: 'thickness' | 'diagnostics',
  sessionId: string,
  keyMetric: string
): string {
  if (type === 'thickness') {
    return `Отчёт толщиномера готов. ${keyMetric}. ID: ${sessionId.slice(-8)}`;
  } else {
    return `Диагностика OBD-II завершена. ${keyMetric}. ID: ${sessionId.slice(-8)}`;
  }
}

/**
 * Вычисляет цвет для тепловой карты на основе значения толщины
 */
export function getHeatmapColor(value: number, min: number, max: number): string {
  // Нормализуем значение в диапазон 0-1
  const normalized = (value - min) / (max - min);
  
  // Определяем цвет от зелёного (норма) к красному (отклонение)
  if (normalized < 0.33) {
    return '#10B981'; // Зелёный
  } else if (normalized < 0.67) {
    return '#F59E0B'; // Оранжевый
  } else {
    return '#EF4444'; // Красный
  }
}

/**
 * Определяет статус зоны измерения на основе значения
 */
export function determineZoneStatus(
  value: number,
  normalRange: { min: number; max: number }
): 'ok' | 'warning' | 'critical' {
  if (value >= normalRange.min && value <= normalRange.max) {
    return 'ok';
  } else if (value < normalRange.min * 0.8 || value > normalRange.max * 1.2) {
    return 'critical';
  } else {
    return 'warning';
  }
}

/**
 * Определяет рекомендацию на основе статистики
 */
export function generateRecommendation(
  deviationPercent: number,
  criticalCount: number
): string {
  if (criticalCount > 5) {
    return 'Обнаружены множественные критические отклонения. Рекомендуется детальный осмотр специалистом.';
  } else if (criticalCount > 0) {
    return 'Обнаружены отклонения в отдельных зонах. Рекомендуется проверка специалистом.';
  } else if (deviationPercent > 20) {
    return 'Значительная часть покрытия имеет отклонения от нормы. Возможны следы ремонта.';
  } else if (deviationPercent > 10) {
    return 'Некоторые зоны имеют отклонения. Состояние покрытия удовлетворительное.';
  } else {
    return 'Состояние лакокрасочного покрытия в норме. Признаков значительного ремонта не обнаружено.';
  }
}

/**
 * Определяет рекомендацию для OBD-II на основе кодов
 */
export function generateDiagnosticsRecommendation(
  criticalCount: number,
  warningCount: number
): string {
  if (criticalCount > 0) {
    return 'Обнаружены критические ошибки. Требуется немедленное обслуживание в автосервисе.';
  } else if (warningCount > 3) {
    return 'Обнаружены множественные предупреждения. Рекомендуется диагностика в автосервисе.';
  } else if (warningCount > 0) {
    return 'Обнаружены предупреждения. Рекомендуется проверка систем в ближайшее время.';
  } else {
    return 'Система автомобиля в норме. Критических ошибок не обнаружено.';
  }
}

/**
 * Генерирует уникальный идентификатор файла
 */
export function generateFileId(sessionId: string, type: string, format: string): string {
  const timestamp = Date.now();
  return `${type}_${sessionId}_${timestamp}.${format}`;
}

/**
 * Вычисляет SHA256 хэш строки или буфера
 */
export async function computeHash(content: string | Buffer): Promise<string> {
  const crypto = await import('crypto');
  const hash = crypto.createHash('sha256');
  hash.update(content);
  return hash.digest('hex');
}
