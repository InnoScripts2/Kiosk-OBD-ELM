/**
 * Главный генератор отчётов
 * Объединяет все компоненты для создания HTML и PDF отчётов
 */

import { promises as fs } from 'fs';
import path from 'path';
import {
  Report,
  ThicknessReport,
  DiagnosticsReport,
  ReportFormat,
  ReportGenerationOptions,
  ReportGenerationResult,
  ReportMetadata,
} from './types/index';
import { validateReport } from './utils/validators';
import { computeHash, generateFileId } from './utils/formatters';
import { generateThicknessHTML } from './templates/thickness-template';
import { generateDiagnosticsHTML } from './templates/diagnostics-template';
import { htmlToPDF, mockPDFRenderer } from './renderers/pdf-renderer';

/**
 * Главный класс генератора отчётов
 */
export class ReportGenerator {
  private reportsDir: string;
  private useMockPDF: boolean;

  constructor(reportsDir: string = './logs/reports', useMockPDF: boolean = false) {
    this.reportsDir = reportsDir;
    this.useMockPDF = useMockPDF;
  }

  /**
   * Генерирует отчёт в указанном формате
   */
  async generate(
    report: Report,
    options: ReportGenerationOptions = { format: 'html' }
  ): Promise<ReportGenerationResult> {
    // Валидация входных данных
    const validation = validateReport(report);
    if (!validation.valid) {
      throw new Error(`Validation failed: ${validation.errors.join(', ')}`);
    }

    if (validation.warnings.length > 0) {
      console.warn('[REPORT-GENERATOR] Warnings:', validation.warnings);
    }

    // Генерируем HTML
    const html = this.generateHTML(report, options);

    // Определяем формат вывода
    const format = options.format || 'html';
    let content: string | Buffer;
    let fileExtension: string;

    if (format === 'pdf') {
      // Генерируем PDF
      content = await this.generatePDF(html, options);
      fileExtension = 'pdf';
    } else {
      // Возвращаем HTML
      content = html;
      fileExtension = 'html';
    }

    // Вычисляем хэш
    const hash = await computeHash(content);

    // Генерируем имя файла
    const fileName = generateFileId(report.sessionId, report.type, fileExtension);
    const filePath = path.join(this.reportsDir, report.sessionId, fileName);

    // Сохраняем файл
    await this.saveFile(filePath, content);

    // Сохраняем метаданные
    await this.saveMetadata(report, filePath, hash, format);

    return {
      sessionId: report.sessionId,
      format,
      content,
      filePath,
      fileSize: Buffer.byteLength(content),
      hash,
      generatedAt: new Date(),
    };
  }

  /**
   * Генерирует HTML на основе типа отчёта
   */
  private generateHTML(report: Report, options: ReportGenerationOptions): string {
    const locale = options.locale || 'ru-RU';

    if (report.type === 'thickness') {
      return generateThicknessHTML(report as ThicknessReport, locale);
    } else if (report.type === 'diagnostics') {
      return generateDiagnosticsHTML(report as DiagnosticsReport, locale);
    } else {
      throw new Error(`Unknown report type: ${(report as any).type}`);
    }
  }

  /**
   * Генерирует PDF из HTML
   */
  private async generatePDF(html: string, options: ReportGenerationOptions): Promise<Buffer> {
    if (this.useMockPDF) {
      console.log('[REPORT-GENERATOR] Using mock PDF renderer');
      return mockPDFRenderer(html);
    }

    try {
      const pdfBuffer = await htmlToPDF(html, {
        dpi: options.dpi || 144,
        format: 'A4',
        printBackground: true,
        margin: {
          top: '1cm',
          right: '1cm',
          bottom: '1cm',
          left: '1cm',
        },
      });

      return pdfBuffer;
    } catch (error) {
      console.error('[REPORT-GENERATOR] Failed to generate PDF with Puppeteer, using mock:', error);
      // Fallback на mock если Puppeteer недоступен
      return mockPDFRenderer(html);
    }
  }

  /**
   * Сохраняет файл на диск
   */
  private async saveFile(filePath: string, content: string | Buffer): Promise<void> {
    try {
      // Создаём директорию если не существует
      const dir = path.dirname(filePath);
      await fs.mkdir(dir, { recursive: true });

      // Сохраняем файл
      await fs.writeFile(filePath, content);

      console.log(`[REPORT-GENERATOR] Saved report to ${filePath}`);
    } catch (error) {
      console.error('[REPORT-GENERATOR] Failed to save file:', error);
      throw new Error(`Failed to save report file: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  /**
   * Сохраняет метаданные отчёта
   */
  private async saveMetadata(
    report: Report,
    filePath: string,
    hash: string,
    format: ReportFormat
  ): Promise<void> {
    const metadata: ReportMetadata = {
      sessionId: report.sessionId,
      type: report.type,
      format,
      filePath,
      fileSize: 0, // Будет заполнено после сохранения файла
      hash,
      timestamp: new Date(),
      contact: report.contact,
      mode: report.mode || 'PROD',
    };

    // Получаем размер файла
    try {
      const stats = await fs.stat(filePath);
      metadata.fileSize = stats.size;
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to get file size:', error);
    }

    // Сохраняем метаданные в JSON файл
    const metadataPath = path.join(
      path.dirname(filePath),
      `${path.basename(filePath, path.extname(filePath))}.meta.json`
    );

    try {
      await fs.writeFile(metadataPath, JSON.stringify(metadata, null, 2));
      console.log(`[REPORT-GENERATOR] Saved metadata to ${metadataPath}`);
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to save metadata:', error);
    }
  }

  /**
   * Читает метаданные отчёта
   */
  async getMetadata(sessionId: string, fileName: string): Promise<ReportMetadata | null> {
    const metadataPath = path.join(
      this.reportsDir,
      sessionId,
      `${path.basename(fileName, path.extname(fileName))}.meta.json`
    );

    try {
      const content = await fs.readFile(metadataPath, 'utf-8');
      return JSON.parse(content);
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to read metadata:', error);
      return null;
    }
  }

  /**
   * Читает отчёт из файла
   */
  async getReport(sessionId: string, fileName: string): Promise<Buffer> {
    const filePath = path.join(this.reportsDir, sessionId, fileName);

    try {
      return await fs.readFile(filePath);
    } catch (error) {
      console.error('[REPORT-GENERATOR] Failed to read report:', error);
      throw new Error(`Report file not found: ${filePath}`);
    }
  }

  /**
   * Удаляет отчёт и его метаданные
   */
  async deleteReport(sessionId: string, fileName: string): Promise<void> {
    const filePath = path.join(this.reportsDir, sessionId, fileName);
    const metadataPath = path.join(
      this.reportsDir,
      sessionId,
      `${path.basename(fileName, path.extname(fileName))}.meta.json`
    );

    try {
      await fs.unlink(filePath);
      console.log(`[REPORT-GENERATOR] Deleted report ${filePath}`);
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to delete report:', error);
    }

    try {
      await fs.unlink(metadataPath);
      console.log(`[REPORT-GENERATOR] Deleted metadata ${metadataPath}`);
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to delete metadata:', error);
    }
  }

  /**
   * Удаляет все отчёты для сессии
   */
  async deleteSessionReports(sessionId: string): Promise<void> {
    const sessionDir = path.join(this.reportsDir, sessionId);

    try {
      await fs.rm(sessionDir, { recursive: true, force: true });
      console.log(`[REPORT-GENERATOR] Deleted all reports for session ${sessionId}`);
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to delete session reports:', error);
    }
  }

  /**
   * Возвращает список всех отчётов для сессии
   */
  async listSessionReports(sessionId: string): Promise<string[]> {
    const sessionDir = path.join(this.reportsDir, sessionId);

    try {
      const files = await fs.readdir(sessionDir);
      // Фильтруем только отчёты (не метаданные)
      return files.filter(file => !file.endsWith('.meta.json'));
    } catch (error) {
      console.warn('[REPORT-GENERATOR] Failed to list session reports:', error);
      return [];
    }
  }
}
