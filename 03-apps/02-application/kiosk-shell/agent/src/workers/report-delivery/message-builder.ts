import type { ReportMetadata } from './config.js';

export function composeDiagnosticsEmailSubject(sessionId: string, metadata: ReportMetadata): string {
  const vehicleLabel = extractVehicleLabel(metadata) ?? 'Диагностика';
  return `${vehicleLabel} · отчёт ${sessionId}`;
}

export function composeThicknessEmailSubject(sessionId: string, metadata: ReportMetadata): string {
  const vehicleLabel = extractVehicleLabel(metadata) ?? 'Толщиномер ЛКП';
  return `${vehicleLabel} · результаты ${sessionId}`;
}

export function composeDiagnosticsSmsBody(sessionId: string, metadata: ReportMetadata): string {
  const summary = extractSummaryLine(metadata);
  if (summary) {
    return truncateSms(`${summary}. Сессия ${sessionId}.`);
  }
  return `Отчёт диагностики ${sessionId} готов. Проверьте email.`;
}

export function composeThicknessSmsBody(sessionId: string, metadata: ReportMetadata): string {
  const summary = extractSummaryLine(metadata);
  if (summary) {
    return truncateSms(`${summary}. Сессия ${sessionId}.`);
  }
  return `Результаты толщиномера ${sessionId} готовы. Проверьте email.`;
}

function extractVehicleLabel(metadata: ReportMetadata): string | undefined {
  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    return undefined;
  }
  const record = metadata as Record<string, unknown>;
  const vehicle = record.vehicle;
  if (vehicle && typeof vehicle === 'object' && !Array.isArray(vehicle)) {
    const vehicleRecord = vehicle as Record<string, unknown>;
    const brand = typeof vehicleRecord.brand === 'string' ? vehicleRecord.brand : undefined;
    const model = typeof vehicleRecord.model === 'string' ? vehicleRecord.model : undefined;
    if (brand && model) {
      return `${brand} ${model}`.trim();
    }
    if (brand) {
      return brand;
    }
    if (model) {
      return model;
    }
  }
  const vehicleType = record.vehicle_type;
  if (typeof vehicleType === 'string' && vehicleType.trim()) {
    return vehicleType.trim();
  }
  return undefined;
}

function extractSummaryLine(metadata: ReportMetadata): string | undefined {
  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    return undefined;
  }
  const record = metadata as Record<string, unknown>;
  const summary = record.summary;
  if (typeof summary === 'string') {
    return summary.trim() || undefined;
  }
  if (summary && typeof summary === 'object' && !Array.isArray(summary)) {
    const summaryRecord = summary as Record<string, unknown>;
    const headline = typeof summaryRecord.headline === 'string' ? summaryRecord.headline.trim() : undefined;
    const status = typeof summaryRecord.status === 'string' ? summaryRecord.status.trim() : undefined;
    if (headline && status) {
      return `${headline} (${status})`;
    }
    return headline ?? status;
  }
  return undefined;
}

function truncateSms(value: string, maxLength = 160): string {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 1)}…`;
}
