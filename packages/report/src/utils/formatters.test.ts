/**
 * Тесты для утилит форматирования
 */

import {
  formatTimestamp,
  formatDate,
  formatTime,
  formatThickness,
  formatPrice,
  formatDuration,
  formatPercent,
  maskVIN,
  maskEmail,
  maskPhone,
  escapeHtml,
  generateSMSSummary,
  getHeatmapColor,
  determineZoneStatus,
  generateRecommendation,
  generateDiagnosticsRecommendation,
} from '../utils/formatters';

describe('Formatters', () => {
  describe('formatTimestamp', () => {
    it('форматирует дату и время', () => {
      const date = new Date('2025-11-23T12:30:45Z');
      const result = formatTimestamp(date, 'ru-RU');
      expect(result).toContain('2025');
      expect(result).toContain('12');
      expect(result).toContain('30');
    });
  });

  describe('formatThickness', () => {
    it('форматирует значение толщины с единицей измерения', () => {
      expect(formatThickness(123.45)).toBe('123.5 µm');
      expect(formatThickness(100)).toBe('100.0 µm');
    });
  });

  describe('formatPrice', () => {
    it('форматирует цену с символом рубля', () => {
      expect(formatPrice(350)).toBe('350 ₽');
      expect(formatPrice(1500)).toContain('500');
    });
  });

  describe('formatDuration', () => {
    it('форматирует продолжительность в секундах', () => {
      expect(formatDuration(30)).toBe('30 сек.');
      expect(formatDuration(65)).toBe('1 мин. 5 сек.');
      expect(formatDuration(120)).toBe('2 мин. 0 сек.');
    });
  });

  describe('formatPercent', () => {
    it('форматирует проценты', () => {
      expect(formatPercent(25.5)).toBe('25.5%');
      expect(formatPercent(10)).toBe('10.0%');
    });
  });

  describe('maskVIN', () => {
    it('маскирует VIN код', () => {
      const vin = '1HGBH41JXMN109186';
      const masked = maskVIN(vin);
      expect(masked).toContain('1HG');
      expect(masked).toContain('9186');
      expect(masked).toContain('*');
      expect(masked.length).toBe(vin.length);
    });

    it('не маскирует слишком короткий VIN', () => {
      const vin = 'SHORT';
      expect(maskVIN(vin)).toBe(vin);
    });
  });

  describe('maskEmail', () => {
    it('маскирует email', () => {
      const masked = maskEmail('test@example.com');
      expect(masked).toContain('t*');
      expect(masked).toContain('@example.com');
    });
  });

  describe('maskPhone', () => {
    it('маскирует телефон', () => {
      const masked = maskPhone('+79001234567');
      expect(masked).toContain('+7');
      expect(masked).toContain('67');
      expect(masked).toContain('*');
    });
  });

  describe('escapeHtml', () => {
    it('экранирует HTML специальные символы', () => {
      expect(escapeHtml('<script>alert("XSS")</script>')).toBe(
        '&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;'
      );
      expect(escapeHtml("Test & 'quote'")).toContain('&amp;');
      expect(escapeHtml("Test & 'quote'")).toContain('&#039;');
    });
  });

  describe('generateSMSSummary', () => {
    it('генерирует краткое SMS для толщиномера', () => {
      const summary = generateSMSSummary('thickness', 'session_12345678', 'Среднее: 125 µm');
      expect(summary).toContain('Отчёт толщиномера');
      expect(summary).toContain('Среднее: 125 µm');
      expect(summary).toContain('12345678');
    });

    it('генерирует краткое SMS для диагностики', () => {
      const summary = generateSMSSummary('diagnostics', 'session_87654321', '3 ошибки');
      expect(summary).toContain('Диагностика OBD-II');
      expect(summary).toContain('3 ошибки');
      expect(summary).toContain('87654321');
    });
  });

  describe('getHeatmapColor', () => {
    it('возвращает зелёный для низких значений', () => {
      const color = getHeatmapColor(100, 100, 200);
      expect(color).toBe('#10B981');
    });

    it('возвращает красный для высоких значений', () => {
      const color = getHeatmapColor(190, 100, 200);
      expect(color).toBe('#EF4444');
    });

    it('возвращает оранжевый для средних значений', () => {
      const color = getHeatmapColor(150, 100, 200);
      expect(color).toBe('#F59E0B');
    });
  });

  describe('determineZoneStatus', () => {
    const normalRange = { min: 100, max: 150 };

    it('возвращает ok для нормальных значений', () => {
      expect(determineZoneStatus(125, normalRange)).toBe('ok');
    });

    it('возвращает warning для небольших отклонений', () => {
      expect(determineZoneStatus(90, normalRange)).toBe('warning');
      expect(determineZoneStatus(160, normalRange)).toBe('warning');
    });

    it('возвращает critical для больших отклонений', () => {
      expect(determineZoneStatus(70, normalRange)).toBe('critical');
      expect(determineZoneStatus(200, normalRange)).toBe('critical');
    });
  });

  describe('generateRecommendation', () => {
    it('возвращает критическую рекомендацию при множественных отклонениях', () => {
      const rec = generateRecommendation(25, 6);
      expect(rec).toContain('множественные критические отклонения');
    });

    it('возвращает предупреждение при отдельных отклонениях', () => {
      const rec = generateRecommendation(10, 2);
      expect(rec).toContain('отклонения в отдельных зонах');
    });

    it('возвращает норму при отсутствии отклонений', () => {
      const rec = generateRecommendation(5, 0);
      expect(rec).toContain('в норме');
    });
  });

  describe('generateDiagnosticsRecommendation', () => {
    it('возвращает критическую рекомендацию при критических ошибках', () => {
      const rec = generateDiagnosticsRecommendation(2, 1);
      expect(rec).toContain('критические ошибки');
      expect(rec).toContain('немедленное');
    });

    it('возвращает рекомендацию при множественных предупреждениях', () => {
      const rec = generateDiagnosticsRecommendation(0, 5);
      expect(rec).toContain('множественные предупреждения');
    });

    it('возвращает норму при отсутствии ошибок', () => {
      const rec = generateDiagnosticsRecommendation(0, 0);
      expect(rec).toContain('в норме');
    });
  });
});
