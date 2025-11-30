/**
 * Unit tests for ArduinoAdapter
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { EventEmitter } from 'events';

// Mock SerialPort
type CallbackFn = (err: Error | null) => void;
type WriteCallbackFn = (err: Error | null, bytesWritten?: number) => void;

const mockSerialPort = {
  isOpen: true,
  open: jest.fn((callback: CallbackFn) => callback(null)),
  close: jest.fn((callback: CallbackFn) => callback(null)),
  write: jest.fn((data: string, callback: WriteCallbackFn) => callback(null)),
  pipe: jest.fn(() => mockParser),
  on: jest.fn(),
};

const mockParser = new EventEmitter();

jest.mock('serialport', () => ({
  SerialPort: jest.fn(() => mockSerialPort),
}));

jest.mock('@serialport/parser-readline', () => ({
  ReadlineParser: jest.fn(() => mockParser),
}));

import { ArduinoAdapter, type ArduinoConfig } from './ArduinoAdapter.js';

describe('ArduinoAdapter', () => {
  let adapter: ArduinoAdapter;
  let config: ArduinoConfig;

  beforeEach(() => {
    jest.clearAllMocks();
    mockParser.removeAllListeners();
    
    config = {
      port: '/dev/ttyUSB0',
      baudRate: 9600,
      commandTimeout: 5000,
      reconnectDelay: 2000,
      heartbeatInterval: 30000,
    };

    adapter = new ArduinoAdapter(config);
  });

  afterEach(async () => {
    if (adapter.isConnected()) {
      await adapter.disconnect();
    }
  });

  describe('connect', () => {
    it('should connect to Arduino and verify with PING', async () => {
      const connectPromise = adapter.connect();

      // Simulate READY message
      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 100);

      // Simulate PONG response
      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 2100);

      await connectPromise;

      expect(adapter.isConnected()).toBe(true);
    });

    it('should emit connected event', async () => {
      const connectedSpy = jest.fn();
      adapter.on('connected', connectedSpy);

      const connectPromise = adapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 100);

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 2100);

      await connectPromise;

      expect(connectedSpy).toHaveBeenCalled();
    });

    it('should emit ready event when Arduino reports READY', async () => {
      const readySpy = jest.fn();
      adapter.on('ready', readySpy);

      const connectPromise = adapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 50);

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 100);

      await connectPromise;

      expect(readySpy).toHaveBeenCalledTimes(1);
    });

    it('should still connect if READY is missed (timeout fallback)', async () => {
      const connectPromise = adapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 2500);

      await connectPromise;

      expect(adapter.isConnected()).toBe(true);
    }, 10000);
  });

  describe('sendCommand', () => {
    beforeEach(async () => {
      const connectPromise = adapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 100);

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 2100);

      await connectPromise;
    });

    it('should send PING and receive PONG', async () => {
      const commandPromise = adapter.sendCommand('PING');

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 100);

      const response = await commandPromise;

      expect(response.type).toBe('PONG');
      expect(response.raw).toBe('PONG');
    });

    it('should send OPEN_THICKNESS and receive OK response', async () => {
      const commandPromise = adapter.sendCommand('OPEN_THICKNESS');

      setTimeout(() => {
        mockParser.emit('data', 'OK:OPENED:THICKNESS');
      }, 100);

      const response = await commandPromise;

      expect(response.type).toBe('OK');
      expect(response.action).toBe('OPENED');
      expect(response.device).toBe('THICKNESS');
    });

    it('should handle ERROR responses', async () => {
      const commandPromise = adapter.sendCommand('OPEN_THICKNESS');

      setTimeout(() => {
        mockParser.emit('data', 'ERROR:TIMEOUT:THICKNESS');
      }, 100);

      await expect(commandPromise).rejects.toThrow('TIMEOUT');
    });

    it('should timeout if no response received', async () => {
      const shortTimeoutConfig = { ...config, commandTimeout: 100 };
      const shortAdapter = new ArduinoAdapter(shortTimeoutConfig);

      const connectPromise = shortAdapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 10);

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 60);

      await connectPromise;

      const commandPromise = shortAdapter.sendCommand('PING');
      // Don't send any response

      await expect(commandPromise).rejects.toThrow('timed out');

      await shortAdapter.disconnect();
    }, 10000);

    it('should send STATUS and parse response', async () => {
      const commandPromise = adapter.sendCommand('STATUS');

      setTimeout(() => {
        mockParser.emit('data', 'STATUS:THICKNESS=CLOSED,OBD=OPEN');
      }, 100);

      const response = await commandPromise;

      expect(response.type).toBe('STATUS');
      expect(response.status).toEqual({
        thickness: 'CLOSED',
        obd: 'OPEN',
      });
    });
  });

  describe('parseResponse', () => {
    it('should parse LOG messages', () => {
      const adapter = new ArduinoAdapter(config);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const response = (adapter as any).parseResponse('[LOG] 12345 OPEN_START THICKNESS');

      expect(response).toMatchObject({ type: 'LOG' });
    });

    it('should parse PONG', () => {
      const adapter = new ArduinoAdapter(config);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const response = (adapter as any).parseResponse('PONG');

      expect(response).toMatchObject({ type: 'PONG' });
    });

    it('should parse STATUS response', () => {
      const adapter = new ArduinoAdapter(config);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const response = (adapter as any).parseResponse('STATUS:THICKNESS=CLOSED,OBD=CLOSED');

      expect(response).toMatchObject({
        type: 'STATUS',
        status: {
          thickness: 'CLOSED',
          obd: 'CLOSED',
        },
      });
    });

    it('should parse OK responses', () => {
      const adapter = new ArduinoAdapter(config);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const response = (adapter as any).parseResponse('OK:OPENED:THICKNESS');

      expect(response).toMatchObject({
        type: 'OK',
        action: 'OPENED',
        device: 'THICKNESS',
      });
    });

    it('should parse ERROR responses', () => {
      const adapter = new ArduinoAdapter(config);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const response = (adapter as any).parseResponse('ERROR:TIMEOUT:OBD');

      expect(response).toMatchObject({
        type: 'ERROR',
        error: 'TIMEOUT',
        device: 'OBD',
      });
    });
  });

  describe('disconnect', () => {
    it('should close the serial port', async () => {
      const connectPromise = adapter.connect();

      setTimeout(() => {
        mockParser.emit('data', 'READY:DISPENCER:v1.0');
      }, 100);

      setTimeout(() => {
        mockParser.emit('data', 'PONG');
      }, 2100);

      await connectPromise;

      await adapter.disconnect();

      expect(adapter.isConnected()).toBe(false);
    });
  });
});
