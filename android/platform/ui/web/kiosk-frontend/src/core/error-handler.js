/**
 * @typedef {Object} ShowErrorOptions
 * @property {string=} title
 * @property {() => void=} onRetry
 */

/** @type {HTMLDivElement | null} */
let errorModal = null;

/**
 * Display a blocking modal with retry affordances.
 * @param {string} message
 * @param {ShowErrorOptions} [options]
 * @returns {void}
 */
export function showError(message, options = {}) {
  if (!errorModal) {
    createErrorModal();
  }

  const titleEl = /** @type {HTMLElement | null} */ (errorModal?.querySelector('.error-modal-title'));
  const messageEl = /** @type {HTMLElement | null} */ (errorModal?.querySelector('.error-modal-message'));
  const retryBtn = /** @type {HTMLButtonElement | null} */ (errorModal?.querySelector('.error-modal-retry'));

  if (titleEl) {
    titleEl.textContent = options.title || 'Ошибка';
  }

  if (messageEl) {
    messageEl.textContent = message || 'Произошла неизвестная ошибка';
  }

  if (retryBtn) {
    retryBtn.style.display = options.onRetry ? '' : 'none';
    retryBtn.onclick = () => {
      hideError();
      if (options.onRetry) {
        options.onRetry();
      }
    };
  }

  errorModal?.classList.remove('hidden');
}

export function hideError() {
  if (errorModal) {
    errorModal.classList.add('hidden');
  }
}

function createErrorModal() {
  errorModal = document.createElement('div');
  errorModal.className = 'modal-backdrop hidden';
  errorModal.setAttribute('role', 'dialog');
  errorModal.setAttribute('aria-modal', 'true');
  errorModal.setAttribute('aria-labelledby', 'error-modal-title');

  errorModal.innerHTML = `
    <div class="modal">
      <div class="modal-header">
        <div class="modal-title error-modal-title" id="error-modal-title">Ошибка</div>
        <button type="button" class="close error-modal-close" aria-label="Закрыть">Закрыть</button>
      </div>
      <div class="modal-body">
        <div class="error-modal-message"></div>
      </div>
      <div class="modal-actions">
        <button type="button" class="secondary error-modal-close">Отменить</button>
        <button type="button" class="primary error-modal-retry">Повторить</button>
      </div>
    </div>
  `;

  document.body.appendChild(errorModal);

  const closeButtons = errorModal.querySelectorAll('.error-modal-close');
  closeButtons.forEach(btn => {
    btn.addEventListener('click', hideError);
  });

  errorModal.addEventListener('click', (e) => {
    if (errorModal && e.target === errorModal) {
      hideError();
    }
  });
}

export function initErrorHandler() {
  window.addEventListener('unhandledrejection', (event) => {
    console.error('[error-handler] Unhandled promise rejection:', event.reason);

    if (import.meta.env.DEV) {
      const reasonDetails = event.reason instanceof Error
        ? event.reason.message
        : String(event.reason);

      showError(`Необработанная ошибка: ${reasonDetails}`, {
        title: 'Ошибка разработки',
      });
    }
  });

  window.addEventListener('error', (event) => {
    console.error('[error-handler] Global error:', event.error);
  });

  console.log('[error-handler] Error handler initialized');
}
