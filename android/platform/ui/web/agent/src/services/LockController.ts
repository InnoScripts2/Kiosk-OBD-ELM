/**
 * LockController - управление электронными замками для выдачи устройств
 * Управляет толщиномером и OBD-адаптером через Arduino
 */

import { ArduinoAdapter, type ArduinoConfig, type ArduinoCommand, type ArduinoResponse } from './ArduinoAdapter.js';
import * as fs from 'fs/promises';
import * as path from 'path';

export type DeviceType = 'thickness' | 'adapter';

export interface LockStatus {
  thickness: 'open' | 'closed';
  adapter: 'open' | 'closed';
  error?: string;
  connected: boolean;
}

export interface LockControllerConfig {
  arduino?: ArduinoConfig;
  mockMode?: boolean;
  logDir?: string;
}

/**
 * Класс для управления замками устройств
 */
export class LockController {
  private status: LockStatus = {
    thickness: 'closed',
    adapter: 'closed',
    connected: false,
  };
  
  private arduino: ArduinoAdapter | null = null;
  private mockMode: boolean;
  private logDir: string;
  private retryCount = 3;
  private retryDelay = 1000;

  constructor(config: LockControllerConfig = {}) {
    this.mockMode = config.mockMode ?? (process.env.NODE_ENV !== 'production');
    this.logDir = config.logDir ?? 'logs/sessions';

    if (!this.mockMode && config.arduino) {
      this.arduino = new ArduinoAdapter(config.arduino);
      this.setupArduinoHandlers();
    }
  }

  /**
   * Настроить обработчики событий Arduino
   */
  private setupArduinoHandlers(): void {
    if (!this.arduino) {
      return;
    }

    this.arduino.on('connected', () => {
      console.log('[LOCK] Arduino connected');
      this.status.connected = true;
      this.status.error = undefined;
    });

    this.arduino.on('disconnected', () => {
      console.log('[LOCK] Arduino disconnected');
      this.status.connected = false;
      this.status.error = 'Arduino disconnected';
    });

    this.arduino.on('error', (error: Error) => {
      console.error('[LOCK] Arduino error:', error.message);
      this.status.error = error.message;
    });

    this.arduino.on('response', (response) => {
      // Обновление статуса на основе ответов Arduino
      if (response.type === 'STATUS' && response.status) {
        this.status.thickness = response.status.thickness === 'OPEN' ? 'open' : 'closed';
        this.status.adapter = response.status.obd === 'OPEN' ? 'open' : 'closed';
      }
    });
  }

  /**
   * Инициализация контроллера
   */
  async initialize(): Promise<void> {
    if (this.mockMode) {
      console.log('[LOCK] Running in mock mode (no Arduino connection)');
      this.status.connected = true;
      return;
    }

    if (this.arduino) {
      try {
        await this.arduino.connect();
        
        // Запросить текущий статус замков
        const statusResponse = await this.arduino.sendCommand('STATUS');
        if (statusResponse.status) {
          this.status.thickness = statusResponse.status.thickness === 'OPEN' ? 'open' : 'closed';
          this.status.adapter = statusResponse.status.obd === 'OPEN' ? 'open' : 'closed';
        }
        
        console.log('[LOCK] Controller initialized, status:', this.status);
      } catch (error) {
        console.error('[LOCK] Failed to initialize:', error);
        this.status.error = error instanceof Error ? error.message : 'Initialization failed';
        throw error;
      }
    }
  }

  /**
   * Открыть слот для устройства
   */
  async openSlot(deviceType: DeviceType): Promise<void> {
    console.log(`[LOCK] Opening slot for ${deviceType}`);
    
    const command: ArduinoCommand = deviceType === 'thickness' 
      ? 'OPEN_THICKNESS' 
      : 'OPEN_OBD';

    try {
      if (this.mockMode) {
        // Симуляция задержки открытия
        await new Promise(resolve => setTimeout(resolve, 500));
        this.status[deviceType] = 'open';
        await this.logAction('open', deviceType, true);
      } else {
        // Реальное управление через Arduino
        const response = await this.sendCommandWithRetry(command);
        
        if (response.type === 'OK' && response.action?.includes('OPEN')) {
          this.status[deviceType] = 'open';
          await this.logAction('open', deviceType, true);
        } else {
          throw new Error(`Unexpected response: ${response.raw}`);
        }
      }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[LOCK] Failed to open ${deviceType}:`, errorMsg);
      this.status.error = errorMsg;
      await this.logAction('open', deviceType, false, errorMsg);
      throw error;
    }
  }

  /**
   * Закрыть слот устройства
   */
  async closeSlot(deviceType: DeviceType): Promise<void> {
    console.log(`[LOCK] Closing slot for ${deviceType}`);
    
    const command: ArduinoCommand = deviceType === 'thickness'
      ? 'CLOSE_THICKNESS'
      : 'CLOSE_OBD';

    try {
      if (this.mockMode) {
        await new Promise(resolve => setTimeout(resolve, 500));
        this.status[deviceType] = 'closed';
        await this.logAction('close', deviceType, true);
      } else {
        const response = await this.sendCommandWithRetry(command);
        
        if (response.type === 'OK' && response.action?.includes('CLOSE')) {
          this.status[deviceType] = 'closed';
          await this.logAction('close', deviceType, true);
        } else {
          throw new Error(`Unexpected response: ${response.raw}`);
        }
      }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[LOCK] Failed to close ${deviceType}:`, errorMsg);
      this.status.error = errorMsg;
      await this.logAction('close', deviceType, false, errorMsg);
      throw error;
    }
  }

  /**
   * Получить статус всех замков
   */
  async getStatus(): Promise<LockStatus> {
    if (!this.mockMode && this.arduino && this.arduino.isConnected()) {
      try {
        const response = await this.arduino.sendCommand('STATUS');
        if (response.status) {
          this.status.thickness = response.status.thickness === 'OPEN' ? 'open' : 'closed';
          this.status.adapter = response.status.obd === 'OPEN' ? 'open' : 'closed';
        }
      } catch (error) {
        console.error('[LOCK] Failed to get status:', error);
      }
    }
    
    return { ...this.status };
  }

  /**
   * Отправить команду с повторными попытками
   */
  private async sendCommandWithRetry(command: ArduinoCommand, attempt = 1): Promise<ArduinoResponse> {
    if (!this.arduino) {
      throw new Error('Arduino adapter not initialized');
    }

    try {
      return await this.arduino.sendCommand(command);
    } catch (error) {
      if (attempt < this.retryCount) {
        console.log(`[LOCK] Retry ${attempt}/${this.retryCount} for command ${command}`);
        await new Promise(resolve => setTimeout(resolve, this.retryDelay * attempt));
        return this.sendCommandWithRetry(command, attempt + 1);
      }
      throw error;
    }
  }

  /**
   * Логирование операции в файл
   */
  private async logAction(
    action: 'open' | 'close',
    deviceType: DeviceType,
    success: boolean,
    error?: string
  ): Promise<void> {
    const log = {
      timestamp: new Date().toISOString(),
      action,
      deviceType,
      success,
      status: this.status[deviceType],
      error,
      sessionId: this.getCurrentSessionId(),
    };
    
    console.log('[LOCK-LOG]', JSON.stringify(log));

    // Запись в файл логов
    try {
      const date = new Date().toISOString().split('T')[0];
      const logFile = path.join(this.logDir, `lock-operations-${date}.json`);
      
      // Создать директорию если не существует
      await fs.mkdir(this.logDir, { recursive: true });
      
      // Добавить запись в файл
      const logLine = JSON.stringify(log) + '\n';
      await fs.appendFile(logFile, logLine, 'utf-8');
    } catch (fileError) {
      console.error('[LOCK] Failed to write log:', fileError);
    }
  }

  /**
   * Получить текущий ID сессии (заглушка)
   */
  private getCurrentSessionId(): string {
    // TODO: Интеграция с session manager
    return `session_${Date.now()}`;
  }

  /**
   * Закрыть контроллер и освободить ресурсы
   */
  async shutdown(): Promise<void> {
    console.log('[LOCK] Shutting down controller');
    
    if (this.arduino) {
      await this.arduino.disconnect();
    }
  }
}
