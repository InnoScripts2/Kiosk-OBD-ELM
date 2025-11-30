/**
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart
 * Original language: C++ / Dart
 * Integration date: 2025-11-30
 * Purpose: Error handling, retry logic, and reconnection for OBD communication
 *
 * Key patterns adapted:
 * - Error codes from ELMduino.h (ELM_SUCCESS, ELM_TIMEOUT, etc.)
 * - Reconnection logic from obd2_plugin.dart
 * - Timeout handling from ELMduino.cpp
 */

import { EventEmitter } from 'events';
import { ObdErrorCode } from './obd-protocol.js';

/**
 * Error handler configuration
 */
export interface ErrorHandlerConfig {
  /** Maximum retry attempts */
  maxRetries?: number;
  /** Base delay between retries (ms) */
  retryDelay?: number;
  /** Use exponential backoff */
  exponentialBackoff?: boolean;
  /** Maximum delay between retries (ms) */
  maxRetryDelay?: number;
  /** Connection timeout (ms) */
  connectionTimeout?: number;
  /** Command timeout (ms) */
  commandTimeout?: number;
}

/**
 * Error context for handling
 */
export interface ErrorContext {
  /** Error code */
  code: ObdErrorCode;
  /** Error message */
  message: string;
  /** Command that failed (if applicable) */
  command?: string;
  /** Number of retry attempts made */
  retryCount?: number;
  /** Timestamp of error */
  timestamp: Date;
  /** Original error */
  originalError?: Error;
}

/**
 * Retry strategy result
 */
export interface RetryStrategy {
  /** Whether to retry */
  shouldRetry: boolean;
  /** Delay before retry (ms) */
  delay: number;
  /** Modified timeout for retry */
  timeout?: number;
  /** Alternative action to take */
  action?: 'reconnect' | 'reinitialize' | 'alternate_protocol';
}

/**
 * ObdErrorHandler - handles errors, retries, and reconnection
 *
 * Adapted from:
 * - ELMduino error codes and timeout handling
 * - obd2_plugin.dart reconnection logic
 */
export class ObdErrorHandler extends EventEmitter {
  private config: Required<ErrorHandlerConfig>;
  private errorHistory: ErrorContext[] = [];
  private readonly maxHistorySize = 100;

  constructor(config?: ErrorHandlerConfig) {
    super();
    this.config = {
      maxRetries: config?.maxRetries ?? 3,
      retryDelay: config?.retryDelay ?? 1000,
      exponentialBackoff: config?.exponentialBackoff ?? true,
      maxRetryDelay: config?.maxRetryDelay ?? 10000,
      connectionTimeout: config?.connectionTimeout ?? 5000,
      commandTimeout: config?.commandTimeout ?? 3000,
    };
  }

  /**
   * Create error context from error code
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - printError
   *
   * @param code - Error code
   * @param command - Failed command (optional)
   * @param originalError - Original error (optional)
   * @returns Error context
   */
  createErrorContext(
    code: ObdErrorCode,
    command?: string,
    originalError?: Error
  ): ErrorContext {
    const context: ErrorContext = {
      code,
      message: this.getErrorMessage(code),
      command,
      retryCount: 0,
      timestamp: new Date(),
      originalError,
    };

    this.recordError(context);
    return context;
  }

  /**
   * Get human-readable error message
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp - printError
   */
  getErrorMessage(code: ObdErrorCode): string {
    switch (code) {
      case ObdErrorCode.SUCCESS:
        return 'Success';
      case ObdErrorCode.NO_RESPONSE:
        return 'No response from adapter';
      case ObdErrorCode.BUFFER_OVERFLOW:
        return 'Response buffer overflow';
      case ObdErrorCode.GARBAGE:
        return 'Received garbage data';
      case ObdErrorCode.UNABLE_TO_CONNECT:
        return 'Unable to connect to vehicle';
      case ObdErrorCode.NO_DATA:
        return 'No data available';
      case ObdErrorCode.STOPPED:
        return 'Operation stopped';
      case ObdErrorCode.TIMEOUT:
        return 'Operation timed out';
      case ObdErrorCode.GETTING_MSG:
        return 'Receiving message';
      case ObdErrorCode.MSG_RECEIVED:
        return 'Message received';
      case ObdErrorCode.GENERAL_ERROR:
      default:
        return 'General error';
    }
  }

  /**
   * Determine retry strategy for an error
   * Adapted from: DONORS/2-PowerBroker2-ELMduino/NOTES.md - error handling
   * Adapted from: DONORS/1-begaz-OBDII/NOTES.md - reconnection logic
   *
   * @param context - Error context
   * @returns Retry strategy
   */
  getRetryStrategy(context: ErrorContext): RetryStrategy {
    const retryCount = context.retryCount ?? 0;

    // Check if max retries exceeded
    if (retryCount >= this.config.maxRetries) {
      this.emit('maxRetriesExceeded', context);
      return { shouldRetry: false, delay: 0 };
    }

    // Calculate delay with optional exponential backoff
    let delay = this.config.retryDelay;
    if (this.config.exponentialBackoff) {
      delay = Math.min(
        this.config.retryDelay * Math.pow(2, retryCount),
        this.config.maxRetryDelay
      );
    }

    switch (context.code) {
      case ObdErrorCode.TIMEOUT:
        // Retry with longer timeout
        return {
          shouldRetry: true,
          delay,
          timeout: this.config.commandTimeout * (retryCount + 2),
        };

      case ObdErrorCode.NO_RESPONSE:
        // Try to reconnect
        return {
          shouldRetry: true,
          delay: delay * 2,
          action: 'reconnect',
        };

      case ObdErrorCode.UNABLE_TO_CONNECT:
        // Try to reinitialize
        return {
          shouldRetry: true,
          delay: delay * 3,
          action: 'reinitialize',
        };

      case ObdErrorCode.BUFFER_OVERFLOW:
      case ObdErrorCode.GARBAGE:
        // Retry immediately
        return {
          shouldRetry: true,
          delay: 100,
        };

      case ObdErrorCode.NO_DATA:
        // Vehicle may not support this PID - don't retry
        return { shouldRetry: false, delay: 0 };

      case ObdErrorCode.STOPPED:
        // Operation was explicitly stopped - don't retry
        return { shouldRetry: false, delay: 0 };

      case ObdErrorCode.GENERAL_ERROR:
      default:
        // Generic retry
        return {
          shouldRetry: true,
          delay,
        };
    }
  }

  /**
   * Execute a function with retry logic
   *
   * @param fn - Function to execute
   * @param context - Error context for retry tracking
   * @returns Result of function execution
   */
  async withRetry<T>(
    fn: () => Promise<T>,
    context?: Partial<ErrorContext>
  ): Promise<T> {
    let lastError: Error | null = null;
    let retryCount = 0;

    while (retryCount <= this.config.maxRetries) {
      try {
        return await fn();
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));

        const errorContext = this.createErrorContext(
          this.inferErrorCode(lastError),
          context?.command,
          lastError
        );
        errorContext.retryCount = retryCount;

        const strategy = this.getRetryStrategy(errorContext);

        if (!strategy.shouldRetry) {
          throw lastError;
        }

        this.emit('retry', { context: errorContext, strategy, attempt: retryCount + 1 });

        await this.delay(strategy.delay);
        retryCount++;
      }
    }

    throw lastError ?? new Error('Max retries exceeded');
  }

  /**
   * Infer error code from error message
   */
  private inferErrorCode(error: Error): ObdErrorCode {
    const message = error.message.toLowerCase();

    if (message.includes('timeout')) {
      return ObdErrorCode.TIMEOUT;
    }
    if (message.includes('no response') || message.includes('no data')) {
      return ObdErrorCode.NO_RESPONSE;
    }
    if (message.includes('connect')) {
      return ObdErrorCode.UNABLE_TO_CONNECT;
    }
    if (message.includes('overflow') || message.includes('buffer')) {
      return ObdErrorCode.BUFFER_OVERFLOW;
    }
    if (message.includes('stopped') || message.includes('cancelled')) {
      return ObdErrorCode.STOPPED;
    }

    return ObdErrorCode.GENERAL_ERROR;
  }

  /**
   * Record error in history
   */
  private recordError(context: ErrorContext): void {
    this.errorHistory.push(context);

    // Limit history size
    if (this.errorHistory.length > this.maxHistorySize) {
      this.errorHistory.shift();
    }

    this.emit('error', context);
  }

  /**
   * Get error history
   */
  getErrorHistory(): ErrorContext[] {
    return [...this.errorHistory];
  }

  /**
   * Get recent errors (last N)
   */
  getRecentErrors(count: number): ErrorContext[] {
    return this.errorHistory.slice(-count);
  }

  /**
   * Clear error history
   */
  clearErrorHistory(): void {
    this.errorHistory = [];
  }

  /**
   * Get error statistics
   */
  getErrorStats(): Record<ObdErrorCode, number> {
    const stats: Record<number, number> = {};

    for (const context of this.errorHistory) {
      stats[context.code] = (stats[context.code] ?? 0) + 1;
    }

    return stats;
  }

  /**
   * Check if error is recoverable
   */
  isRecoverable(code: ObdErrorCode): boolean {
    switch (code) {
      case ObdErrorCode.TIMEOUT:
      case ObdErrorCode.NO_RESPONSE:
      case ObdErrorCode.BUFFER_OVERFLOW:
      case ObdErrorCode.GARBAGE:
      case ObdErrorCode.GENERAL_ERROR:
        return true;

      case ObdErrorCode.UNABLE_TO_CONNECT:
      case ObdErrorCode.NO_DATA:
      case ObdErrorCode.STOPPED:
        return false;

      default:
        return false;
    }
  }

  /**
   * Create a timeout promise
   *
   * @param ms - Timeout in milliseconds
   * @param message - Error message on timeout
   * @returns Promise that rejects after timeout
   */
  createTimeout(ms: number, message = 'Operation timed out'): Promise<never> {
    return new Promise((_, reject) => {
      setTimeout(() => {
        const context = this.createErrorContext(ObdErrorCode.TIMEOUT);
        reject(new Error(`${message} after ${ms}ms`));
        this.emit('timeout', context);
      }, ms);
    });
  }

  /**
   * Execute with timeout
   *
   * @param fn - Function to execute
   * @param timeout - Timeout in milliseconds
   * @returns Result of function execution
   */
  async withTimeout<T>(fn: () => Promise<T>, timeout?: number): Promise<T> {
    const timeoutMs = timeout ?? this.config.commandTimeout;

    return Promise.race([fn(), this.createTimeout(timeoutMs)]);
  }

  /**
   * Delay utility
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<ErrorHandlerConfig>): void {
    this.config = {
      ...this.config,
      ...config,
    };
  }

  /**
   * Get current configuration
   */
  getConfig(): Required<ErrorHandlerConfig> {
    return { ...this.config };
  }
}

/**
 * Create a wrapped function with automatic retry and timeout handling
 *
 * @param handler - Error handler instance
 * @param fn - Function to wrap
 * @param options - Options for wrapping
 * @returns Wrapped function
 */
export function withErrorHandling<T extends unknown[], R>(
  handler: ObdErrorHandler,
  fn: (...args: T) => Promise<R>,
  options?: {
    command?: string;
    timeout?: number;
    retries?: number;
  }
): (...args: T) => Promise<R> {
  return async (...args: T): Promise<R> => {
    const execute = (): Promise<R> => {
      if (options?.timeout) {
        return handler.withTimeout(() => fn(...args), options.timeout);
      }
      return fn(...args);
    };

    // Apply retry logic - each retry calls execute() fresh
    return handler.withRetry(execute, { command: options?.command });
  };
}
