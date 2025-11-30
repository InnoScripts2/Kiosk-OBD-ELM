/**
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart
 * Original language: C++ / Dart
 * Integration date: 2025-11-30
 * Purpose: DTC (Diagnostic Trouble Code) scanning and clearing
 *
 * Key patterns adapted:
 * - currentDTCCodes() from ELMduino.cpp
 * - resetDTC() from ELMduino.cpp
 * - _getDtcsFrom() and DTC parsing from obd2_plugin.dart
 */

import { EventEmitter } from 'events';
import type { WriteFunction } from './obd-protocol.js';

/**
 * DTC code prefixes
 * From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - currentDTCCodes
 */
export const DTC_PREFIXES = {
  P: 'Powertrain',
  C: 'Chassis',
  B: 'Body',
  U: 'Network',
} as const;

/**
 * DTC severity levels
 */
export enum DtcSeverity {
  INFO = 'info',
  WARNING = 'warning',
  CRITICAL = 'critical',
}

/**
 * Parsed DTC code
 */
export interface DtcCode {
  /** Full DTC code (e.g., P0300) */
  code: string;
  /** DTC prefix (P, C, B, U) */
  prefix: 'P' | 'C' | 'B' | 'U';
  /** Category name */
  category: string;
  /** Numeric part of code */
  numericCode: string;
  /** Human-readable description (if available) */
  description?: string;
  /** Severity level */
  severity?: DtcSeverity;
}

/**
 * DTC scan result
 */
export interface DtcScanResult {
  /** List of found DTC codes */
  codes: DtcCode[];
  /** Total number of codes found */
  count: number;
  /** Whether MIL (Check Engine) light is on */
  milOn: boolean;
  /** Raw response from adapter */
  rawResponse: string;
  /** Timestamp of scan */
  timestamp: Date;
}

/**
 * Scanner configuration
 */
export interface DtcScannerConfig {
  /** Command timeout in ms */
  timeout?: number;
  /** Debug mode */
  debug?: boolean;
}

/**
 * DtcScannerService - handles DTC reading and clearing
 *
 * Adapted from:
 * - ELMduino currentDTCCodes() for reading DTCs
 * - ELMduino resetDTC() for clearing DTCs
 * - obd2_plugin.dart _getDtcsFrom() for parsing logic
 */
export class DtcScannerService extends EventEmitter {
  private write: WriteFunction | null = null;
  private responseBuffer = '';
  private config: Required<DtcScannerConfig>;
  private pendingCommand: {
    resolve: (result: string) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
  } | null = null;

  constructor(config?: DtcScannerConfig) {
    super();
    this.config = {
      timeout: config?.timeout ?? 5000,
      debug: config?.debug ?? false,
    };
  }

  /**
   * Set the write function for sending data
   */
  setWriteFunction(write: WriteFunction): void {
    this.write = write;
  }

  /**
   * Handle incoming data from the adapter
   */
  handleData(data: string): void {
    if (this.pendingCommand) {
      this.responseBuffer += data;

      if (this.responseBuffer.includes('>')) {
        const response = this.cleanResponse(this.responseBuffer);
        this.responseBuffer = '';

        const { resolve, timeout } = this.pendingCommand;
        clearTimeout(timeout);
        this.pendingCommand = null;

        resolve(response);
      }
    }
  }

  /**
   * Send a command and wait for response
   */
  private async sendCommand(command: string): Promise<string> {
    if (!this.write) {
      throw new Error('Write function not set');
    }

    return new Promise((resolve, reject) => {
      this.responseBuffer = '';

      const timeout = setTimeout(() => {
        this.pendingCommand = null;
        reject(new Error(`Command ${command} timed out`));
      }, this.config.timeout);

      this.pendingCommand = { resolve, reject, timeout };

      this.write!(`${command}\r`).catch((err) => {
        clearTimeout(timeout);
        this.pendingCommand = null;
        reject(err);
      });
    });
  }

  /**
   * Read current (stored) DTC codes
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - currentDTCCodes
   *
   * OBD Service Mode 03: Request stored DTCs
   *
   * @returns DTC scan result
   */
  async getCurrentDtc(): Promise<DtcScanResult> {
    const response = await this.sendCommand('03');

    if (this.config.debug) {
      console.log('[DTC] Current DTC response:', response);
    }

    return this.parseDtcResponse(response, '43');
  }

  /**
   * Read pending DTC codes
   * OBD Service Mode 07: Request pending DTCs
   *
   * @returns DTC scan result
   */
  async getPendingDtc(): Promise<DtcScanResult> {
    const response = await this.sendCommand('07');

    if (this.config.debug) {
      console.log('[DTC] Pending DTC response:', response);
    }

    return this.parseDtcResponse(response, '47');
  }

  /**
   * Clear all DTC codes
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - resetDTC
   *
   * OBD Service Mode 04: Clear DTCs and freeze frame
   *
   * @returns True if clear was successful
   */
  async clearDtc(): Promise<boolean> {
    const response = await this.sendCommand('04');

    if (this.config.debug) {
      console.log('[DTC] Clear DTC response:', response);
    }

    // Success response is "44"
    const success = response.includes('44');

    this.emit('dtcCleared', { success, timestamp: new Date() });

    return success;
  }

  /**
   * Get monitor status (including MIL status and DTC count)
   * OBD Service Mode 01, PID 01
   *
   * @returns Monitor status including MIL and DTC count
   */
  async getMonitorStatus(): Promise<{
    milOn: boolean;
    dtcCount: number;
    rawResponse: string;
  }> {
    const response = await this.sendCommand('0101');

    if (this.config.debug) {
      console.log('[DTC] Monitor status response:', response);
    }

    // Response format: 41 01 XX YY ZZ AA
    // XX: bit 7 = MIL, bits 0-6 = DTC count
    const headerIndex = response.indexOf('4101');
    if (headerIndex === -1) {
      return { milOn: false, dtcCount: 0, rawResponse: response };
    }

    const dataStart = headerIndex + 4;
    const dataHex = response.substring(dataStart, dataStart + 2);
    const statusByte = parseInt(dataHex, 16);

    const milOn = (statusByte & 0x80) !== 0;
    const dtcCount = statusByte & 0x7f;

    return { milOn, dtcCount, rawResponse: response };
  }

  /**
   * Parse DTC response
   * Adapted from:
   * - DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - currentDTCCodes parsing
   * - DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - _getDtcsFrom, _initialDataOne, _initialDataTwo
   *
   * @param response - Raw response string
   * @param expectedHeader - Expected response header (43 for stored, 47 for pending)
   * @returns Parsed DTC scan result
   */
  private parseDtcResponse(
    response: string,
    expectedHeader: string
  ): DtcScanResult {
    const codes: DtcCode[] = [];
    const cleanedResponse = response.replace(/\s/g, '');

    // Check for valid response
    if (!cleanedResponse.includes(expectedHeader)) {
      return {
        codes: [],
        count: 0,
        milOn: false,
        rawResponse: response,
        timestamp: new Date(),
      };
    }

    // Find all occurrences of the header
    let searchIndex = 0;
    let headerIndex: number;
    while ((headerIndex = cleanedResponse.indexOf(expectedHeader, searchIndex)) !== -1) {

      // Each DTC is 2 bytes (4 hex chars) after the header
      // Response format: 43 [DTC1_HIGH] [DTC1_LOW] [DTC2_HIGH] [DTC2_LOW] ...
      // Each group of 8 chars after header = 43 + 2 DTCs
      const dtcDataStart = headerIndex + 2;

      // Parse DTCs in pairs
      for (let i = dtcDataStart; i + 3 < cleanedResponse.length; i += 4) {
        const byte1Hex = cleanedResponse.substring(i, i + 2);
        const byte2Hex = cleanedResponse.substring(i + 2, i + 4);

        // Stop at next header or end
        if (byte1Hex === expectedHeader.substring(0, 2)) break;

        const byte1 = parseInt(byte1Hex, 16);
        const byte2 = parseInt(byte2Hex, 16);

        // Skip empty codes (0000)
        if (byte1 === 0 && byte2 === 0) continue;

        const dtcCode = this.parseDtcBytes(byte1, byte2);
        if (dtcCode && !codes.some((c) => c.code === dtcCode.code)) {
          codes.push(dtcCode);
        }
      }

      searchIndex = headerIndex + 2;
    }

    this.emit('dtcScanned', { codes, count: codes.length, timestamp: new Date() });

    return {
      codes,
      count: codes.length,
      milOn: codes.length > 0,
      rawResponse: response,
      timestamp: new Date(),
    };
  }

  /**
   * Parse DTC bytes into code
   * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - _initialDataOne, _initialDataTwo, _initialDTC
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - currentDTCCodes switch statement
   *
   * DTC format:
   * - First 2 bits of byte1: Type (00=P, 01=C, 10=B, 11=U)
   * - Next 2 bits of byte1: First digit (0-3)
   * - Last 4 bits of byte1: Second digit (0-F)
   * - byte2: Third and fourth digits
   *
   * @param byte1 - First byte of DTC
   * @param byte2 - Second byte of DTC
   * @returns Parsed DTC code or null
   */
  private parseDtcBytes(byte1: number, byte2: number): DtcCode | null {
    // First 2 bits determine prefix
    // From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp lines 3124-3190
    const typeCode = (byte1 >> 6) & 0x03;
    const firstDigit = (byte1 >> 4) & 0x03;

    let prefix: 'P' | 'C' | 'B' | 'U';
    switch (typeCode) {
      case 0:
        prefix = 'P';
        break;
      case 1:
        prefix = 'C';
        break;
      case 2:
        prefix = 'B';
        break;
      case 3:
        prefix = 'U';
        break;
      default:
        return null;
    }

    // Build numeric code
    const secondDigit = byte1 & 0x0f;
    const thirdDigit = (byte2 >> 4) & 0x0f;
    const fourthDigit = byte2 & 0x0f;

    const numericCode = `${firstDigit}${secondDigit.toString(16).toUpperCase()}${thirdDigit.toString(16).toUpperCase()}${fourthDigit.toString(16).toUpperCase()}`;
    const code = `${prefix}${numericCode}`;

    // Skip invalid codes
    if (code === 'P0000') return null;

    return {
      code,
      prefix,
      category: DTC_PREFIXES[prefix],
      numericCode,
      severity: this.estimateSeverity(prefix, parseInt(numericCode, 16)),
    };
  }

  /**
   * Estimate DTC severity based on prefix and code
   */
  private estimateSeverity(prefix: string, numericCode: number): DtcSeverity {
    // Critical codes typically indicate emissions or safety issues
    // P0xxx and P1xxx are often more critical
    if (prefix === 'P' && numericCode < 0x1000) {
      return DtcSeverity.CRITICAL;
    }

    // Body and chassis codes are usually warnings
    if (prefix === 'B' || prefix === 'C') {
      return DtcSeverity.WARNING;
    }

    // Network codes are typically informational
    if (prefix === 'U') {
      return DtcSeverity.INFO;
    }

    return DtcSeverity.WARNING;
  }

  /**
   * Clean response string
   */
  private cleanResponse(response: string): string {
    return response
      .replace(/[\r\n]/g, '')
      .replace(/>/g, '')
      .replace(/SEARCHING\.\.\./gi, '')
      .trim();
  }

  /**
   * Get DTC description (placeholder for future database lookup)
   * TODO: Integrate with DTC database
   */
  getDtcDescription(code: string): string | undefined {
    // Common DTC descriptions
    const descriptions: Record<string, string> = {
      P0300: 'Random/Multiple Cylinder Misfire Detected',
      P0301: 'Cylinder 1 Misfire Detected',
      P0302: 'Cylinder 2 Misfire Detected',
      P0303: 'Cylinder 3 Misfire Detected',
      P0304: 'Cylinder 4 Misfire Detected',
      P0420: 'Catalyst System Efficiency Below Threshold (Bank 1)',
      P0171: 'System Too Lean (Bank 1)',
      P0172: 'System Too Rich (Bank 1)',
      P0133: 'O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)',
      P0401: 'Exhaust Gas Recirculation Flow Insufficient',
      P0440: 'Evaporative Emission Control System Malfunction',
      P0442: 'Evaporative Emission Control System Leak Detected (Small Leak)',
      P0455: 'Evaporative Emission Control System Leak Detected (Gross Leak)',
      P0500: 'Vehicle Speed Sensor Malfunction',
      P0700: 'Transmission Control System Malfunction',
    };

    return descriptions[code];
  }
}
