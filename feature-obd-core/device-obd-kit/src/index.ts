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
 * Factory function for creating OBD device instances.
 * Implementation pending Bluetooth/Serial adapter integration.
 */
export function createObdDevice(): ObdDevice {
  throw new Error('ObdDevice not implemented. Requires adapter integration.');
}
