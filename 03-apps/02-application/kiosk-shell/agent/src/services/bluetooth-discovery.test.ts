/**
 * Unit tests for BluetoothDiscoveryService
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart patterns
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { EventEmitter } from 'events';

// Mock SerialPort
type CallbackFn = (err: Error | null) => void;
type WriteCallbackFn = (err: Error | null, bytesWritten?: number) => void;

const mockPorts = [
  { path: 'COM3', friendlyName: 'OBDII Bluetooth Device' },
  { path: 'COM4', friendlyName: 'Ediag Plus' },
  { path: 'COM5', friendlyName: 'USB Serial Device' },
];

const mockSerialPort = {
  isOpen: true,
  open: jest.fn((callback: CallbackFn) => callback(null)),
  close: jest.fn((callback: CallbackFn) => callback(null)),
  write: jest.fn((data: string, callback: WriteCallbackFn) => callback(null)),
  pipe: jest.fn(),
  on: jest.fn(),
};

const mockParser = new EventEmitter();

const MockSerialPortClass = jest.fn(() => mockSerialPort) as unknown as {
  new (...args: unknown[]): typeof mockSerialPort;
  list: jest.Mock;
};
MockSerialPortClass.list = jest.fn(() => Promise.resolve(mockPorts));

jest.mock('serialport', () => ({
  SerialPort: MockSerialPortClass,
}));

jest.mock('@serialport/parser-readline', () => ({
  ReadlineParser: jest.fn(() => mockParser),
}));

import {
  BluetoothDiscoveryService,
  OBD_DEVICE_PATTERNS,
  BAUD_RATES,
  type ObdDevice,
} from './bluetooth-discovery.js';

describe('BluetoothDiscoveryService', () => {
  let service: BluetoothDiscoveryService;

  beforeEach(() => {
    jest.clearAllMocks();
    mockParser.removeAllListeners();
    mockSerialPort.isOpen = true;
    mockSerialPort.pipe.mockReturnValue(mockParser);
    service = new BluetoothDiscoveryService();
  });

  afterEach(async () => {
    if (service.isConnected()) {
      await service.disconnect();
    }
  });

  describe('constants', () => {
    it('should have correct OBD device patterns', () => {
      expect(OBD_DEVICE_PATTERNS).toContain('obdii');
      expect(OBD_DEVICE_PATTERNS).toContain('ediag');
      expect(OBD_DEVICE_PATTERNS).toContain('ediag plus');
    });

    it('should have correct baud rates', () => {
      expect(BAUD_RATES).toContain(38400);
      expect(BAUD_RATES).toContain(115200);
    });
  });

  describe('isObdDevice', () => {
    it('should identify OBDII devices', () => {
      expect(service.isObdDevice('OBDII Bluetooth')).toBe(true);
      expect(service.isObdDevice('obdii adapter')).toBe(true);
      expect(service.isObdDevice('OBDII')).toBe(true);
    });

    it('should identify Ediag devices', () => {
      expect(service.isObdDevice('Ediag')).toBe(true);
      expect(service.isObdDevice('ediag')).toBe(true);
      expect(service.isObdDevice('Ediag Plus')).toBe(true);
      expect(service.isObdDevice('EDIAG PLUS')).toBe(true);
    });

    it('should reject non-OBD devices', () => {
      expect(service.isObdDevice('USB Serial Device')).toBe(false);
      expect(service.isObdDevice('Bluetooth Headphones')).toBe(false);
      expect(service.isObdDevice('COM Port')).toBe(false);
    });
  });

  describe('scanForDevices', () => {
    it('should return filtered OBD devices', async () => {
      const devices = await service.scanForDevices();

      expect(devices).toHaveLength(2);
      expect(devices[0].name).toBe('OBDII Bluetooth Device');
      expect(devices[1].name).toBe('Ediag Plus');
    });

    it('should emit devicesFound event', async () => {
      const spy = jest.fn();
      service.on('devicesFound', spy);

      await service.scanForDevices();

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(expect.arrayContaining([
        expect.objectContaining({ name: 'OBDII Bluetooth Device' }),
      ]));
    });

    it('should include address from port path', async () => {
      const devices = await service.scanForDevices();

      expect(devices[0].address).toBe('COM3');
      expect(devices[1].address).toBe('COM4');
    });
  });

  describe('connectToDevice', () => {
    const testDevice: ObdDevice = {
      name: 'OBDII Bluetooth Device',
      address: 'COM3',
    };

    it('should connect to device successfully', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      const result = await service.connectToDevice(testDevice);

      expect(result).toBe(true);
      expect(service.isConnected()).toBe(true);
    });

    it('should emit connected event with device and baud rate', async () => {
      const spy = jest.fn();
      service.on('connected', spy);
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice(testDevice);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          device: testDevice,
          baudRate: expect.any(Number),
        })
      );
    });

    it('should emit stateChange events', async () => {
      const spy = jest.fn();
      service.on('stateChange', spy);
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice(testDevice);

      expect(spy).toHaveBeenCalledWith('connecting');
      expect(spy).toHaveBeenCalledWith('connected');
    });

    it('should store current device', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice(testDevice);

      expect(service.getCurrentDevice()).toEqual(testDevice);
    });

    it('should store current baud rate', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice(testDevice);

      const baudRate = service.getCurrentBaudRate();
      expect(baudRate).toBeDefined();
      expect(BAUD_RATES).toContain(baudRate);
    });
  });

  describe('disconnect', () => {
    const testDevice: ObdDevice = {
      name: 'OBDII Bluetooth Device',
      address: 'COM3',
    };

    it('should disconnect from device', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));
      await service.connectToDevice(testDevice);

      await service.disconnect();

      expect(service.isConnected()).toBe(false);
    });

    it('should emit disconnected event', async () => {
      const spy = jest.fn();
      service.on('disconnected', spy);
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice(testDevice);
      await service.disconnect();

      expect(spy).toHaveBeenCalled();
    });

    it('should clear current device', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));
      await service.connectToDevice(testDevice);

      await service.disconnect();

      expect(service.getCurrentDevice()).toBeNull();
      expect(service.getCurrentBaudRate()).toBeNull();
    });
  });

  describe('getState', () => {
    it('should return disconnected initially', () => {
      expect(service.getState()).toBe('disconnected');
    });

    it('should return connected after successful connection', async () => {
      mockSerialPort.open = jest.fn((callback: CallbackFn) => callback(null));

      await service.connectToDevice({
        name: 'OBDII',
        address: 'COM3',
      });

      expect(service.getState()).toBe('connected');
    });
  });
});
