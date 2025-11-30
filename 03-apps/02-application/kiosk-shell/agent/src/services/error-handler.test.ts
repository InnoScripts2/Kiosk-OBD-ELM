/**
 * Unit tests for ObdErrorHandler
 * Adapted from: DONORS/2-PowerBroker2-ELMduino/src/ELMduino.cpp patterns
 * Adapted from: DONORS/1-begaz-OBDII/lib/obd2_plugin.dart patterns
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import {
  ObdErrorHandler,
  withErrorHandling,
} from './error-handler.js';
import { ObdErrorCode } from './obd-protocol.js';

describe('ObdErrorHandler', () => {
  let handler: ObdErrorHandler;

  beforeEach(() => {
    jest.useFakeTimers();
    handler = new ObdErrorHandler({
      maxRetries: 3,
      retryDelay: 1000,
      exponentialBackoff: true,
      maxRetryDelay: 10000,
    });
    // Prevent unhandled error events from failing tests
    handler.on('error', () => { /* handled */ });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('getErrorMessage', () => {
    it('should return correct message for SUCCESS', () => {
      expect(handler.getErrorMessage(ObdErrorCode.SUCCESS)).toBe('Success');
    });

    it('should return correct message for TIMEOUT', () => {
      expect(handler.getErrorMessage(ObdErrorCode.TIMEOUT)).toBe(
        'Operation timed out'
      );
    });

    it('should return correct message for NO_RESPONSE', () => {
      expect(handler.getErrorMessage(ObdErrorCode.NO_RESPONSE)).toBe(
        'No response from adapter'
      );
    });

    it('should return correct message for UNABLE_TO_CONNECT', () => {
      expect(handler.getErrorMessage(ObdErrorCode.UNABLE_TO_CONNECT)).toBe(
        'Unable to connect to vehicle'
      );
    });

    it('should return correct message for NO_DATA', () => {
      expect(handler.getErrorMessage(ObdErrorCode.NO_DATA)).toBe(
        'No data available'
      );
    });

    it('should return correct message for BUFFER_OVERFLOW', () => {
      expect(handler.getErrorMessage(ObdErrorCode.BUFFER_OVERFLOW)).toBe(
        'Response buffer overflow'
      );
    });
  });

  describe('createErrorContext', () => {
    it('should create error context with correct properties', () => {
      const context = handler.createErrorContext(
        ObdErrorCode.TIMEOUT,
        '010C'
      );

      expect(context.code).toBe(ObdErrorCode.TIMEOUT);
      expect(context.message).toBe('Operation timed out');
      expect(context.command).toBe('010C');
      expect(context.retryCount).toBe(0);
      expect(context.timestamp).toBeInstanceOf(Date);
    });

    it('should emit error event', () => {
      const spy = jest.fn();
      handler.on('error', spy);

      handler.createErrorContext(ObdErrorCode.TIMEOUT);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({ code: ObdErrorCode.TIMEOUT })
      );
    });

    it('should record error in history', () => {
      handler.createErrorContext(ObdErrorCode.TIMEOUT);

      const history = handler.getErrorHistory();
      expect(history).toHaveLength(1);
      expect(history[0].code).toBe(ObdErrorCode.TIMEOUT);
    });
  });

  describe('getRetryStrategy', () => {
    it('should allow retry for TIMEOUT with increased timeout', () => {
      const context = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(true);
      expect(strategy.timeout).toBeDefined();
    });

    it('should recommend reconnect for NO_RESPONSE', () => {
      const context = handler.createErrorContext(ObdErrorCode.NO_RESPONSE);
      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(true);
      expect(strategy.action).toBe('reconnect');
    });

    it('should recommend reinitialize for UNABLE_TO_CONNECT', () => {
      const context = handler.createErrorContext(ObdErrorCode.UNABLE_TO_CONNECT);
      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(true);
      expect(strategy.action).toBe('reinitialize');
    });

    it('should not retry for NO_DATA', () => {
      const context = handler.createErrorContext(ObdErrorCode.NO_DATA);
      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(false);
    });

    it('should not retry for STOPPED', () => {
      const context = handler.createErrorContext(ObdErrorCode.STOPPED);
      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(false);
    });

    it('should use exponential backoff', () => {
      const context1 = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      context1.retryCount = 0;
      const strategy1 = handler.getRetryStrategy(context1);

      const context2 = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      context2.retryCount = 1;
      const strategy2 = handler.getRetryStrategy(context2);

      const context3 = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      context3.retryCount = 2;
      const strategy3 = handler.getRetryStrategy(context3);

      expect(strategy2.delay).toBeGreaterThan(strategy1.delay);
      expect(strategy3.delay).toBeGreaterThan(strategy2.delay);
    });

    it('should not exceed max retries', () => {
      const context = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      context.retryCount = 10; // Exceeds maxRetries

      const strategy = handler.getRetryStrategy(context);

      expect(strategy.shouldRetry).toBe(false);
    });

    it('should emit maxRetriesExceeded event', () => {
      const spy = jest.fn();
      handler.on('maxRetriesExceeded', spy);

      const context = handler.createErrorContext(ObdErrorCode.TIMEOUT);
      context.retryCount = 10;

      handler.getRetryStrategy(context);

      expect(spy).toHaveBeenCalled();
    });
  });

  describe('isRecoverable', () => {
    it('should return true for TIMEOUT', () => {
      expect(handler.isRecoverable(ObdErrorCode.TIMEOUT)).toBe(true);
    });

    it('should return true for NO_RESPONSE', () => {
      expect(handler.isRecoverable(ObdErrorCode.NO_RESPONSE)).toBe(true);
    });

    it('should return true for BUFFER_OVERFLOW', () => {
      expect(handler.isRecoverable(ObdErrorCode.BUFFER_OVERFLOW)).toBe(true);
    });

    it('should return false for NO_DATA', () => {
      expect(handler.isRecoverable(ObdErrorCode.NO_DATA)).toBe(false);
    });

    it('should return false for STOPPED', () => {
      expect(handler.isRecoverable(ObdErrorCode.STOPPED)).toBe(false);
    });
  });

  describe('withRetry', () => {
    it('should return result on first success', async () => {
      jest.useRealTimers();

      const fn = jest.fn(() => Promise.resolve('success'));

      const result = await handler.withRetry(fn);

      expect(result).toBe('success');
      expect(fn).toHaveBeenCalledTimes(1);
    });

    it('should retry on failure and succeed', async () => {
      jest.useRealTimers();

      let attempts = 0;
      const fn = jest.fn(() => {
        attempts++;
        if (attempts < 2) {
          return Promise.reject(new Error('timeout'));
        }
        return Promise.resolve('success');
      });

      const result = await handler.withRetry(fn);

      expect(result).toBe('success');
      expect(fn).toHaveBeenCalledTimes(2);
    });

    it('should emit retry event on retry', async () => {
      jest.useRealTimers();

      const spy = jest.fn();
      handler.on('retry', spy);

      let attempts = 0;
      const fn = jest.fn(() => {
        attempts++;
        if (attempts < 2) {
          return Promise.reject(new Error('timeout'));
        }
        return Promise.resolve('success');
      });

      await handler.withRetry(fn);

      expect(spy).toHaveBeenCalled();
    });

    it('should throw after max retries', async () => {
      jest.useRealTimers();

      // Create handler with very short delays for testing
      const fastHandler = new ObdErrorHandler({ 
        maxRetries: 2, 
        retryDelay: 10,
        exponentialBackoff: false
      });
      fastHandler.on('error', () => { /* handled */ });

      const fn = jest.fn(() => Promise.reject(new Error('always fails')));

      await expect(fastHandler.withRetry(fn)).rejects.toThrow('always fails');
      expect(fn).toHaveBeenCalledTimes(3); // 1 initial + 2 retries
    }, 10000);
  });

  describe('error history', () => {
    it('should track error history', () => {
      handler.createErrorContext(ObdErrorCode.TIMEOUT);
      handler.createErrorContext(ObdErrorCode.NO_RESPONSE);
      handler.createErrorContext(ObdErrorCode.GENERAL_ERROR);

      const history = handler.getErrorHistory();

      expect(history).toHaveLength(3);
    });

    it('should get recent errors', () => {
      handler.createErrorContext(ObdErrorCode.TIMEOUT);
      handler.createErrorContext(ObdErrorCode.NO_RESPONSE);
      handler.createErrorContext(ObdErrorCode.GENERAL_ERROR);

      const recent = handler.getRecentErrors(2);

      expect(recent).toHaveLength(2);
      expect(recent[1].code).toBe(ObdErrorCode.GENERAL_ERROR);
    });

    it('should clear error history', () => {
      handler.createErrorContext(ObdErrorCode.TIMEOUT);
      handler.createErrorContext(ObdErrorCode.NO_RESPONSE);

      handler.clearErrorHistory();

      expect(handler.getErrorHistory()).toHaveLength(0);
    });

    it('should get error statistics', () => {
      handler.createErrorContext(ObdErrorCode.TIMEOUT);
      handler.createErrorContext(ObdErrorCode.TIMEOUT);
      handler.createErrorContext(ObdErrorCode.NO_RESPONSE);

      const stats = handler.getErrorStats();

      expect(stats[ObdErrorCode.TIMEOUT]).toBe(2);
      expect(stats[ObdErrorCode.NO_RESPONSE]).toBe(1);
    });
  });

  describe('withTimeout', () => {
    it('should resolve before timeout', async () => {
      jest.useRealTimers();

      const fn = jest.fn(() =>
        new Promise<string>((resolve) => setTimeout(() => resolve('success'), 10))
      );

      const result = await handler.withTimeout(fn, 1000);

      expect(result).toBe('success');
    });

    it('should reject on timeout', async () => {
      jest.useRealTimers();
      
      // Create a new handler for this test with error listener
      const testHandler = new ObdErrorHandler();
      testHandler.on('error', () => { /* handled */ });

      const fn = jest.fn(
        () =>
          new Promise<string>((resolve) =>
            setTimeout(() => resolve('success'), 200)
          )
      );

      await expect(testHandler.withTimeout(fn, 50)).rejects.toThrow('timed out');
    });
  });

  describe('configuration', () => {
    it('should update configuration', () => {
      handler.updateConfig({ maxRetries: 5, retryDelay: 500 });

      const config = handler.getConfig();

      expect(config.maxRetries).toBe(5);
      expect(config.retryDelay).toBe(500);
    });

    it('should get current configuration', () => {
      const config = handler.getConfig();

      expect(config.maxRetries).toBe(3);
      expect(config.retryDelay).toBe(1000);
      expect(config.exponentialBackoff).toBe(true);
    });
  });
});

describe('withErrorHandling', () => {
  it('should wrap function with error handling', async () => {
    jest.useRealTimers();

    const handler = new ObdErrorHandler();
    handler.on('error', () => { /* handled */ });
    const fn = jest.fn((x: number) => Promise.resolve(x * 2));

    const wrapped = withErrorHandling(handler, fn);

    const result = await wrapped(5);

    expect(result).toBe(10);
    expect(fn).toHaveBeenCalledWith(5);
  });

  it('should retry wrapped function on failure', async () => {
    jest.useRealTimers();

    const handler = new ObdErrorHandler({ 
      maxRetries: 2,
      retryDelay: 10,
      exponentialBackoff: false
    });
    handler.on('error', () => { /* handled */ });

    let attempts = 0;
    const fn = jest.fn((x: number) => {
      attempts++;
      if (attempts < 2) {
        return Promise.reject(new Error('timeout'));
      }
      return Promise.resolve(x * 2);
    });

    const wrapped = withErrorHandling(handler, fn);

    const result = await wrapped(5);

    expect(result).toBe(10);
    expect(fn).toHaveBeenCalledTimes(2);
  }, 10000);
});
