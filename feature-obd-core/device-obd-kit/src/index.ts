/**
 * @kiosk/device-obd
 * 
 * OBD-II device driver for ELM327 adapters.
 * Provides interfaces for connecting and reading diagnostic data from vehicles.
 */

export interface DtcCode {
  /** DTC code (e.g., P0300, B1234) */
  code: string;
  /** Description of the fault */
  description: string;
  /** Severity level */
  severity: 'information' | 'warning' | 'critical';
  /** Whether the MIL (Check Engine) is on */
  milStatus?: boolean;
}

export interface PidValue {
  /** PID identifier */
  pid: string;
  /** Parsed value */
  value: number;
  /** Unit of measurement */
  unit: string;
  /** Human-readable name */
  name: string;
  /** Timestamp of reading */
  timestamp: Date;
}

export interface ObdDevice {
  /** Connect to the OBD-II adapter */
  connect(): Promise<void>;
  /** Disconnect from the adapter */
  disconnect(): Promise<void>;
  /** Check if connected */
  isConnected(): boolean;
  /** Initialize ELM327 adapter */
  initialize(): Promise<void>;
  /** Read diagnostic trouble codes */
  readDtc(): Promise<DtcCode[]>;
  /** Clear diagnostic trouble codes */
  clearDtc(): Promise<void>;
  /** Read a specific PID value */
  readPid(pid: string): Promise<PidValue>;
}

/**
 * Mock implementation of ObdDevice for development and testing.
 * Simulates ELM327 adapter behavior without actual hardware.
 */
class MockObdDevice implements ObdDevice {
  private connected = false;
  private initialized = false;

  async connect(): Promise<void> {
    this.connected = true;
  }

  async disconnect(): Promise<void> {
    this.connected = false;
    this.initialized = false;
  }

  isConnected(): boolean {
    return this.connected;
  }

  async initialize(): Promise<void> {
    if (!this.connected) {
      throw new Error('Device not connected');
    }
    this.initialized = true;
  }

  async readDtc(): Promise<DtcCode[]> {
    if (!this.initialized) {
      throw new Error('Device not initialized');
    }
    // Return mock DTCs for testing
    return [
      { code: 'P0300', description: 'Random/Multiple Cylinder Misfire Detected', severity: 'warning' },
    ];
  }

  async clearDtc(): Promise<void> {
    if (!this.initialized) {
      throw new Error('Device not initialized');
    }
    // Mock clear operation
  }

  async readPid(pid: string): Promise<PidValue> {
    if (!this.initialized) {
      throw new Error('Device not initialized');
    }
    // Return mock PID value
    return {
      pid,
      value: Math.floor(Math.random() * 100),
      unit: '%',
      name: `PID ${pid}`,
      timestamp: new Date(),
    };
  }
}

/**
 * Factory function for creating OBD device instances.
 * Returns mock implementation for development; real adapter integration pending.
 */
export function createObdDevice(): ObdDevice {
  return new MockObdDevice();
}
