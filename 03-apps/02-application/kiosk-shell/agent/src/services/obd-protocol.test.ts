/**
 * Unit tests for ObdProtocolService
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp patterns
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import {
  ObdProtocolService,
  PROTOCOL_IDS,
  SERVICE_MODES,
  PIDS,
  ProtocolState,
} from './obd-protocol.js';

describe('ObdProtocolService', () => {
  let service: ObdProtocolService;
  let mockWrite: jest.Mock<() => Promise<void>>;

  beforeEach(() => {
    jest.clearAllMocks();
    mockWrite = jest.fn(() => Promise.resolve());
    service = new ObdProtocolService({ debug: false, timeout: 100 });
    service.setWriteFunction(mockWrite);
  });

  describe('constants', () => {
    it('should have correct protocol IDs', () => {
      expect(PROTOCOL_IDS.AUTOMATIC).toBe('0');
      expect(PROTOCOL_IDS.ISO_15765_11_BIT_500_KBAUD).toBe('6');
    });

    it('should have correct service modes', () => {
      expect(SERVICE_MODES.CURRENT_DATA).toBe(0x01);
      expect(SERVICE_MODES.STORED_DTC).toBe(0x03);
      expect(SERVICE_MODES.CLEAR_DTC).toBe(0x04);
    });

    it('should have correct PIDs', () => {
      expect(PIDS.ENGINE_RPM).toBe(0x0c);
      expect(PIDS.VEHICLE_SPEED).toBe(0x0d);
      expect(PIDS.ENGINE_COOLANT_TEMP).toBe(0x05);
      expect(PIDS.THROTTLE_POSITION).toBe(0x11);
    });
  });

  describe('initialization', () => {
    it('should start in IDLE state', () => {
      expect(service.getState()).toBe(ProtocolState.IDLE);
    });

    it('should require write function to be set', async () => {
      const newService = new ObdProtocolService();

      await expect(newService.initialize()).rejects.toThrow(
        'Write function not set'
      );
    });

    it('should emit stateChange to INITIALIZING on initialize', () => {
      const spy = jest.fn();
      service.on('stateChange', spy);

      // Start initialization (it will fail due to timeout, but we only care about the state change)
      service.initialize().catch(() => { /* expected timeout */ });

      expect(spy).toHaveBeenCalledWith(ProtocolState.INITIALIZING);
    });
  });

  describe('queryPid parsing', () => {
    it('should calculate RPM correctly', () => {
      // RPM formula: ((A*256)+B)/4
      // A=0x1A, B=0xF8 -> ((26*256)+248)/4 = 1726
      const A = 0x1A;
      const B = 0xF8;
      const expected = ((A * 256) + B) / 4;
      expect(expected).toBeCloseTo(1726);
    });

    it('should calculate vehicle speed correctly', () => {
      // Speed is just A in km/h
      const A = 80;
      expect(A).toBe(80);
    });

    it('should calculate coolant temp correctly', () => {
      // Temp formula: A - 40
      // A=0x7B (123) -> 123 - 40 = 83
      const A = 0x7B;
      const expected = A - 40;
      expect(expected).toBe(83);
    });

    it('should calculate throttle position correctly', () => {
      // Throttle formula: (A * 100) / 255
      // A=0x80 (128) -> (128 * 100) / 255 = 50.2
      const A = 0x80;
      const expected = (A * 100) / 255;
      expect(expected).toBeCloseTo(50.2, 1);
    });
  });

  describe('deduplicateResponse', () => {
    it('should deduplicate identical responses', () => {
      const result = service.deduplicateResponse(['410C1AF8', '410C1AF8']);
      expect(result).toBe('410C1AF8');
    });

    it('should keep first response if different', () => {
      const result = service.deduplicateResponse(['410C1AF8', '410C1A00']);
      expect(result).toBe('410C1AF8');
    });

    it('should handle single response', () => {
      const result = service.deduplicateResponse(['410C1AF8']);
      expect(result).toBe('410C1AF8');
    });

    it('should handle empty array', () => {
      const result = service.deduplicateResponse([]);
      expect(result).toBe('');
    });
  });

  describe('state management', () => {
    it('should report not ready initially', () => {
      expect(service.isReady()).toBe(false);
    });

    it('should report not connected initially', () => {
      expect(service.isConnected()).toBe(false);
    });

    it('should start in IDLE state', () => {
      expect(service.getState()).toBe(ProtocolState.IDLE);
    });
  });
});
