/**
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp
 * Original language: C++
 * Integration date: 2025-11-30
 * Purpose: OBD-II protocol communication and PID queries
 *
 * Key patterns adapted:
 * - AT command initialization sequence
 * - PID query formatting and response parsing
 * - Error code handling (ELM_SUCCESS, ELM_TIMEOUT, etc.)
 * - conditionResponse() and processPID() logic
 */

import { EventEmitter } from 'events';

/**
 * Protocol IDs
 * From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.h
 */
export const PROTOCOL_IDS = {
  AUTOMATIC: '0',
  SAE_J1850_PWM_41_KBAUD: '1',
  SAE_J1850_PWM_10_KBAUD: '2',
  ISO_9141_5_BAUD_INIT: '3',
  ISO_14230_5_BAUD_INIT: '4',
  ISO_14230_FAST_INIT: '5',
  ISO_15765_11_BIT_500_KBAUD: '6',
  ISO_15765_29_BIT_500_KBAUD: '7',
  ISO_15765_11_BIT_250_KBAUD: '8',
  ISO_15765_29_BIT_250_KBAUD: '9',
  SAE_J1939_29_BIT_250_KBAUD: 'A',
} as const;

/**
 * OBD service modes
 * From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.h
 */
export const SERVICE_MODES = {
  CURRENT_DATA: 0x01,
  FREEZE_FRAME: 0x02,
  STORED_DTC: 0x03,
  CLEAR_DTC: 0x04,
  PENDING_DTC: 0x07,
  VEHICLE_INFO: 0x09,
} as const;

/**
 * Common PIDs
 * From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.h
 */
export const PIDS = {
  SUPPORTED_PIDS_1_20: 0x00,
  ENGINE_LOAD: 0x04,
  ENGINE_COOLANT_TEMP: 0x05,
  FUEL_PRESSURE: 0x0a,
  INTAKE_MANIFOLD_PRESSURE: 0x0b,
  ENGINE_RPM: 0x0c,
  VEHICLE_SPEED: 0x0d,
  TIMING_ADVANCE: 0x0e,
  INTAKE_AIR_TEMP: 0x0f,
  MAF_FLOW_RATE: 0x10,
  THROTTLE_POSITION: 0x11,
  FUEL_TANK_LEVEL: 0x2f,
  AMBIENT_AIR_TEMP: 0x46,
  ENGINE_OIL_TEMP: 0x5c,
  FUEL_INJECTION_TIMING: 0x5d,
  ENGINE_FUEL_RATE: 0x5e,
} as const;

/**
 * OBD error codes
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.h
 */
export enum ObdErrorCode {
  SUCCESS = 0,
  NO_RESPONSE = 1,
  BUFFER_OVERFLOW = 2,
  GARBAGE = 3,
  UNABLE_TO_CONNECT = 4,
  NO_DATA = 5,
  STOPPED = 6,
  TIMEOUT = 7,
  GETTING_MSG = 8,
  MSG_RECEIVED = 9,
  GENERAL_ERROR = -1,
}

/**
 * Protocol state machine
 * From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.h - obd_cmd_states
 */
export enum ProtocolState {
  IDLE = 'idle',
  INITIALIZING = 'initializing',
  READY = 'ready',
  QUERYING = 'querying',
  ERROR = 'error',
}

/**
 * PID query result
 */
export interface PidResult {
  service: number;
  pid: number;
  rawValue: number;
  calculatedValue: number;
  responseBytes: number[];
  rawResponse: string;
}

/**
 * Protocol configuration
 */
export interface ProtocolConfig {
  /** Protocol ID (default: AUTOMATIC) */
  protocol?: string;
  /** Command timeout in ms */
  timeout?: number;
  /** Debug mode */
  debug?: boolean;
}

/**
 * Write function type - provided by BluetoothDiscoveryService
 */
export type WriteFunction = (data: string) => Promise<void>;

/**
 * ObdProtocolService - handles OBD-II protocol communication
 *
 * Adapted from PowerBroker2/ELMduino C++ implementation:
 * - initializeELM -> initialize
 * - processPID -> queryPid
 * - conditionResponse -> parseResponse
 */
export class ObdProtocolService extends EventEmitter {
  private state: ProtocolState = ProtocolState.IDLE;
  private write: WriteFunction | null = null;
  private responseBuffer = '';
  private pendingQuery: {
    resolve: (result: PidResult) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
    service: number;
    pid: number;
  } | null = null;
  private config: Required<ProtocolConfig>;
  private connected = false;

  constructor(config?: ProtocolConfig) {
    super();
    this.config = {
      protocol: config?.protocol ?? PROTOCOL_IDS.AUTOMATIC,
      timeout: config?.timeout ?? 5000,
      debug: config?.debug ?? false,
    };
  }

  /**
   * Set the write function for sending data
   * This should be provided by BluetoothDiscoveryService
   */
  setWriteFunction(write: WriteFunction): void {
    this.write = write;
  }

  /**
   * Initialize the ELM327 adapter
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - initializeELM
   *
   * Sequence:
   * 1. ATZ - Reset adapter
   * 2. ATE0 - Echo off
   * 3. ATL0 - Line feed off
   * 4. ATS0 - Spaces off
   * 5. ATSP0 - Auto-detect protocol
   *
   * @param onProgress - Optional progress callback
   * @returns True if initialization successful
   */
  async initialize(onProgress?: (stage: string) => void): Promise<boolean> {
    if (!this.write) {
      throw new Error('Write function not set. Call setWriteFunction first.');
    }

    this.state = ProtocolState.INITIALIZING;
    this.emit('stateChange', this.state);

    try {
      // Reset adapter
      onProgress?.('Resetting adapter...');
      await this.sendCommandBlocking('ATZ');
      await this.delay(1000); // ATZ needs extra time

      // Echo off
      onProgress?.('Configuring adapter...');
      await this.sendCommandBlocking('ATE0');
      await this.delay(100);

      // Line feed off
      await this.sendCommandBlocking('ATL0');
      await this.delay(100);

      // Spaces off
      await this.sendCommandBlocking('ATS0');
      await this.delay(100);

      // Set protocol (auto-detect by default)
      onProgress?.('Detecting protocol...');
      const protocolCommand = `ATSP${this.config.protocol}`;
      await this.sendCommandBlocking(protocolCommand);
      await this.delay(100);

      // Verify connection with supported PIDs query
      onProgress?.('Verifying connection...');
      const result = await this.sendCommandBlocking('0100');

      // Check for valid OBD-II response: should start with '41 00' (response to mode 01, PID 00)
      // or contain 'OK' for AT commands acknowledgment
      const cleanResult = result.replace(/\s/g, '').toUpperCase();
      if (cleanResult.startsWith('4100') || result.toUpperCase().includes('OK')) {
        this.state = ProtocolState.READY;
        this.connected = true;
        this.emit('stateChange', this.state);
        onProgress?.('Ready!');
        return true;
      }

      throw new Error(`Protocol initialization failed: ${result}`);
    } catch (error) {
      this.state = ProtocolState.ERROR;
      this.emit('stateChange', this.state);
      this.emit('error', error);
      return false;
    }
  }

  /**
   * Send a command and wait for response (blocking)
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - sendCommand_Blocking
   *
   * @param command - AT or OBD command
   * @returns Response string
   */
  async sendCommandBlocking(command: string): Promise<string> {
    if (!this.write) {
      throw new Error('Write function not set');
    }

    return new Promise((resolve, reject) => {
      this.responseBuffer = '';

      const timeout = setTimeout(() => {
        reject(new Error(`Command ${command} timed out`));
      }, this.config.timeout);

      // Response handler
      const handleResponse = (data: string): void => {
        this.responseBuffer += data;

        // Look for end of response marker '>'
        if (this.responseBuffer.includes('>')) {
          clearTimeout(timeout);
          this.off('rawData', handleResponse);

          // Clean up response
          const response = this.cleanResponse(this.responseBuffer);
          this.responseBuffer = '';

          if (this.config.debug) {
            console.log(`[OBD] Command: ${command}, Response: ${response}`);
          }

          resolve(response);
        }
      };

      this.on('rawData', handleResponse);

      // Send command with carriage return
      this.write!(`${command}\r`).catch((err) => {
        clearTimeout(timeout);
        this.off('rawData', handleResponse);
        reject(err);
      });
    });
  }

  /**
   * Handle incoming data from the adapter
   * Call this when data is received from BluetoothDiscoveryService
   */
  handleData(data: string): void {
    this.emit('rawData', data);

    // Handle pending PID query
    if (this.pendingQuery) {
      this.responseBuffer += data;

      if (this.responseBuffer.includes('>')) {
        const response = this.cleanResponse(this.responseBuffer);
        this.responseBuffer = '';

        const { resolve, reject, timeout, service, pid } = this.pendingQuery;
        clearTimeout(timeout);
        this.pendingQuery = null;
        this.state = ProtocolState.READY;
        this.emit('stateChange', this.state);

        try {
          const result = this.parseResponse(response, service, pid);
          resolve(result);
        } catch (parseError) {
          // If parsing fails, reject with the parse error
          reject(parseError instanceof Error ? parseError : new Error(String(parseError)));
        }
      }
    }
  }

  /**
   * Query a PID
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - processPID
   *
   * @param service - Service mode (e.g., 0x01 for current data)
   * @param pid - Parameter ID
   * @param numResponses - Expected number of response lines
   * @returns PID result
   */
  async queryPid(
    service: number,
    pid: number,
    numResponses = 1
  ): Promise<PidResult> {
    if (!this.write) {
      throw new Error('Write function not set');
    }

    if (this.state !== ProtocolState.READY) {
      throw new Error(`Cannot query PID: adapter in ${this.state} state`);
    }

    this.state = ProtocolState.QUERYING;
    this.emit('stateChange', this.state);

    // Format query string
    // From: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - formatQueryArray
    const serviceHex = service.toString(16).padStart(2, '0').toUpperCase();
    const pidHex = pid.toString(16).padStart(2, '0').toUpperCase();
    const query =
      numResponses > 1
        ? `${serviceHex}${pidHex}${numResponses}`
        : `${serviceHex}${pidHex}`;

    return new Promise((resolve, reject) => {
      this.responseBuffer = '';

      const timeout = setTimeout(() => {
        this.pendingQuery = null;
        this.state = ProtocolState.READY;
        this.emit('stateChange', this.state);
        reject(new Error(`PID query ${query} timed out`));
      }, this.config.timeout);

      this.pendingQuery = { resolve, reject, timeout, service, pid };

      this.write!(`${query}\r`).catch((err) => {
        clearTimeout(timeout);
        this.pendingQuery = null;
        this.state = ProtocolState.READY;
        this.emit('stateChange', this.state);
        reject(err);
      });
    });
  }

  /**
   * Parse PID response
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - conditionResponse
   *
   * @param response - Raw response string
   * @param service - Service mode
   * @param pid - Parameter ID
   * @returns Parsed PID result
   */
  private parseResponse(
    response: string,
    service: number,
    pid: number
  ): PidResult {
    // Expected header format: [service+40][pid]
    const expectedHeader =
      (service + 0x40).toString(16).toUpperCase() +
      pid.toString(16).padStart(2, '0').toUpperCase();

    // Find response header
    const headerIndex = response.indexOf(expectedHeader);
    if (headerIndex === -1) {
      throw new Error(`Invalid response: header ${expectedHeader} not found`);
    }

    // Extract data bytes after header
    const dataStart = headerIndex + expectedHeader.length;
    const dataHex = response.substring(dataStart).replace(/[^0-9A-Fa-f]/g, '');

    // Parse bytes
    const responseBytes: number[] = [];
    for (let i = 0; i < dataHex.length; i += 2) {
      const byteHex = dataHex.substring(i, i + 2);
      if (byteHex.length === 2) {
        responseBytes.push(parseInt(byteHex, 16));
      }
    }

    // Calculate raw value (combine bytes)
    let rawValue = 0;
    for (let i = 0; i < responseBytes.length; i++) {
      rawValue = (rawValue << 8) | responseBytes[i];
    }

    // Apply PID-specific calculation
    const calculatedValue = this.calculatePidValue(pid, responseBytes);

    return {
      service,
      pid,
      rawValue,
      calculatedValue,
      responseBytes,
      rawResponse: response,
    };
  }

  /**
   * Calculate PID value based on formula
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - calculator functions
   */
  private calculatePidValue(pid: number, bytes: number[]): number {
    const A = bytes[0] ?? 0;
    const B = bytes[1] ?? 0;

    switch (pid) {
      // Engine RPM: ((A*256)+B)/4
      case PIDS.ENGINE_RPM:
        return ((A * 256) + B) / 4;

      // Vehicle Speed: A (km/h)
      case PIDS.VEHICLE_SPEED:
        return A;

      // Engine Coolant Temp: A-40 (°C)
      case PIDS.ENGINE_COOLANT_TEMP:
        return A - 40;

      // Engine Load: (A*100)/255 (%)
      case PIDS.ENGINE_LOAD:
        return (A * 100) / 255;

      // Throttle Position: (A*100)/255 (%)
      case PIDS.THROTTLE_POSITION:
        return (A * 100) / 255;

      // Fuel Tank Level: (A*100)/255 (%)
      case PIDS.FUEL_TANK_LEVEL:
        return (A * 100) / 255;

      // Intake Air Temp: A-40 (°C)
      case PIDS.INTAKE_AIR_TEMP:
        return A - 40;

      // Ambient Air Temp: A-40 (°C)
      case PIDS.AMBIENT_AIR_TEMP:
        return A - 40;

      // Engine Oil Temp: A-40 (°C)
      case PIDS.ENGINE_OIL_TEMP:
        return A - 40;

      // Fuel Pressure: A*3 (kPa)
      case PIDS.FUEL_PRESSURE:
        return A * 3;

      // Intake Manifold Pressure: A (kPa)
      case PIDS.INTAKE_MANIFOLD_PRESSURE:
        return A;

      // MAF Flow Rate: ((A*256)+B)/100 (g/s)
      case PIDS.MAF_FLOW_RATE:
        return ((A * 256) + B) / 100;

      // Timing Advance: (A/2)-64 (°)
      case PIDS.TIMING_ADVANCE:
        return A / 2 - 64;

      // Engine Fuel Rate: ((A*256)+B)/20 (L/h)
      case PIDS.ENGINE_FUEL_RATE:
        return ((A * 256) + B) / 20;

      // Fuel Injection Timing: ((A*256)+B)/128 - 210 (°)
      case PIDS.FUEL_INJECTION_TIMING:
        return ((A * 256) + B) / 128 - 210;

      // Default: return raw combined value
      default:
        return (A * 256) + B;
    }
  }

  /**
   * Clean response string
   * Removes whitespace, control characters, and prompt
   */
  private cleanResponse(response: string): string {
    return response
      .replace(/[\r\n]/g, '')
      .replace(/>/g, '')
      .replace(/SEARCHING\.\.\./gi, '')
      .trim();
  }

  /**
   * Deduplicate double responses from Ediag adapters
   * From: DONORS/INTEGRATION_GUIDE.md - deduplicateResponse
   */
  deduplicateResponse(responses: string[]): string {
    if (responses.length === 2 && responses[0] === responses[1]) {
      return responses[0];
    }
    return responses[0] ?? '';
  }

  /**
   * Utility delay function
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // Convenience methods for common PIDs

  /**
   * Get engine RPM
   */
  async getRpm(): Promise<number> {
    const result = await this.queryPid(SERVICE_MODES.CURRENT_DATA, PIDS.ENGINE_RPM);
    return result.calculatedValue;
  }

  /**
   * Get vehicle speed (km/h)
   */
  async getSpeed(): Promise<number> {
    const result = await this.queryPid(SERVICE_MODES.CURRENT_DATA, PIDS.VEHICLE_SPEED);
    return result.calculatedValue;
  }

  /**
   * Get engine coolant temperature (°C)
   */
  async getEngineCoolantTemp(): Promise<number> {
    const result = await this.queryPid(
      SERVICE_MODES.CURRENT_DATA,
      PIDS.ENGINE_COOLANT_TEMP
    );
    return result.calculatedValue;
  }

  /**
   * Get engine load (%)
   */
  async getEngineLoad(): Promise<number> {
    const result = await this.queryPid(SERVICE_MODES.CURRENT_DATA, PIDS.ENGINE_LOAD);
    return result.calculatedValue;
  }

  /**
   * Get throttle position (%)
   */
  async getThrottlePosition(): Promise<number> {
    const result = await this.queryPid(
      SERVICE_MODES.CURRENT_DATA,
      PIDS.THROTTLE_POSITION
    );
    return result.calculatedValue;
  }

  /**
   * Get fuel tank level (%)
   */
  async getFuelLevel(): Promise<number> {
    const result = await this.queryPid(SERVICE_MODES.CURRENT_DATA, PIDS.FUEL_TANK_LEVEL);
    return result.calculatedValue;
  }

  /**
   * Get current protocol state
   */
  getState(): ProtocolState {
    return this.state;
  }

  /**
   * Check if protocol is ready
   */
  isReady(): boolean {
    return this.state === ProtocolState.READY;
  }

  /**
   * Check if connected to vehicle
   */
  isConnected(): boolean {
    return this.connected;
  }
}
