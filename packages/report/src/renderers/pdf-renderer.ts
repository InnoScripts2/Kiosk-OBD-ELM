/**
 * Рендерер PDF из HTML
 * Использует Puppeteer для конвертации HTML в PDF с высоким DPI
 */

/**
 * Опции для генерации PDF
 */
export interface PDFRenderOptions {
  dpi?: number; // DPI для PDF (по умолчанию 144)
  format?: 'A4' | 'Letter'; // Формат страницы
  margin?: {
    top?: string;
    right?: string;
    bottom?: string;
    left?: string;
  };
  printBackground?: boolean; // Печатать ли фоновые цвета/изображения
  preferCSSPageSize?: boolean; // Использовать ли размер из CSS @page
}

/**
 * Конвертирует HTML в PDF с использованием Puppeteer
 * 
 * @param html - HTML содержимое отчёта
 * @param options - опции рендеринга
 * @returns Buffer с PDF данными
 */
export async function htmlToPDF(html: string, options: PDFRenderOptions = {}): Promise<Buffer> {
  try {
    // Динамический импорт Puppeteer (не включён в devDependencies на данном этапе)
    // В реальной реализации нужно добавить puppeteer в package.json
    // @ts-ignore - optional dependency
    const puppeteer = await import('puppeteer').catch(() => {
      throw new Error('Puppeteer не установлен. Выполните: npm install puppeteer');
    });

    const {
      dpi = 144,
      format = 'A4',
      margin = {
        top: '1cm',
        right: '1cm',
        bottom: '1cm',
        left: '1cm',
      },
      printBackground = true,
      preferCSSPageSize = false,
    } = options;

    // Запускаем headless браузер
    const browser = await puppeteer.launch({
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-gpu',
      ],
    });

    try {
      const page = await browser.newPage();

      // Устанавливаем содержимое страницы
      await page.setContent(html, {
        waitUntil: ['load', 'networkidle0'],
      });

      // Вычисляем scale для нужного DPI (72 - базовый DPI PDF)
      const scale = dpi / 72;

      // Генерируем PDF
      const pdfBuffer = await page.pdf({
        format,
        margin,
        printBackground,
        preferCSSPageSize,
        scale,
      });

      return pdfBuffer;
    } finally {
      await browser.close();
    }
  } catch (error) {
    console.error('[PDF-RENDERER] Ошибка генерации PDF:', error);
    throw new Error(`Failed to generate PDF: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Альтернативный рендерер с использованием Playwright (если Puppeteer недоступен)
 */
export async function htmlToPDFWithPlaywright(html: string, options: PDFRenderOptions = {}): Promise<Buffer> {
  try {
    // @ts-ignore - optional dependency
    const playwright = await import('playwright').catch(() => {
      throw new Error('Playwright не установлен. Выполните: npm install playwright');
    });

    const {
      dpi = 144,
      format = 'A4',
      margin = {
        top: '1cm',
        right: '1cm',
        bottom: '1cm',
        left: '1cm',
      },
      printBackground = true,
      preferCSSPageSize = false,
    } = options;

    const browser = await playwright.chromium.launch({
      headless: true,
    });

    try {
      const context = await browser.newContext();
      const page = await context.newPage();

      await page.setContent(html, {
        waitUntil: 'networkidle',
      });

      const scale = dpi / 72;

      const pdfBuffer = await page.pdf({
        format,
        margin,
        printBackground,
        preferCSSPageSize,
        scale,
      });

      return Buffer.from(pdfBuffer);
    } finally {
      await browser.close();
    }
  } catch (error) {
    console.error('[PDF-RENDERER] Ошибка генерации PDF с Playwright:', error);
    throw new Error(`Failed to generate PDF with Playwright: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Mock рендерер для тестирования (без реального браузера)
 * Возвращает фиктивный PDF-заголовок с HTML содержимым
 */
export function mockPDFRenderer(html: string): Buffer {
  // Простой фиктивный PDF для тестирования
  // В реальности это должен быть настоящий PDF, но для тестов достаточно заголовка
  const pdfHeader = '%PDF-1.4\n';
  const mockContent = `${pdfHeader}% Mock PDF for testing\n% HTML Length: ${html.length} bytes\n`;
  return Buffer.from(mockContent + html.substring(0, 200));
}
