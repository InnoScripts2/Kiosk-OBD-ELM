/**
 * Утилиты для валидации данных отчётов
 */

import { ThicknessReport, DiagnosticsReport, Report } from '../types/index.js';

/**
 * Результат валидации
 */
export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Валидирует базовые поля отчёта
 */
function validateBaseReport(report: Report): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (!report.sessionId || report.sessionId.trim() === '') {
    errors.push('SessionId обязателен');
  }

  if (!report.timestamp || !(report.timestamp instanceof Date)) {
    errors.push('Timestamp должен быть экземпляром Date');
  } else if (isNaN(report.timestamp.getTime())) {
    errors.push('Timestamp содержит некорректную дату');
  }

  if (!report.contact.email && !report.contact.phone) {
    warnings.push('Не указан ни email, ни телефон для контакта');
  }

  if (report.contact.email && !isValidEmail(report.contact.email)) {
    errors.push('Некорректный формат email');
  }

  if (report.contact.phone && !isValidPhone(report.contact.phone)) {
    errors.push('Некорректный формат телефона');
  }

  return { valid: errors.length === 0, errors, warnings };
}

/**
 * Валидирует отчёт толщиномера
 */
export function validateThicknessReport(report: ThicknessReport): ValidationResult {
  const baseResult = validateBaseReport(report);
  const errors = [...baseResult.errors];
  const warnings = [...baseResult.warnings];

  if (!report.vehicleType || report.vehicleType.trim() === '') {
    errors.push('VehicleType обязателен');
  }

  if (typeof report.price !== 'number' || report.price <= 0) {
    errors.push('Price должен быть положительным числом');
  }

  if (!Array.isArray(report.measurements)) {
    errors.push('Measurements должен быть массивом');
  } else {
    if (report.measurements.length !== 60) {
      warnings.push(`Ожидается 60 замеров, получено ${report.measurements.length}`);
    }

    report.measurements.forEach((measurement, index) => {
      if (!measurement.zone || measurement.zone.trim() === '') {
        errors.push(`Замер ${index}: zone обязателен`);
      }

      if (typeof measurement.value !== 'number') {
        errors.push(`Замер ${index}: value должен быть числом`);
      } else if (measurement.value < 0) {
        errors.push(`Замер ${index}: value не может быть отрицательным`);
      } else if (measurement.value > 5000) {
        warnings.push(`Замер ${index}: необычно высокое значение (${measurement.value} µm)`);
      }

      const validStatuses = ['ok', 'warning', 'critical', 'empty', 'error'];
      if (!validStatuses.includes(measurement.status)) {
        errors.push(`Замер ${index}: некорректный статус "${measurement.status}"`);
      }
    });
  }

  if (!report.stats) {
    errors.push('Stats обязателен');
  } else {
    if (typeof report.stats.total !== 'number' || report.stats.total < 0) {
      errors.push('Stats.total должен быть неотрицательным числом');
    }

    if (typeof report.stats.completed !== 'number' || report.stats.completed < 0) {
      errors.push('Stats.completed должен быть неотрицательным числом');
    }

    if (report.stats.completed > report.stats.total) {
      errors.push('Stats.completed не может быть больше stats.total');
    }

    if (typeof report.stats.average !== 'number' || report.stats.average < 0) {
      errors.push('Stats.average должен быть неотрицательным числом');
    }

    if (typeof report.stats.min !== 'number' || report.stats.min < 0) {
      errors.push('Stats.min должен быть неотрицательным числом');
    }

    if (typeof report.stats.max !== 'number' || report.stats.max < 0) {
      errors.push('Stats.max должен быть неотрицательным числом');
    }

    if (report.stats.min > report.stats.max) {
      errors.push('Stats.min не может быть больше stats.max');
    }
  }

  if (!report.analysis) {
    errors.push('Analysis обязателен');
  } else {
    if (!report.analysis.recommendation || report.analysis.recommendation.trim() === '') {
      warnings.push('Analysis.recommendation пуст');
    }

    if (!report.analysis.normalRange) {
      errors.push('Analysis.normalRange обязателен');
    } else {
      if (report.analysis.normalRange.min >= report.analysis.normalRange.max) {
        errors.push('Analysis.normalRange.min должен быть меньше max');
      }
    }
  }

  return { valid: errors.length === 0, errors, warnings };
}

/**
 * Валидирует отчёт диагностики OBD-II
 */
export function validateDiagnosticsReport(report: DiagnosticsReport): ValidationResult {
  const baseResult = validateBaseReport(report);
  const errors = [...baseResult.errors];
  const warnings = [...baseResult.warnings];

  if (!report.vehicleBrand || report.vehicleBrand.trim() === '') {
    errors.push('VehicleBrand обязателен');
  }

  if (report.vin && report.vin.length < 10) {
    warnings.push('VIN код кажется слишком коротким');
  }

  if (!report.adapter || report.adapter.trim() === '') {
    warnings.push('Adapter не указан');
  }

  if (typeof report.scanDuration !== 'number' || report.scanDuration < 0) {
    errors.push('ScanDuration должен быть неотрицательным числом');
  } else if (report.scanDuration > 300) {
    warnings.push(`Необычно долгое сканирование: ${report.scanDuration} сек.`);
  }

  if (typeof report.price !== 'number' || report.price <= 0) {
    errors.push('Price должен быть положительным числом');
  }

  if (!Array.isArray(report.dtcCodes)) {
    errors.push('DtcCodes должен быть массивом');
  } else {
    report.dtcCodes.forEach((dtc, index) => {
      if (!dtc.code || dtc.code.trim() === '') {
        errors.push(`DTC ${index}: code обязателен`);
      } else if (!isValidDTCCode(dtc.code)) {
        warnings.push(`DTC ${index}: некорректный формат кода "${dtc.code}"`);
      }

      if (!dtc.description || dtc.description.trim() === '') {
        warnings.push(`DTC ${index}: description пуст`);
      }

      const validSeverities = ['info', 'warning', 'critical'];
      if (!validSeverities.includes(dtc.severity)) {
        errors.push(`DTC ${index}: некорректный severity "${dtc.severity}"`);
      }
    });
  }

  if (!report.stats) {
    errors.push('Stats обязателен');
  } else {
    if (typeof report.stats.totalCodes !== 'number' || report.stats.totalCodes < 0) {
      errors.push('Stats.totalCodes должен быть неотрицательным числом');
    }

    const sum = report.stats.critical + report.stats.warnings + report.stats.info;
    if (sum !== report.stats.totalCodes) {
      warnings.push(
        `Сумма critical+warnings+info (${sum}) не совпадает с totalCodes (${report.stats.totalCodes})`
      );
    }

    if (report.stats.cleared > report.stats.totalCodes) {
      errors.push('Stats.cleared не может быть больше stats.totalCodes');
    }
  }

  const validMilStatuses = ['on', 'off'];
  if (!validMilStatuses.includes(report.milStatus)) {
    errors.push(`Некорректный milStatus "${report.milStatus}"`);
  }

  if (report.clearResult) {
    if (!report.clearResult.timestamp || !(report.clearResult.timestamp instanceof Date)) {
      errors.push('ClearResult.timestamp должен быть экземпляром Date');
    }

    if (!Array.isArray(report.clearResult.clearedCodes)) {
      errors.push('ClearResult.clearedCodes должен быть массивом');
    }

    if (typeof report.clearResult.success !== 'boolean') {
      errors.push('ClearResult.success должен быть boolean');
    }

    if (!report.clearResult.confirmationMethod || report.clearResult.confirmationMethod.trim() === '') {
      warnings.push('ClearResult.confirmationMethod не указан');
    }
  }

  return { valid: errors.length === 0, errors, warnings };
}

/**
 * Валидирует отчёт (автоопределение типа)
 */
export function validateReport(report: Report): ValidationResult {
  if (report.type === 'thickness') {
    return validateThicknessReport(report as ThicknessReport);
  } else if (report.type === 'diagnostics') {
    return validateDiagnosticsReport(report as DiagnosticsReport);
  } else {
    return {
      valid: false,
      errors: [`Неизвестный тип отчёта: ${(report as any).type}`],
      warnings: [],
    };
  }
}

/**
 * Проверяет корректность email
 */
function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

/**
 * Проверяет корректность телефона
 */
function isValidPhone(phone: string): boolean {
  // Простая проверка: должен содержать хотя бы 10 цифр
  const digits = phone.replace(/\D/g, '');
  return digits.length >= 10;
}

/**
 * Проверяет корректность кода DTC
 */
function isValidDTCCode(code: string): boolean {
  // Коды DTC обычно имеют формат: P0420, U0100, B1234, C2345
  // Первый символ: P (Powertrain), B (Body), C (Chassis), U (Network)
  // Далее 4 цифры
  const dtcRegex = /^[PBCU]\d{4}$/;
  return dtcRegex.test(code);
}
