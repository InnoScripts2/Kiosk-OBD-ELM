/**
 * Типы данных для модуля отчётности
 * Определяют структуры данных для толщиномера и OBD-II диагностики
 */

/**
 * Режим работы приложения
 */
export type AppMode = 'DEV' | 'QA' | 'PROD';

/**
 * Общие поля для всех отчётов
 */
export interface BaseReport {
  sessionId: string;
  timestamp: Date;
  contact: {
    email?: string;
    phone?: string;
  };
  mode?: AppMode; // Помечает DEV режим в отчёте
}

/**
 * Зона измерения толщиномером
 */
export interface MeasurementZone {
  zone: string; // Название зоны (Капот, Крыша, Дверь передняя левая и т.д.)
  value: number; // Значение в микронах (µm)
  status: 'ok' | 'warning' | 'critical' | 'empty' | 'error';
  comment?: string; // Опциональный комментарий
}

/**
 * Статистика измерений
 */
export interface MeasurementStats {
  total: number; // Всего замеров
  completed: number; // Заполненных замеров
  average: number; // Среднее значение (µm)
  min: number; // Минимальное значение (µm)
  max: number; // Максимальное значение (µm)
  deviations: number; // Количество отклонений
  deviationPercent: number; // Процент отклонений
}

/**
 * Отчёт толщиномера
 */
export interface ThicknessReport extends BaseReport {
  type: 'thickness';
  vehicleType: string; // Седан/Хэтчбек, Минивэн, SUV
  price: number; // Цена услуги (₽)
  measurements: MeasurementZone[]; // 60 зон измерений
  stats: MeasurementStats;
  analysis: {
    recommendation: string;
    normalRange: { min: number; max: number }; // Нормальный диапазон для данного типа авто
  };
}

/**
 * Код ошибки DTC
 */
export interface DTCCode {
  code: string; // P0420, P0171 и т.д.
  description: string; // Расшифровка
  severity: 'info' | 'warning' | 'critical';
  system?: string; // Powertrain, Body, Chassis, Network
  recommendation?: string; // Рекомендация по устранению
}

/**
 * Статистика диагностики
 */
export interface DiagnosticsStats {
  totalCodes: number; // Всего кодов
  critical: number; // Критических
  warnings: number; // Предупреждений
  info: number; // Информационных
  cleared: number; // Сброшенных
}

/**
 * Результат сброса ошибок
 */
export interface ClearDTCResult {
  timestamp: Date;
  clearedCodes: string[]; // Коды которые были сброшены
  success: boolean;
  confirmationMethod: string; // Как подтверждено: "client_consent"
}

/**
 * Отчёт диагностики OBD-II
 */
export interface DiagnosticsReport extends BaseReport {
  type: 'diagnostics';
  vehicleBrand: string; // Toyota, Lexus, Hyundai, BMW
  vin?: string; // VIN код (звёздочки в середине для приватности)
  adapter: string; // Тип адаптера
  scanDuration: number; // Продолжительность сканирования (сек)
  price: number; // Цена услуги (₽)
  dtcCodes: DTCCode[]; // Коды ошибок
  stats: DiagnosticsStats;
  milStatus: 'on' | 'off'; // Статус Check Engine Light
  clearResult?: ClearDTCResult; // Результат сброса (если применимо)
}

/**
 * Объединённый тип отчётов
 */
export type Report = ThicknessReport | DiagnosticsReport;

/**
 * Формат вывода отчёта
 */
export type ReportFormat = 'html' | 'pdf';

/**
 * Опции генерации отчёта
 */
export interface ReportGenerationOptions {
  format: ReportFormat;
  locale?: string; // 'ru-RU' по умолчанию
  theme?: 'dark' | 'light'; // Тема оформления (dark по умолчанию)
  includeHeader?: boolean; // Включать ли шапку (логотип, контакты)
  includeFooter?: boolean; // Включать ли футер (юридическая инфо)
  dpi?: number; // DPI для PDF (144 по умолчанию)
}

/**
 * Результат генерации отчёта
 */
export interface ReportGenerationResult {
  sessionId: string;
  format: ReportFormat;
  content: string | Buffer; // HTML строка или PDF буфер
  filePath?: string; // Путь к сохранённому файлу
  fileSize: number; // Размер в байтах
  hash: string; // SHA256 хэш для проверки целостности
  generatedAt: Date;
}

/**
 * Метаданные отчёта для логирования
 */
export interface ReportMetadata {
  sessionId: string;
  type: 'thickness' | 'diagnostics';
  format: ReportFormat;
  filePath: string;
  fileSize: number;
  hash: string;
  timestamp: Date;
  contact: {
    email?: string;
    phone?: string;
  };
  mode: AppMode;
}
