import { LockController } from '../services/LockController';

describe('LockController', () => {
  let controller: LockController;

  beforeEach(() => {
    controller = new LockController();
  });

  test('изначально все замки закрыты', () => {
    const status = controller.getStatus();
    expect(status.thickness).toBe('closed');
    expect(status.adapter).toBe('closed');
  });

  test('открывает слот толщиномера', async () => {
    await controller.openSlot('thickness');
    const status = controller.getStatus();
    expect(status.thickness).toBe('open');
  });

  test('закрывает слот адаптера', async () => {
    await controller.openSlot('adapter');
    let status = controller.getStatus();
    expect(status.adapter).toBe('open');

    await controller.closeSlot('adapter');
    status = controller.getStatus();
    expect(status.adapter).toBe('closed');
  });

  test('управляет несколькими устройствами независимо', async () => {
    await controller.openSlot('thickness');
    await controller.openSlot('adapter');

    let status = controller.getStatus();
    expect(status.thickness).toBe('open');
    expect(status.adapter).toBe('open');

    await controller.closeSlot('thickness');
    status = controller.getStatus();
    expect(status.thickness).toBe('closed');
    expect(status.adapter).toBe('open');
  });
});
