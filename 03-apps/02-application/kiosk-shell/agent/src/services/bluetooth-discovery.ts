/**
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart
 * Original language: Dart
 * Integration date: 2025-11-30
 * Purpose: Bluetooth device discovery and connection management for OBD adapters
 *
 * Key patterns adapted:
 * - Device filtering by name (OBDII, Ediag, Ediag Plus)
 * - Connection management with reconnection logic
 * - Baud rate detection (38400, 115200)
 */

import { EventEmitter } from 'events';
import { SerialPort } from 'serialport';
import { ReadlineParser } from '@serialport/parser-readline';

/**
 * Extended port info interface for platforms that provide friendlyName
 * Based on serialport PortInfo type with optional extended properties
 */
interface ExtendedPortInfo {
  path: string;
  manufacturer?: string;
  serialNumber?: string;
  pnpId?: string;
  locationId?: string;
  productId?: string;
  vendorId?: string;
  friendlyName?: string;
}

/**
 * Supported OBD device name patterns (case-insensitive)
 * From: DONORS/1-begaz-OBDII/NOTES.md
 */
export const OBD_DEVICE_PATTERNS = ['obdii', 'ediag', 'ediag plus'];

/**
 * Baud rates to try when connecting to OBD adapter
 * From: DONORS/2-PowerBroker2-ELMduino/NOTES.md
 */
export const BAUD_RATES = [38400, 115200] as const;

/**
 * Default timeout for connection operations (ms)
 */
export const DEFAULT_CONNECTION_TIMEOUT = 5000;

/**
 * Represents a discovered OBD device
 */
export interface ObdDevice {
  /** Device name */
  name: string;
  /** Device address (COM port path or MAC address) */
  address: string;
  /** Signal strength (if available) */
  rssi?: number;
  /** Whether device is paired */
  isPaired?: boolean;
}

/**
 * Connection configuration
 */
export interface ConnectionConfig {
  /** Timeout for connection attempts (ms) */
  timeout?: number;
  /** Baud rate to use (auto-detect if not specified) */
  baudRate?: number;
  /** Auto-reconnect on disconnect */
  autoReconnect?: boolean;
  /** Reconnect delay (ms) */
  reconnectDelay?: number;
}

/**
 * Connection state
 */
export type ConnectionState =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting';

/**
 * BluetoothDiscoveryService - handles OBD adapter discovery and connection
 *
 * Adapted from begaz/OBDII Dart implementation:
 * - getNearbyDevices -> scanForDevices
 * - getConnection -> connectToDevice
 * - Device filtering logic
 */
export class BluetoothDiscoveryService extends EventEmitter {
  private port: SerialPort | null = null;
  private parser: ReadlineParser | null = null;
  private connectionState: ConnectionState = 'disconnected';
  private currentDevice: ObdDevice | null = null;
  private currentBaudRate: number | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private config: Required<ConnectionConfig>;

  constructor(config?: ConnectionConfig) {
    super();
    this.config = {
      timeout: config?.timeout ?? DEFAULT_CONNECTION_TIMEOUT,
      baudRate: config?.baudRate ?? 0, // 0 = auto-detect
      autoReconnect: config?.autoReconnect ?? true,
      reconnectDelay: config?.reconnectDelay ?? 2000,
    };
  }

  /**
   * Scan for available serial ports and filter OBD devices
   * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - getNearbyDevices
   *
   * @returns List of discovered OBD devices
   */
  async scanForDevices(): Promise<ObdDevice[]> {
    const ports = await SerialPort.list() as ExtendedPortInfo[];
    const devices: ObdDevice[] = [];

    for (const port of ports) {
      // Use friendlyName (Windows) or manufacturer as device name
      const name = port.friendlyName ?? port.manufacturer ?? port.path;

      // Filter by OBD device patterns (case-insensitive)
      // From: DONORS/1-begaz-OBDII/NOTES.md - device name filtering
      if (this.isObdDevice(name)) {
        devices.push({
          name: name,
          address: port.path,
          isPaired: true, // Serial ports are already "paired"
        });
      }
    }

    this.emit('devicesFound', devices);
    return devices;
  }

  /**
   * Check if device name matches OBD adapter patterns
   * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - device filtering
   *
   * @param name - Device name to check
   * @returns True if device matches OBD patterns
   */
  isObdDevice(name: string): boolean {
    const lowerName = name.toLowerCase();
    return OBD_DEVICE_PATTERNS.some((pattern) => lowerName.includes(pattern));
  }

  /**
   * Connect to an OBD device
   * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - getConnection
   *
   * @param device - Device to connect to
   * @returns True if connection successful
   */
  async connectToDevice(device: ObdDevice): Promise<boolean> {
    if (this.connectionState === 'connected') {
      await this.disconnect();
    }

    this.connectionState = 'connecting';
    this.currentDevice = device;
    this.emit('stateChange', this.connectionState);

    try {
      // Determine baud rate to use
      const baudRates = this.config.baudRate
        ? [this.config.baudRate]
        : [...BAUD_RATES];

      // Try each baud rate until successful
      // From: DONORS/2-PowerBroker2-ELMduino/NOTES.md - baud rate detection
      for (const baudRate of baudRates) {
        const success = await this.tryConnect(device.address, baudRate);
        if (success) {
          this.currentBaudRate = baudRate;
          this.connectionState = 'connected';
          this.emit('stateChange', this.connectionState);
          this.emit('connected', { device, baudRate });
          return true;
        }
      }

      throw new Error('Failed to connect with any baud rate');
    } catch (error) {
      this.connectionState = 'disconnected';
      this.emit('stateChange', this.connectionState);
      this.emit('error', error);

      if (this.config.autoReconnect) {
        this.scheduleReconnect();
      }

      return false;
    }
  }

  /**
   * Try to connect with a specific baud rate
   *
   * @param address - Serial port address
   * @param baudRate - Baud rate to try
   * @returns True if connection successful
   */
  private async tryConnect(address: string, baudRate: number): Promise<boolean> {
    return new Promise((resolve) => {
      const port = new SerialPort(
        {
          path: address,
          baudRate: baudRate,
          autoOpen: false,
        },
        (err) => {
          if (err) {
            resolve(false);
          }
        }
      );

      const timeout = setTimeout(() => {
        port.close();
        resolve(false);
      }, this.config.timeout);

      port.open((err) => {
        clearTimeout(timeout);

        if (err) {
          resolve(false);
          return;
        }

        // Setup port handlers
        this.port = port;
        this.parser = port.pipe(new ReadlineParser({ delimiter: '\r' }));

        port.on('error', (error) => {
          console.error('[BLUETOOTH] Serial port error:', error.message);
          this.emit('error', error);
        });

        port.on('close', () => {
          console.log('[BLUETOOTH] Serial port closed');
          this.handleDisconnect();
        });

        this.parser.on('data', (data: string) => {
          this.emit('data', data.trim());
        });

        console.log(
          `[BLUETOOTH] Connected to ${address} at ${baudRate} baud`
        );
        resolve(true);
      });
    });
  }

  /**
   * Disconnect from current device
   */
  async disconnect(): Promise<void> {
    this.cancelReconnect();

    if (this.port && this.port.isOpen) {
      await new Promise<void>((resolve) => {
        this.port!.close((err) => {
          if (err) {
            console.error('[BLUETOOTH] Error closing port:', err.message);
          }
          resolve();
        });
      });
    }

    this.port = null;
    this.parser = null;
    this.currentDevice = null;
    this.currentBaudRate = null;
    this.connectionState = 'disconnected';
    this.emit('stateChange', this.connectionState);
    this.emit('disconnected');
    console.log('[BLUETOOTH] Disconnected');
  }

  /**
   * Handle unexpected disconnect
   */
  private handleDisconnect(): void {
    this.connectionState = 'disconnected';
    this.emit('stateChange', this.connectionState);
    this.emit('disconnected');

    if (this.config.autoReconnect && this.currentDevice) {
      this.scheduleReconnect();
    }
  }

  /**
   * Schedule a reconnection attempt
   * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart - reconnection logic
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      return;
    }

    console.log(
      `[BLUETOOTH] Scheduling reconnect in ${this.config.reconnectDelay}ms`
    );
    this.connectionState = 'reconnecting';
    this.emit('stateChange', this.connectionState);

    this.reconnectTimer = setTimeout(async () => {
      this.reconnectTimer = null;

      if (this.currentDevice) {
        try {
          await this.connectToDevice(this.currentDevice);
        } catch (error) {
          console.error('[BLUETOOTH] Reconnect failed:', error);
        }
      }
    }, this.config.reconnectDelay);
  }

  /**
   * Cancel pending reconnection
   */
  private cancelReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * Write data to the connected device
   *
   * @param data - Data to write
   * @returns Promise that resolves when write is complete
   */
  async write(data: string): Promise<void> {
    if (!this.port || !this.port.isOpen) {
      throw new Error('Not connected to device');
    }

    return new Promise((resolve, reject) => {
      this.port!.write(data, (err) => {
        if (err) {
          reject(new Error(`Failed to write: ${err.message}`));
        } else {
          resolve();
        }
      });
    });
  }

  /**
   * Get current connection state
   */
  getState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return (
      this.connectionState === 'connected' &&
      this.port !== null &&
      this.port.isOpen
    );
  }

  /**
   * Get current device
   */
  getCurrentDevice(): ObdDevice | null {
    return this.currentDevice;
  }

  /**
   * Get current baud rate
   */
  getCurrentBaudRate(): number | null {
    return this.currentBaudRate;
  }
}
