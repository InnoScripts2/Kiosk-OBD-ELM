/**
 * Тесты для валидаторов отчётов
 */

import {
  validateThicknessReport,
  validateDiagnosticsReport,
  validateReport,
} from './validators';
import { ThicknessReport, DiagnosticsReport } from '../types/index';

describe('Validators', () => {
  describe('validateThicknessReport', () => {
    it('валидирует корректный отчёт толщиномера', () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_session_1',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
          phone: '+79001234567',
        },
        vehicleType: 'Седан',
        price: 350,
        measurements: Array(60).fill(null).map((_, i) => ({
          zone: `Зона ${i + 1}`,
          value: 120 + Math.random() * 20,
          status: 'ok' as const,
        })),
        stats: {
          total: 60,
          completed: 60,
          average: 125,
          min: 110,
          max: 140,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Состояние покрытия в норме',
          normalRange: { min: 100, max: 150 },
        },
      };

      const result = validateThicknessReport(report);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('выявляет отсутствие sessionId', () => {
      const report = {
        type: 'thickness',
        sessionId: '',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleType: 'Седан',
        price: 350,
        measurements: [],
        stats: {
          total: 0,
          completed: 0,
          average: 0,
          min: 0,
          max: 0,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Тест',
          normalRange: { min: 100, max: 150 },
        },
      } as ThicknessReport;

      const result = validateThicknessReport(report);
      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.stringContaining('SessionId'));
    });

    it('выявляет некорректный email', () => {
      const report = {
        type: 'thickness',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'invalid-email' },
        vehicleType: 'Седан',
        price: 350,
        measurements: [],
        stats: {
          total: 0,
          completed: 0,
          average: 0,
          min: 0,
          max: 0,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Тест',
          normalRange: { min: 100, max: 150 },
        },
      } as ThicknessReport;

      const result = validateThicknessReport(report);
      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.stringContaining('email'));
    });

    it('предупреждает о некорректном количестве замеров', () => {
      const report = {
        type: 'thickness',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleType: 'Седан',
        price: 350,
        measurements: [
          { zone: 'Капот', value: 120, status: 'ok' },
        ],
        stats: {
          total: 60,
          completed: 1,
          average: 120,
          min: 120,
          max: 120,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Тест',
          normalRange: { min: 100, max: 150 },
        },
      } as ThicknessReport;

      const result = validateThicknessReport(report);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('60 замеров');
    });
  });

  describe('validateDiagnosticsReport', () => {
    it('валидирует корректный отчёт диагностики', () => {
      const report: DiagnosticsReport = {
        type: 'diagnostics',
        sessionId: 'test_session_2',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
        },
        vehicleBrand: 'Toyota',
        vin: '1HGBH41JXMN109186',
        adapter: 'ELM327 v2.1',
        scanDuration: 45,
        price: 480,
        dtcCodes: [
          {
            code: 'P0420',
            description: 'Catalyst System Efficiency Below Threshold',
            severity: 'warning',
            system: 'Powertrain',
          },
        ],
        stats: {
          totalCodes: 1,
          critical: 0,
          warnings: 1,
          info: 0,
          cleared: 0,
        },
        milStatus: 'on',
      };

      const result = validateDiagnosticsReport(report);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('выявляет некорректный код DTC', () => {
      const report = {
        type: 'diagnostics',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleBrand: 'Toyota',
        adapter: 'ELM327',
        scanDuration: 30,
        price: 480,
        dtcCodes: [
          {
            code: 'INVALID',
            description: 'Test',
            severity: 'info',
          },
        ],
        stats: {
          totalCodes: 1,
          critical: 0,
          warnings: 0,
          info: 1,
          cleared: 0,
        },
        milStatus: 'off',
      } as DiagnosticsReport;

      const result = validateDiagnosticsReport(report);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('некорректный формат кода');
    });

    it('выявляет несоответствие суммы кодов', () => {
      const report = {
        type: 'diagnostics',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleBrand: 'Toyota',
        adapter: 'ELM327',
        scanDuration: 30,
        price: 480,
        dtcCodes: [],
        stats: {
          totalCodes: 5,
          critical: 1,
          warnings: 1,
          info: 1,
          cleared: 0,
        },
        milStatus: 'off',
      } as DiagnosticsReport;

      const result = validateDiagnosticsReport(report);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('не совпадает');
    });

    it('выявляет некорректный milStatus', () => {
      const report: DiagnosticsReport = {
        type: 'diagnostics',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleBrand: 'Toyota',
        adapter: 'ELM327',
        scanDuration: 30,
        price: 480,
        dtcCodes: [],
        stats: {
          totalCodes: 0,
          critical: 0,
          warnings: 0,
          info: 0,
          cleared: 0,
        },
        milStatus: 'invalid' as any,
      };

      const result = validateDiagnosticsReport(report);
      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.stringContaining('milStatus'));
    });
  });

  describe('validateReport', () => {
    it('автоопределяет тип отчёта толщиномера', () => {
      const report = {
        type: 'thickness',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleType: 'Седан',
        price: 350,
        measurements: [],
        stats: {
          total: 0,
          completed: 0,
          average: 0,
          min: 0,
          max: 0,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Тест',
          normalRange: { min: 100, max: 150 },
        },
      } as ThicknessReport;

      const result = validateReport(report);
      expect(result).toBeDefined();
    });

    it('автоопределяет тип отчёта диагностики', () => {
      const report = {
        type: 'diagnostics',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleBrand: 'Toyota',
        adapter: 'ELM327',
        scanDuration: 30,
        price: 480,
        dtcCodes: [],
        stats: {
          totalCodes: 0,
          critical: 0,
          warnings: 0,
          info: 0,
          cleared: 0,
        },
        milStatus: 'off',
      } as DiagnosticsReport;

      const result = validateReport(report);
      expect(result).toBeDefined();
    });

    it('выявляет неизвестный тип отчёта', () => {
      const report = {
        type: 'unknown',
        sessionId: 'test_1',
        timestamp: new Date(),
        contact: {},
      } as any;

      const result = validateReport(report);
      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.stringContaining('Неизвестный тип'));
    });
  });
});
