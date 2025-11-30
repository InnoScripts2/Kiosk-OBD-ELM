/**
 * Unit tests for DtcScannerService
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp patterns
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart patterns
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import {
  DtcScannerService,
  DTC_PREFIXES,
  DtcSeverity,
} from './dtc-scanner.js';

describe('DtcScannerService', () => {
  let service: DtcScannerService;
  let mockWrite: jest.Mock<() => Promise<void>>;

  beforeEach(() => {
    jest.clearAllMocks();
    mockWrite = jest.fn(() => Promise.resolve());
    service = new DtcScannerService({ debug: false });
    service.setWriteFunction(mockWrite);
  });

  describe('constants', () => {
    it('should have correct DTC prefixes', () => {
      expect(DTC_PREFIXES.P).toBe('Powertrain');
      expect(DTC_PREFIXES.C).toBe('Chassis');
      expect(DTC_PREFIXES.B).toBe('Body');
      expect(DTC_PREFIXES.U).toBe('Network');
    });
  });

  describe('getCurrentDtc', () => {
    it('should send correct command for stored DTCs', async () => {
      const dtcPromise = service.getCurrentDtc();

      // Simulate response with no DTCs
      setTimeout(() => service.handleData('43>'), 50);

      await dtcPromise;

      expect(mockWrite).toHaveBeenCalledWith('03\r');
    });

    it('should parse single DTC code', async () => {
      const dtcPromise = service.getCurrentDtc();

      // Simulate response: 43 01 33 = P0133 (O2 Sensor)
      setTimeout(() => service.handleData('430133>'), 50);

      const result = await dtcPromise;

      expect(result.count).toBe(1);
      expect(result.codes[0].code).toBe('P0133');
      expect(result.codes[0].prefix).toBe('P');
      expect(result.codes[0].category).toBe('Powertrain');
    });

    it('should parse multiple DTC codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // Simulate response: 43 01 33 04 20 = P0133, P0420
      setTimeout(() => service.handleData('430133042043>'), 50);

      const result = await dtcPromise;

      expect(result.count).toBe(2);
      expect(result.codes[0].code).toBe('P0133');
      expect(result.codes[1].code).toBe('P0420');
    });

    it('should handle no DTCs', async () => {
      const dtcPromise = service.getCurrentDtc();

      // Simulate response with no DTCs
      setTimeout(() => service.handleData('4300000000>'), 50);

      const result = await dtcPromise;

      expect(result.count).toBe(0);
      expect(result.codes).toHaveLength(0);
    });

    it('should emit dtcScanned event', async () => {
      const spy = jest.fn();
      service.on('dtcScanned', spy);

      const dtcPromise = service.getCurrentDtc();
      setTimeout(() => service.handleData('430133>'), 50);

      await dtcPromise;

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          codes: expect.any(Array),
          count: expect.any(Number),
        })
      );
    });
  });

  describe('getPendingDtc', () => {
    it('should send correct command for pending DTCs', async () => {
      const dtcPromise = service.getPendingDtc();

      setTimeout(() => service.handleData('47>'), 50);

      await dtcPromise;

      expect(mockWrite).toHaveBeenCalledWith('07\r');
    });

    it('should parse pending DTCs with 47 header', async () => {
      const dtcPromise = service.getPendingDtc();

      // Simulate response: 47 01 33 = P0133
      setTimeout(() => service.handleData('470133>'), 50);

      const result = await dtcPromise;

      expect(result.count).toBe(1);
      expect(result.codes[0].code).toBe('P0133');
    });
  });

  describe('clearDtc', () => {
    it('should send correct command to clear DTCs', async () => {
      const clearPromise = service.clearDtc();

      setTimeout(() => service.handleData('44>'), 50);

      await clearPromise;

      expect(mockWrite).toHaveBeenCalledWith('04\r');
    });

    it('should return true on successful clear', async () => {
      const clearPromise = service.clearDtc();

      setTimeout(() => service.handleData('44>'), 50);

      const result = await clearPromise;

      expect(result).toBe(true);
    });

    it('should return false on failed clear', async () => {
      const clearPromise = service.clearDtc();

      setTimeout(() => service.handleData('ERROR>'), 50);

      const result = await clearPromise;

      expect(result).toBe(false);
    });

    it('should emit dtcCleared event', async () => {
      const spy = jest.fn();
      service.on('dtcCleared', spy);

      const clearPromise = service.clearDtc();
      setTimeout(() => service.handleData('44>'), 50);

      await clearPromise;

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          timestamp: expect.any(Date),
        })
      );
    });
  });

  describe('getMonitorStatus', () => {
    it('should send correct command for monitor status', async () => {
      const statusPromise = service.getMonitorStatus();

      setTimeout(() => service.handleData('4101820700>'), 50);

      await statusPromise;

      expect(mockWrite).toHaveBeenCalledWith('0101\r');
    });

    it('should parse MIL status correctly (MIL on)', async () => {
      const statusPromise = service.getMonitorStatus();

      // 0x82 = 10000010 binary, bit 7 = 1 (MIL on), bits 0-6 = 2 (DTC count)
      setTimeout(() => service.handleData('410182>'), 50);

      const result = await statusPromise;

      expect(result.milOn).toBe(true);
      expect(result.dtcCount).toBe(2);
    });

    it('should parse MIL status correctly (MIL off)', async () => {
      const statusPromise = service.getMonitorStatus();

      // 0x00 = MIL off, 0 DTCs
      setTimeout(() => service.handleData('410100>'), 50);

      const result = await statusPromise;

      expect(result.milOn).toBe(false);
      expect(result.dtcCount).toBe(0);
    });
  });

  describe('DTC code parsing', () => {
    it('should parse P (Powertrain) codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // P0300 = 03 00 (type 0, digit 0)
      setTimeout(() => service.handleData('430300>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].prefix).toBe('P');
      expect(result.codes[0].category).toBe('Powertrain');
    });

    it('should parse C (Chassis) codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // C0300 = 40 + 03 00 (type 1 = bits 01, first digit 0 = bits 00)
      // First byte: 01 00 0011 = 0x43 is wrong, we need type code 1 (01 binary)
      // Type code 1 (C) = 01 in top 2 bits = 0x40 + rest
      // For C0100: top 2 bits = 01, next 2 = 00, then 01 00 = 0x41 0x00
      setTimeout(() => service.handleData('434100>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].prefix).toBe('C');
      expect(result.codes[0].category).toBe('Chassis');
    });

    it('should parse B (Body) codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // B0100 = type 2 (10 binary) in top 2 bits = 0x80
      // For B0100: 10 00 0001 0000 0000 = 0x81 0x00
      setTimeout(() => service.handleData('438100>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].prefix).toBe('B');
      expect(result.codes[0].category).toBe('Body');
    });

    it('should parse U (Network) codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // U0100 = type 3 (11 binary) in top 2 bits = 0xC0
      // For U0100: 11 00 0001 0000 0000 = 0xC1 0x00
      setTimeout(() => service.handleData('43C100>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].prefix).toBe('U');
      expect(result.codes[0].category).toBe('Network');
    });

    it('should skip P0000 codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // P0000 should be skipped
      setTimeout(() => service.handleData('430000>'), 50);

      const result = await dtcPromise;

      expect(result.count).toBe(0);
    });
  });

  describe('getDtcDescription', () => {
    it('should return description for known codes', () => {
      expect(service.getDtcDescription('P0300')).toBe(
        'Random/Multiple Cylinder Misfire Detected'
      );
      expect(service.getDtcDescription('P0420')).toBe(
        'Catalyst System Efficiency Below Threshold (Bank 1)'
      );
    });

    it('should return undefined for unknown codes', () => {
      expect(service.getDtcDescription('P9999')).toBeUndefined();
    });
  });

  describe('severity estimation', () => {
    it('should estimate critical severity for powertrain codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // P0300 = 03 00
      setTimeout(() => service.handleData('430300>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].severity).toBe(DtcSeverity.CRITICAL);
    });

    it('should estimate warning severity for chassis codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // C0100 = 41 00 (type 1 in top 2 bits)
      setTimeout(() => service.handleData('434100>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].severity).toBe(DtcSeverity.WARNING);
    });

    it('should estimate info severity for network codes', async () => {
      const dtcPromise = service.getCurrentDtc();

      // U0100 = C1 00 (type 3 in top 2 bits)
      setTimeout(() => service.handleData('43C100>'), 50);

      const result = await dtcPromise;

      expect(result.codes[0].severity).toBe(DtcSeverity.INFO);
    });
  });
});
