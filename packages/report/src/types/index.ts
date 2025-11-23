/**
 * Типы данных для генерации отчётов
 * Соответствует требованиям инструкций: минимум персональных данных, реальные измерения
 */

export type ServiceType = 'thickness' | 'obd';
export type AppMode = 'DEV' | 'QA' | 'PROD';

/**
 * Базовые данные сессии
 */
export interface SessionData {
  sessionId: string;
  timestamp: Date;
  clientContact: {
    phone?: string;
    email?: string;
  };
  mode: AppMode; // DEV режим должен быть явно отмечен
}

/**
 * Данные измерений толщиномера (60 зон)
 */
export interface ThicknessMeasurement {
  zone: string; // Капот, Крыша, Дверь ЛП и т.д.
  value: number; // В микронах (µm)
  status: 'ok' | 'warning' | 'critical' | 'empty';
  comment?: string;
}

/**
 * Статус толщиномера
 */
export interface ThicknessAnalysis {
  totalMeasurements: number;
  avgValue: number; // Среднее в µm
  minValue: number;
  maxValue: number;
  deviationCount: number; // Количество отклонений
  normalRange: [number, number]; // Нормальный диапазон [min, max]
  recommendation: string;
}

/**
 * Полный отчёт толщиномера
 */
export interface ThicknessReportData extends SessionData {
  serviceType: 'thickness';
  vehicleType: string; // Седан, Минивэн, SUV
  price: number; // В рублях
  measurements: ThicknessMeasurement[]; // 40-60 замеров
  analysis: ThicknessAnalysis;
}

/**
 * DTC код ошибки
 */
export interface DTCCode {
  code: string; // P0420, P0171 и т.д.
  description: string;
  severity: 'info' | 'warning' | 'critical';
  recommendation?: string;
  category?: string; // Powertrain, Body, Chassis, Network
}

/**
 * Результаты сброса ошибок
 */
export interface ClearDTCResult {
  timestamp: Date;
  clearedCodes: string[]; // Список сброшенных кодов
  success: boolean;
  clientConfirmed: boolean; // Клиент подтвердил сброс
}

/**
 * Статус MIL (Malfunction Indicator Lamp)
 */
export interface MILStatus {
  on: boolean;
  distanceSinceClear: number; // км
  timeSinceClear: number; // минут
}

/**
 * Полный отчёт диагностики OBD-II
 */
export interface OBDReportData extends SessionData {
  serviceType: 'obd';
  vehicleBrand: string; // Toyota, Lexus, etc.
  vin?: string; // VIN (маскируем середину в отчёте)
  adapterType: string; // ELM327 Bluetooth, etc.
  scanDuration: number; // Длительность сканирования в секундах
  dtcCodes: DTCCode[];
  milStatus: MILStatus;
  clearResult?: ClearDTCResult;
  price: number; // В рублях
}

/**
 * Объединённый тип для любого отчёта
 */
export type ReportData = ThicknessReportData | OBDReportData;

/**
 * Конфигурация темы отчёта
 */
export interface ReportTheme {
  backgroundColor: string;
  textColor: string;
  accentColor: string;
  secondaryAccent: string;
  fontFamily: string;
  fontSize: {
    h1: string;
    h2: string;
    body: string;
    small: string;
  };
  spacing: {
    page: { top: number; bottom: number; left: number; right: number };
    section: number;
    grid: number;
  };
}

/**
 * Опции генерации отчёта
 */
export interface ReportGenerationOptions {
  format: 'html' | 'pdf' | 'both';
  theme?: ReportTheme;
  locale?: string; // 'ru' по умолчанию
  dpi?: number; // ≥ 144 для PDF
  includeQR?: boolean; // Включать QR-код с ссылкой на отчёт
}

/**
 * Результат генерации отчёта
 */
export interface ReportGenerationResult {
  sessionId: string;
  htmlPath?: string;
  pdfPath?: string;
  htmlSize?: number; // байты
  pdfSize?: number; // байты
  generatedAt: Date;
  metadata: {
    serviceType: ServiceType;
    mode: AppMode;
    hash: string; // SHA-256 hash содержимого
  };
}
