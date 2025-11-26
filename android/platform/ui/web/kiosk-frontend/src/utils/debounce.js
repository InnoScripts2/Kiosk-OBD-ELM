/**
 * @template {(...args: any[]) => void} T
 * @param {T} func
 * @param {number} wait
 * @returns {(...args: Parameters<T>) => void}
 */
export function debounce(func, wait) {
  /** @type {ReturnType<typeof setTimeout> | null} */
  let timeout = null;
  return function executedFunction(...args) {
    const later = () => {
      if (timeout) {
        clearTimeout(timeout);
      }
      func(...args);
    };
    if (timeout) {
      clearTimeout(timeout);
    }
    timeout = setTimeout(later, wait);
  };
}

/**
 * @template {(...args: any[]) => void} T
 * @param {T} func
 * @param {number} limit
 * @returns {(...args: Parameters<T>) => void}
 */
export function throttle(func, limit) {
  let inThrottle = false;
  /**
   * @this {ThisParameterType<T>}
   */
  return function throttled(...args) {
    const context = /** @type {ThisParameterType<T>} */ (this);
    if (!inThrottle) {
      func.apply(context, args);
      inThrottle = true;
      setTimeout(() => {
        inThrottle = false;
      }, limit);
    }
  };
}
