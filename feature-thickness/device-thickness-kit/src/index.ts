/**
 * @kiosk/device-thickness
 * 
 * Thickness gauge device driver (BLE).
 * Provides interfaces for connecting and reading measurements from thickness gauges.
 */

export interface ThicknessMeasurement {
  /** Measurement value in micrometers */
  value: number;
  /** Timestamp of the measurement */
  timestamp: Date;
  /** Zone identifier on the car body */
  zone?: string;
  /** Measurement status */
  status: 'pending' | 'measured' | 'error';
}

export interface ThicknessDevice {
  /** Connect to the thickness gauge */
  connect(): Promise<void>;
  /** Disconnect from the device */
  disconnect(): Promise<void>;
  /** Check if device is connected */
  isConnected(): boolean;
  /** Read a single measurement */
  readMeasurement(): Promise<ThicknessMeasurement>;
}

/**
 * Factory function for creating thickness device instances.
 * Implementation pending BLE adapter integration.
 */
export function createThicknessDevice(): ThicknessDevice {
  throw new Error('ThicknessDevice not implemented. Requires BLE adapter.');
}
