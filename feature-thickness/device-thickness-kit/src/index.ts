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
 * Mock implementation of ThicknessDevice for development and testing.
 * Simulates BLE device behavior without actual hardware.
 */
class MockThicknessDevice implements ThicknessDevice {
  private connected = false;

  async connect(): Promise<void> {
    this.connected = true;
  }

  async disconnect(): Promise<void> {
    this.connected = false;
  }

  isConnected(): boolean {
    return this.connected;
  }

  async readMeasurement(): Promise<ThicknessMeasurement> {
    if (!this.connected) {
      throw new Error('Device not connected');
    }
    // Return mock measurement (production would read from BLE)
    return {
      value: Math.floor(Math.random() * 200) + 50, // 50-250 micrometers
      timestamp: new Date(),
      status: 'measured',
    };
  }
}

/**
 * Factory function for creating thickness device instances.
 * Returns mock implementation for development; real BLE adapter integration pending.
 */
export function createThicknessDevice(): ThicknessDevice {
  return new MockThicknessDevice();
}
