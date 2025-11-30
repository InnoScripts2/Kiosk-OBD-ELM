/**
 * ArduinoAdapter - адаптер для управления Arduino через Serial
 * Обеспечивает связь между Node.js и Arduino dispencer
 */

import { SerialPort } from 'serialport';
import { ReadlineParser } from '@serialport/parser-readline';
import { EventEmitter } from 'events';

export interface ArduinoConfig {
  port: string;
  baudRate: number;
  commandTimeout: number;
  reconnectDelay: number;
  heartbeatInterval: number;
}

export type ArduinoCommand = 
  | 'OPEN_THICKNESS'
  | 'OPEN_OBD'
  | 'CLOSE_THICKNESS'
  | 'CLOSE_OBD'
  | 'STATUS'
  | 'PING';

export interface ArduinoResponse {
  type: 'OK' | 'ERROR' | 'STATUS' | 'PONG' | 'LOG';
  action?: string;
  device?: string;
  status?: {
    thickness: 'OPEN' | 'CLOSED';
    obd: 'OPEN' | 'CLOSED';
  };
  error?: string;
  raw: string;
}

/**
 * Адаптер для работы с Arduino через Serial порт
 */
export class ArduinoAdapter extends EventEmitter {
  private port: SerialPort | null = null;
  private parser: ReadlineParser | null = null;
  private config: ArduinoConfig;
  private connected = false;
  private ready = false;
  private pendingCommands: Map<string, {
    command: ArduinoCommand;
    resolve: (response: ArduinoResponse) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
  }> = new Map();
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private readonly initializationTimeoutMs = 2000;

  constructor(config: ArduinoConfig) {
    super();
    this.config = config;
  }

  /**
   * Подключиться к Arduino
   */
  async connect(): Promise<void> {
    if (this.connected) {
      return;
    }

    try {
      this.ready = false;
      this.port = new SerialPort({
        path: this.config.port,
        baudRate: this.config.baudRate,
        autoOpen: false,
      });

      this.parser = this.port.pipe(new ReadlineParser({ delimiter: '\n' }));

      // Обработка входящих данных
      this.parser.on('data', (line: string) => {
        this.handleResponse(line);
      });

      // Обработка ошибок порта
      this.port.on('error', (err) => {
        console.error('[ARDUINO] Serial port error:', err.message);
        this.emit('error', err);
      });

      // Обработка закрытия порта
      this.port.on('close', () => {
        console.log('[ARDUINO] Serial port closed');
        this.connected = false;
        this.emit('disconnected');
        this.scheduleReconnect();
      });

      // Открыть порт
      await new Promise<void>((resolve, reject) => {
        this.port!.open((err) => {
          if (err) {
            reject(err);
          } else {
            resolve();
          }
        });
      });

      this.connected = true;
      this.emit('connected');
      console.log(`[ARDUINO] Connected to ${this.config.port} at ${this.config.baudRate} baud`);

      // Запустить heartbeat
      this.startHeartbeat();

      // Ожидание инициализации Arduino (ожидаем READY или таймаут)
      await this.waitForReadySignal();

      // Проверка связи
      await this.sendCommand('PING');
      console.log('[ARDUINO] Connection verified with PING');

    } catch (error) {
      console.error('[ARDUINO] Failed to connect:', error);
      this.scheduleReconnect();
      throw error;
    }
  }

  /**
   * Отключиться от Arduino
   */
  async disconnect(): Promise<void> {
    this.stopHeartbeat();
    this.ready = false;
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.port && this.port.isOpen) {
      await new Promise<void>((resolve) => {
        this.port!.close((err) => {
          if (err) {
            console.error('[ARDUINO] Error closing port:', err.message);
          }
          resolve();
        });
      });
    }

    this.connected = false;
    this.port = null;
    this.parser = null;
    
    console.log('[ARDUINO] Disconnected');
  }

  /**
   * Отправить команду на Arduino
   */
  async sendCommand(command: ArduinoCommand): Promise<ArduinoResponse> {
    if (!this.connected || !this.port || !this.port.isOpen) {
      throw new Error('Arduino not connected');
    }

    return new Promise((resolve, reject) => {
      const commandId = `${command}_${Date.now()}`;
      
      // Таймаут команды
      const timeout = setTimeout(() => {
        this.pendingCommands.delete(commandId);
        reject(new Error(`Command ${command} timed out after ${this.config.commandTimeout}ms`));
      }, this.config.commandTimeout);

      // Сохранить обработчики
      this.pendingCommands.set(commandId, { command, resolve, reject, timeout });

      // Отправить команду
      const commandStr = `${command}\n`;
      this.port!.write(commandStr, (err) => {
        if (err) {
          clearTimeout(timeout);
          this.pendingCommands.delete(commandId);
          reject(new Error(`Failed to send command: ${err.message}`));
        } else {
          console.log(`[ARDUINO] Sent: ${command}`);
        }
      });
    });
  }

  /**
   * Обработка ответа от Arduino
   */
  private handleResponse(line: string): void {
    line = line.trim();
    if (!line) {
      return;
    }

    console.log(`[ARDUINO] Received: ${line}`);

    const response = this.parseResponse(line);
    this.emit('response', response);

    if (response.action === 'READY') {
      this.ready = true;
      this.emit('ready');
    }

    // Найти и выполнить pending команду
    if (response.type !== 'LOG') {
      // Найдём первую pending команду соответствующего типа
      for (const [commandId, handlers] of this.pendingCommands.entries()) {
        const command = handlers.command;
        
        // Проверяем соответствие команды и ответа
        let matches = false;
        if (command === 'PING' && response.type === 'PONG') {
          matches = true;
        } else if (command === 'STATUS' && response.type === 'STATUS') {
          matches = true;
        } else if (command.startsWith('OPEN_') || command.startsWith('CLOSE_')) {
          const device = command.split('_')[1]; // THICKNESS или OBD
          if (response.device === device) {
            matches = true;
          }
        }

        if (matches) {
          clearTimeout(handlers.timeout);
          this.pendingCommands.delete(commandId);
          
          if (response.type === 'ERROR') {
            handlers.reject(new Error(response.error || response.raw));
          } else {
            handlers.resolve(response);
          }
          break;
        }
      }
    }
  }

  /**
   * Парсинг ответа от Arduino
   */
  private parseResponse(line: string): ArduinoResponse {
    const parts = line.split(':');

    // LOG messages
    if (line.startsWith('[LOG]')) {
      return {
        type: 'LOG',
        raw: line,
      };
    }

    // PONG
    if (line === 'PONG') {
      return {
        type: 'PONG',
        raw: line,
      };
    }

    // STATUS response: STATUS:THICKNESS=CLOSED,OBD=CLOSED
    if (parts[0] === 'STATUS') {
      const statusParts = parts[1].split(',');
      const thickness = statusParts[0].split('=')[1] as 'OPEN' | 'CLOSED';
      const obd = statusParts[1].split('=')[1] as 'OPEN' | 'CLOSED';
      
      return {
        type: 'STATUS',
        status: { thickness, obd },
        raw: line,
      };
    }

    // OK responses: OK:OPENED:THICKNESS
    if (parts[0] === 'OK') {
      return {
        type: 'OK',
        action: parts[1],
        device: parts[2],
        raw: line,
      };
    }

    // ERROR responses: ERROR:TIMEOUT:THICKNESS
    if (parts[0] === 'ERROR') {
      return {
        type: 'ERROR',
        error: parts[1],
        device: parts[2],
        raw: line,
      };
    }

    // READY message
    if (line.startsWith('READY:')) {
      return {
        type: 'OK',
        action: 'READY',
        raw: line,
      };
    }

    // Unknown format
    console.warn(`[ARDUINO] Unknown response format: ${line}`);
    return {
      type: 'OK',
      raw: line,
    };
  }

  /**
   * Запустить heartbeat (периодические PING)
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();
    
    this.heartbeatTimer = setInterval(async () => {
      try {
        await this.sendCommand('PING');
      } catch (error) {
        console.error('[ARDUINO] Heartbeat failed:', error);
        // Не прерываем соединение, просто логируем
      }
    }, this.config.heartbeatInterval);
  }

  /**
   * Остановить heartbeat
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * Запланировать переподключение
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      return;
    }

    console.log(`[ARDUINO] Scheduling reconnect in ${this.config.reconnectDelay}ms`);
    
    this.reconnectTimer = setTimeout(async () => {
      this.reconnectTimer = null;
      this.ready = false;
      try {
        await this.connect();
      } catch (error) {
        console.error('[ARDUINO] Reconnect failed:', error);
        // scheduleReconnect будет вызван из обработчика ошибки connect()
      }
    }, this.config.reconnectDelay);
  }

  /**
   * Проверка состояния подключения
   */
  isConnected(): boolean {
    return this.connected && this.port !== null && this.port.isOpen;
  }

  /**
   * Ожидаем READY-сообщение от Arduino с запасным таймаутом
   */
  private waitForReadySignal(timeoutMs = this.initializationTimeoutMs): Promise<void> {
    if (this.ready) {
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      let timeout: NodeJS.Timeout | null = null;

      const cleanup = (): void => {
        if (timeout) {
          clearTimeout(timeout);
          timeout = null;
        }
        this.off('ready', onReady);
      };

      const onReady = (): void => {
        cleanup();
        this.ready = true;
        resolve();
      };

      timeout = setTimeout(() => {
        cleanup();
        resolve();
      }, timeoutMs);

      this.on('ready', onReady);
    });
  }
}
