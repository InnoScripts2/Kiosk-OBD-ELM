/**
 * Тесты для генератора отчётов
 */

import { ReportGenerator } from './report-generator';
import { ThicknessReport, DiagnosticsReport } from './types/index';
import { promises as fs } from 'fs';
import path from 'path';

describe('ReportGenerator', () => {
  const testReportsDir = '/tmp/test-reports';
  let generator: ReportGenerator;

  beforeEach(() => {
    generator = new ReportGenerator(testReportsDir, true); // useMockPDF = true
  });

  afterEach(async () => {
    // Очищаем тестовые файлы
    try {
      await fs.rm(testReportsDir, { recursive: true, force: true });
    } catch (error) {
      // Игнорируем ошибки при очистке
    }
  });

  describe('generate - Thickness Report', () => {
    it('генерирует HTML отчёт толщиномера', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_thickness_1',
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
          average: 125.5,
          min: 110,
          max: 140,
          deviations: 2,
          deviationPercent: 3.3,
        },
        analysis: {
          recommendation: 'Состояние покрытия в норме',
          normalRange: { min: 100, max: 150 },
        },
        mode: 'DEV',
      };

      const result = await generator.generate(report, { format: 'html' });

      expect(result.sessionId).toBe('test_thickness_1');
      expect(result.format).toBe('html');
      expect(result.content).toContain('<!DOCTYPE html>');
      expect(result.content).toContain('Отчёт по толщиномеру');
      expect(result.content).toContain('МОК-РЕЖИМ');
      expect(result.content).toContain('test@example.com');
      expect(result.filePath).toContain('test_thickness_1');
      expect(result.hash).toBeDefined();
      expect(result.fileSize).toBeGreaterThan(0);
    });

    it('генерирует PDF отчёт толщиномера (mock)', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_thickness_2',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
        },
        vehicleType: 'SUV',
        price: 450,
        measurements: Array(60).fill(null).map((_, i) => ({
          zone: `Зона ${i + 1}`,
          value: 130,
          status: 'ok' as const,
        })),
        stats: {
          total: 60,
          completed: 60,
          average: 130,
          min: 130,
          max: 130,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Идеальное состояние',
          normalRange: { min: 100, max: 150 },
        },
      };

      const result = await generator.generate(report, { format: 'pdf' });

      expect(result.sessionId).toBe('test_thickness_2');
      expect(result.format).toBe('pdf');
      expect(Buffer.isBuffer(result.content)).toBe(true);
      expect(result.content.toString()).toContain('%PDF');
      expect(result.filePath).toContain('.pdf');
    });

    it('сохраняет файл и метаданные на диск', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_thickness_3',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
        },
        vehicleType: 'Минивэн',
        price: 400,
        measurements: Array(60).fill(null).map((_, i) => ({
          zone: `Зона ${i + 1}`,
          value: 125,
          status: 'ok' as const,
        })),
        stats: {
          total: 60,
          completed: 60,
          average: 125,
          min: 125,
          max: 125,
          deviations: 0,
          deviationPercent: 0,
        },
        analysis: {
          recommendation: 'Отлично',
          normalRange: { min: 100, max: 150 },
        },
      };

      const result = await generator.generate(report, { format: 'html' });

      // Проверяем что filePath установлен
      expect(result.filePath).toBeDefined();
      
      // Проверяем существование файла
      const fileExists = await fs.access(result.filePath!).then(() => true).catch(() => false);
      expect(fileExists).toBe(true);

      // Проверяем существование метаданных
      const metaPath = result.filePath!.replace('.html', '.meta.json');
      const metaExists = await fs.access(metaPath).then(() => true).catch(() => false);
      expect(metaExists).toBe(true);

      // Читаем и проверяем метаданные
      const meta = await generator.getMetadata('test_thickness_3', path.basename(result.filePath!));
      expect(meta).toBeDefined();
      expect(meta?.sessionId).toBe('test_thickness_3');
      expect(meta?.type).toBe('thickness');
    });
  });

  describe('generate - Diagnostics Report', () => {
    it('генерирует HTML отчёт диагностики', async () => {
      const report: DiagnosticsReport = {
        type: 'diagnostics',
        sessionId: 'test_diagnostics_1',
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
            recommendation: 'Проверить каталитический нейтрализатор',
          },
          {
            code: 'P0171',
            description: 'System Too Lean Bank 1',
            severity: 'critical',
            system: 'Powertrain',
          },
        ],
        stats: {
          totalCodes: 2,
          critical: 1,
          warnings: 1,
          info: 0,
          cleared: 0,
        },
        milStatus: 'on',
        mode: 'DEV',
      };

      const result = await generator.generate(report, { format: 'html' });

      expect(result.sessionId).toBe('test_diagnostics_1');
      expect(result.format).toBe('html');
      expect(result.content).toContain('<!DOCTYPE html>');
      expect(result.content).toContain('Отчёт диагностики OBD-II');
      expect(result.content).toContain('МОК-РЕЖИМ');
      expect(result.content).toContain('P0420');
      expect(result.content).toContain('P0171');
      expect(result.content).toContain('Toyota');
      expect(result.content).toContain('1HG**********9186'); // Masked VIN
    });

    it('генерирует отчёт без ошибок', async () => {
      const report: DiagnosticsReport = {
        type: 'diagnostics',
        sessionId: 'test_diagnostics_2',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
        },
        vehicleBrand: 'BMW',
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
      };

      const result = await generator.generate(report, { format: 'html' });

      expect(result.content).toContain('Ошибок не обнаружено');
      expect(result.content).toContain('в норме');
    });

    it('включает результат сброса ошибок', async () => {
      const report: DiagnosticsReport = {
        type: 'diagnostics',
        sessionId: 'test_diagnostics_3',
        timestamp: new Date('2025-11-23T12:00:00Z'),
        contact: {
          email: 'test@example.com',
        },
        vehicleBrand: 'Lexus',
        adapter: 'ELM327',
        scanDuration: 50,
        price: 480,
        dtcCodes: [],
        stats: {
          totalCodes: 2,
          critical: 0,
          warnings: 2,
          info: 0,
          cleared: 2,
        },
        milStatus: 'off',
        clearResult: {
          timestamp: new Date('2025-11-23T12:05:00Z'),
          clearedCodes: ['P0420', 'P0171'],
          success: true,
          confirmationMethod: 'client_consent',
        },
      };

      const result = await generator.generate(report, { format: 'html' });

      expect(result.content).toContain('Результат сброса ошибок');
      expect(result.content).toContain('P0420');
      expect(result.content).toContain('P0171');
      expect(result.content).toContain('Успешно');
    });
  });

  describe('File Management', () => {
    it('читает отчёт из файла', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_read_1',
        timestamp: new Date(),
        contact: { email: 'test@example.com' },
        vehicleType: 'Седан',
        price: 350,
        measurements: Array(60).fill(null).map((_, i) => ({
          zone: `Зона ${i + 1}`,
          value: 120,
          status: 'ok' as const,
        })),
        stats: {
          total: 60,
          completed: 60,
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
      };

      const generated = await generator.generate(report, { format: 'html' });
      expect(generated.filePath).toBeDefined();
      const fileName = path.basename(generated.filePath!);
      
      const readContent = await generator.getReport('test_read_1', fileName);
      expect(readContent).toBeDefined();
      expect(readContent.toString()).toContain('<!DOCTYPE html>');
    });

    it('удаляет отчёт', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_delete_1',
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
      };

      const generated = await generator.generate(report, { format: 'html' });
      expect(generated.filePath).toBeDefined();
      const fileName = path.basename(generated.filePath!);

      await generator.deleteReport('test_delete_1', fileName);

      const fileExists = await fs.access(generated.filePath!).then(() => true).catch(() => false);
      expect(fileExists).toBe(false);
    });

    it('перечисляет отчёты сессии', async () => {
      const report: ThicknessReport = {
        type: 'thickness',
        sessionId: 'test_list_1',
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
      };

      await generator.generate(report, { format: 'html' });
      await generator.generate(report, { format: 'pdf' });

      const files = await generator.listSessionReports('test_list_1');
      expect(files.length).toBe(2);
      expect(files.some(f => f.endsWith('.html'))).toBe(true);
      expect(files.some(f => f.endsWith('.pdf'))).toBe(true);
    });
  });

  describe('Validation', () => {
    it('выбрасывает ошибку при невалидных данных', async () => {
      const invalidReport = {
        type: 'thickness',
        sessionId: '', // Пустой sessionId - невалидно
        timestamp: new Date(),
        contact: {},
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

      await expect(generator.generate(invalidReport, { format: 'html' }))
        .rejects
        .toThrow('Validation failed');
    });
  });
});
