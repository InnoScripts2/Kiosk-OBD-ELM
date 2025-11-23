import { LockController } from './LockController.js';
import { describe, it, expect, beforeEach, afterEach, jest } from '@jest/globals';

describe('LockController', () => {
  let controller: LockController;

  beforeEach(() => {
    // Всегда используем mock mode в тестах
    controller = new LockController({ mockMode: true });
  });

  afterEach(async () => {
    await controller.shutdown();
  });

  describe('Mock Mode', () => {
    it('изначально все замки закрыты', async () => {
      const status = await controller.getStatus();
      expect(status.thickness).toBe('closed');
      expect(status.adapter).toBe('closed');
    });

    it('открывает слот толщиномера', async () => {
      await controller.openSlot('thickness');
      const status = await controller.getStatus();
      expect(status.thickness).toBe('open');
    });

    it('закрывает слот адаптера', async () => {
      await controller.openSlot('adapter');
      let status = await controller.getStatus();
      expect(status.adapter).toBe('open');

      await controller.closeSlot('adapter');
      status = await controller.getStatus();
      expect(status.adapter).toBe('closed');
    });

    it('управляет несколькими устройствами независимо', async () => {
      await controller.openSlot('thickness');
      await controller.openSlot('adapter');

      let status = await controller.getStatus();
      expect(status.thickness).toBe('open');
      expect(status.adapter).toBe('open');

      await controller.closeSlot('thickness');
      status = await controller.getStatus();
      expect(status.thickness).toBe('closed');
      expect(status.adapter).toBe('open');
    });

    it('логирует все операции', async () => {
      const consoleSpy = jest.spyOn(console, 'log');
      
      await controller.openSlot('thickness');
      
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('[LOCK]'),
        expect.stringContaining('Opening slot for thickness')
      );
      
      consoleSpy.mockRestore();
    });

    it('сохраняет статус connected в mock mode', async () => {
      const status = await controller.getStatus();
      expect(status.connected).toBe(true);
    });

    it('возвращает правильный статус после нескольких операций', async () => {
      await controller.openSlot('thickness');
      await controller.closeSlot('thickness');
      await controller.openSlot('adapter');
      
      const status = await controller.getStatus();
      expect(status.thickness).toBe('closed');
      expect(status.adapter).toBe('open');
    });
  });

  describe('Error Handling', () => {
    it('сохраняет ошибку в статусе при неудаче', async () => {
      // Создаём контроллер который попытается подключиться к несуществующему Arduino
      const failController = new LockController({
        mockMode: false,
        arduino: {
          port: '/dev/nonexistent',
          baudRate: 9600,
          commandTimeout: 100,
          reconnectDelay: 1000,
          heartbeatInterval: 30000,
        },
      });

      try {
        await failController.initialize();
      } catch (error) {
        // Ожидаем ошибку подключения
      }

      const status = await failController.getStatus();
      expect(status.error).toBeDefined();
      
      await failController.shutdown();
    });
  });

  describe('Initialization', () => {
    it('инициализирует контроллер в mock mode без ошибок', async () => {
      const newController = new LockController({ mockMode: true });
      
      await expect(newController.initialize()).resolves.not.toThrow();
      
      const status = await newController.getStatus();
      expect(status.connected).toBe(true);
      
      await newController.shutdown();
    });
  });

  describe('Shutdown', () => {
    it('корректно завершает работу', async () => {
      await controller.initialize();
      await expect(controller.shutdown()).resolves.not.toThrow();
    });
  });
});

