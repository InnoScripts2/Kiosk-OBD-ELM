// @ts-check
import { showScreen } from './navigation.js';
import { appendSessionEvent } from '@services/supabase-sessions.js';
import { clearSessionState, generateSessionId, getSessionState, resetIdleTimer, setSessionValue } from './session-manager.js';
import { validateContact } from '../utils/validators.js';
import { formatCurrency, formatPhoneNumber } from '../utils/formatters.js';

const SELECTED_CLASS = 'selected';

/**
 * @typedef {{ rootId: string; cardSelector: string; onSelect: (card: HTMLElement) => void }} CardGroupOptions
 */

/**
 * @typedef {{
 *  inputId: string;
 *  continueButtonId: string;
 *  sessionKey: string;
 *  nextScreen: string;
 *  errorId?: string;
 *  hintId?: string;
 *  onValid?: (result: import('../utils/validators.js').ContactValidationResult) => void;
 * }} ContactFormOptions
 */

/**
 * @param {HTMLElement[]} cards
 * @param {HTMLElement | null} activeCard
 */
function applySelectionState(cards, activeCard) {
    cards.forEach(card => {
        const isActive = card === activeCard;
        card.classList.toggle(SELECTED_CLASS, isActive);
        card.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    });
}

/**
 * @param {HTMLElement | null} element
 * @param {(event: Event) => void} handler
 */
function bindTap(element, handler) {
    if (!element) {
        return;
    }
    const wrapped = (event) => {
        resetIdleTimer();
        handler(event);
    };
    element.addEventListener('click', wrapped);
    element.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            wrapped(event);
        }
    });
}

/**
 * @param {CardGroupOptions} options
 * @returns {HTMLElement[]}
 */
function bindSelectableCards(options) {
    const root = document.getElementById(options.rootId);
    if (!root) {
        return [];
    }
    const cards = Array.from(root.querySelectorAll(options.cardSelector));
    cards.forEach(card => {
        card.setAttribute('role', card.getAttribute('role') || 'button');
        card.setAttribute('tabindex', card.getAttribute('tabindex') || '0');
        card.setAttribute('aria-pressed', card.classList.contains(SELECTED_CLASS) ? 'true' : 'false');
        bindTap(card, () => {
            applySelectionState(cards, card);
            options.onSelect(card);
        });
    });
    return cards;
}

function ensureThicknessSessionId() {
    const state = getSessionState();
    if (!state.session.thicknessId) {
        setSessionValue('session.thicknessId', generateSessionId('thk'));
    }
}

function ensureDiagnosticsSessionId() {
    const state = getSessionState();
    if (!state.session.obdId) {
        setSessionValue('session.obdId', generateSessionId('obd'));
    }
}

/**
 * @param {string} path
 * @returns {unknown}
 */
function getSessionValue(path) {
    const segments = path.split('.');
    let current = /** @type {unknown} */ (getSessionState());
    for (const segment of segments) {
        if (!current || typeof current !== 'object') {
            return undefined;
        }
        current = /** @type {Record<string, unknown>} */ (current)[segment];
    }
    return current;
}

/**
 * @param {string} value
 * @returns {string}
 */
function formatContactValueForInput(value) {
    if (!value || typeof value !== 'string') {
        return '';
    }
    const trimmed = value.trim();
    if (!trimmed) {
        return '';
    }
    if (trimmed.includes('@')) {
        return trimmed;
    }
    const digits = trimmed.replace(/\D/g, '');
    const formatted = formatPhoneNumber(digits);
    return formatted || trimmed;
}

/**
 * @param {HTMLInputElement} input
 */
function maybeFormatPhoneInput(input) {
    const raw = input.value || '';
    if (!raw || raw.includes('@')) {
        return;
    }
    const digits = raw.replace(/\D/g, '');
    if (!digits) {
        return;
    }
    const formatted = formatPhoneNumber(digits);
    if (formatted && formatted !== raw) {
        input.value = formatted;
    }
}

/**
 * @param {HTMLElement | null} hintNode
 * @param {string} defaultText
 * @param {import('../utils/validators.js').ContactValidationResult} result
 * @param {string} displayValue
 */
function updateContactHint(hintNode, defaultText, result, displayValue) {
    if (!hintNode) {
        return;
    }
    if (!result.valid || !result.type) {
        hintNode.textContent = defaultText;
        return;
    }
    if (result.type === 'phone') {
        const formatted = formatContactValueForInput(displayValue);
        if (formatted) {
            hintNode.textContent = `Отправим SMS на ${formatted}`;
            return;
        }
    }
    if (result.type === 'email') {
        hintNode.textContent = `Отправим письмо на ${displayValue}`;
        return;
    }
    hintNode.textContent = defaultText;
}

function resolveContactChannel(sessionKey) {
    if (!sessionKey) {
        return 'contact';
    }
    const segments = sessionKey.split('.');
    return segments[segments.length - 1] || sessionKey;
}

function emitContactUpdated(options, result, value) {
    if (!value) {
        return;
    }
    const state = getSessionState();
    const sessionId = state.session?.kiosk;
    if (!sessionId) {
        return;
    }
    const channel = resolveContactChannel(options.sessionKey);
    void appendSessionEvent({
        sessionId,
        eventType: 'contact_updated',
        eventStatus: result.type ?? null,
        message: channel,
        payload: {
            channel,
            type: result.type ?? null,
            value,
        },
    });
}

function navigateTo(screenId) {
    showScreen(screenId);
}

function initAttractScreen() {
    const attract = document.getElementById('screen-attract');
    if (!attract) {
        return;
    }
    const start = () => {
        resetIdleTimer();
        navigateTo('screen-welcome');
    };
    attract.addEventListener('click', start);
    attract.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            start();
        }
    });
}

function initWelcomeScreen() {
    const agree = document.getElementById('welcome-agree');
    const continueBtn = document.getElementById('welcome-continue');
    if (!agree || !(agree instanceof HTMLInputElement) || !(continueBtn instanceof HTMLButtonElement)) {
        return;
    }
    agree.addEventListener('change', () => {
        continueBtn.disabled = !agree.checked;
    });
    continueBtn.addEventListener('click', () => {
        if (agree.checked) {
            resetIdleTimer();
            navigateTo('screen-services');
        }
    });
}

function initServiceSelection() {
    const continueBtn = document.getElementById('service-continue');
    if (!(continueBtn instanceof HTMLButtonElement)) {
        return;
    }
    const cards = Array.from(document.querySelectorAll('.service-card'));
    let selectedService = getSessionState().selectedService || null;

    const updateState = () => {
        continueBtn.disabled = !selectedService;
    };

    cards.forEach(card => {
        const service = card.getAttribute('data-service');
        if (!service) {
            return;
        }
        bindTap(card, () => {
            selectedService = service;
            applySelectionState(cards, card);
            setSessionValue('selectedService', service);
            setSessionValue('serviceType', service);
            if (service === 'thickness') {
                ensureThicknessSessionId();
            } else if (service === 'diagnostics') {
                ensureDiagnosticsSessionId();
            }
            updateState();
        });
    });

    continueBtn.addEventListener('click', () => {
        if (!selectedService) {
            return;
        }
        resetIdleTimer();
        if (selectedService === 'thickness') {
            navigateTo('screen-thk-intro');
        } else {
            navigateTo('screen-obd-intro');
        }
    });

    if (selectedService) {
        const initialCard = cards.find(card => card.getAttribute('data-service') === selectedService);
        if (initialCard) {
            applySelectionState(cards, initialCard);
        }
    }

    updateState();
}

function updateThicknessPreview(card) {
    const preview = document.getElementById('thk-vehicle-preview');
    const caption = document.getElementById('thk-vehicle-caption');
    const image = document.getElementById('thk-vehicle-image');
    if (!preview) {
        return;
    }
    preview.classList.remove('hidden');
    const title = card.querySelector('.card-title');
    if (caption && title) {
        caption.textContent = title.textContent || '';
    }
    if (image) {
        const datasetImage = card.getAttribute('data-image');
        if (datasetImage) {
            image.src = datasetImage;
            image.alt = title?.textContent || 'Автомобиль';
        }
    }
}

function updateThicknessPrice(card) {
    const priceNode = document.getElementById('thk-amount');
    if (!priceNode) {
        return;
    }
    const priceValue = Number(card.getAttribute('data-price'));
    priceNode.textContent = isFinite(priceValue)
        ? `К оплате: ${formatCurrency(priceValue)}`
        : 'Сумма уточняется';
}

function initThicknessFlow() {
    const cards = bindSelectableCards({
        rootId: 'thk-types',
        cardSelector: '.thk-type-card',
        onSelect: (card) => {
            const type = card.getAttribute('data-type') || null;
            setSessionValue('thicknessType', type);
            ensureThicknessSessionId();
            updateThicknessPreview(card);
            updateThicknessPrice(card);
            const continueBtn = document.getElementById('thk-continue-1');
            if (continueBtn instanceof HTMLButtonElement) {
                continueBtn.disabled = !type;
            }
        },
    });

    const continueOne = document.getElementById('thk-continue-1');
    if (continueOne instanceof HTMLButtonElement) {
        continueOne.addEventListener('click', () => {
            const type = getSessionState().thicknessType;
            if (!type) {
                return;
            }
            resetIdleTimer();
            ensureThicknessSessionId();
            navigateTo('screen-thk-contact');
        });
    }

    const continueThree = document.getElementById('thk-continue-3');
    if (continueThree instanceof HTMLButtonElement) {
        continueThree.addEventListener('click', () => {
            resetIdleTimer();
            navigateTo('screen-thk-measure');
        });
    }

    const initialType = getSessionState().thicknessType;
    if (initialType) {
        const initialCard = cards.find(card => card.getAttribute('data-type') === initialType);
        if (initialCard) {
            applySelectionState(cards, initialCard);
            updateThicknessPreview(initialCard);
            updateThicknessPrice(initialCard);
            const continueBtn = document.getElementById('thk-continue-1');
            if (continueBtn instanceof HTMLButtonElement) {
                continueBtn.disabled = false;
            }
        }
    }
}

function initDiagnosticsFlow() {
    const modeCards = bindSelectableCards({
        rootId: 'obd-modes',
        cardSelector: '.obd-mode-card',
        onSelect: (card) => {
            const mode = card.getAttribute('data-mode') || 'general';
            setSessionValue('obdMode', mode);
            ensureDiagnosticsSessionId();
            const continueBtn = document.getElementById('obd-continue-1');
            if (continueBtn instanceof HTMLButtonElement) {
                continueBtn.disabled = false;
            }
        },
    });

    const continueMode = document.getElementById('obd-continue-1');
    if (continueMode instanceof HTMLButtonElement) {
        continueMode.addEventListener('click', () => {
            const mode = getSessionState().obdMode;
            if (!mode) {
                return;
            }
            resetIdleTimer();
            ensureDiagnosticsSessionId();
            navigateTo('screen-obd-contact');
        });
    }

    const makeCards = bindSelectableCards({
        rootId: 'obd-makes',
        cardSelector: '.obd-make-card',
        onSelect: (card) => {
            const make = card.getAttribute('data-make');
            if (!make) {
                return;
            }
            setSessionValue('obdMake', make);
            ensureDiagnosticsSessionId();
            updateObdPreview(card);
            const continueBtn = document.getElementById('obd-continue-3');
            if (continueBtn instanceof HTMLButtonElement) {
                continueBtn.disabled = false;
            }
        },
    });

    const continueVehicle = document.getElementById('obd-continue-3');
    if (continueVehicle instanceof HTMLButtonElement) {
        continueVehicle.addEventListener('click', () => {
            const make = getSessionState().obdMake;
            if (!make) {
                return;
            }
            resetIdleTimer();
            navigateTo('screen-obd-prep');
        });
    }

    const continuePrep = document.getElementById('obd-continue-4');
    if (continuePrep instanceof HTMLButtonElement) {
        continuePrep.addEventListener('click', () => {
            resetIdleTimer();
            navigateTo('screen-obd-scan');
        });
    }

    const initialMode = getSessionState().obdMode;
    if (initialMode) {
        const initialCard = modeCards.find(card => (card.getAttribute('data-mode') || 'general') === initialMode);
        if (initialCard) {
            applySelectionState(modeCards, initialCard);
            const btn = document.getElementById('obd-continue-1');
            if (btn instanceof HTMLButtonElement) {
                btn.disabled = false;
            }
        }
    }

    const initialMake = getSessionState().obdMake;
    if (initialMake) {
        const initialCard = makeCards.find(card => card.getAttribute('data-make') === initialMake);
        if (initialCard) {
            applySelectionState(makeCards, initialCard);
            updateObdPreview(initialCard);
            const btn = document.getElementById('obd-continue-3');
            if (btn instanceof HTMLButtonElement) {
                btn.disabled = false;
            }
        }
    }
}

function updateObdPreview(card) {
    const preview = document.getElementById('obd-vehicle-preview');
    const caption = document.getElementById('obd-vehicle-caption');
    const image = document.getElementById('obd-vehicle-image');
    if (!preview) {
        return;
    }
    preview.classList.remove('hidden');
    const title = card.querySelector('.card-title');
    if (caption && title) {
        caption.textContent = title.textContent || '';
    }
    if (image) {
        const datasetImage = card.getAttribute('data-image');
        if (datasetImage) {
            image.src = datasetImage;
            image.alt = title?.textContent || 'Марка автомобиля';
        }
    }
}

function bindContactForm(options) {
    const input = document.getElementById(options.inputId);
    const button = document.getElementById(options.continueButtonId);
    const errorNode = options.errorId ? document.getElementById(options.errorId) : null;
    const hintNode = options.hintId ? document.getElementById(options.hintId) : null;
    if (!(input instanceof HTMLInputElement) || !(button instanceof HTMLButtonElement)) {
        return;
    }
    const defaultHint = hintNode?.textContent ?? '';
    let lastSentValue = null;

    const validateAndStore = () => {
        const result = validateContact(input.value);
        const canonicalValue = result.normalized ?? input.value.trim();
        if (result.valid) {
            button.disabled = false;
            setSessionValue(options.sessionKey, canonicalValue);
            if (canonicalValue && canonicalValue !== lastSentValue) {
                emitContactUpdated(options, result, canonicalValue);
                lastSentValue = canonicalValue;
            }
            if (errorNode) {
                errorNode.textContent = '';
            }
        } else {
            button.disabled = true;
            setSessionValue(options.sessionKey, null);
            if (errorNode) {
                errorNode.textContent = result.message ?? 'Введите контакт';
            }
        }
        updateContactHint(hintNode, defaultHint, result, result.valid ? canonicalValue : '');
        return result;
    };

    const storedValue = getSessionValue(options.sessionKey);
    if (typeof storedValue === 'string' && storedValue.trim()) {
        lastSentValue = storedValue.trim();
        input.value = formatContactValueForInput(storedValue);
        validateAndStore();
    } else {
        button.disabled = true;
    }

    input.addEventListener('input', () => {
        maybeFormatPhoneInput(input);
        validateAndStore();
    });

    button.addEventListener('click', () => {
        const result = validateAndStore();
        if (!result.valid) {
            input.focus();
            return;
        }
        if (options.onValid) {
            options.onValid(result);
        }
        resetIdleTimer();
        navigateTo(options.nextScreen);
    });
}

function initContactForms() {
    bindContactForm({
        inputId: 'thk-contact',
        errorId: 'thk-contact-error',
        hintId: 'thk-contact-hint',
        continueButtonId: 'thk-continue-2',
        sessionKey: 'contact.thickness',
        nextScreen: 'screen-thk-payment',
        onValid: () => ensureThicknessSessionId(),
    });

    bindContactForm({
        inputId: 'obd-contact',
        errorId: 'obd-contact-error',
        hintId: 'obd-contact-hint',
        continueButtonId: 'obd-continue-2',
        sessionKey: 'contact.diagnostics',
        nextScreen: 'screen-obd-vehicle',
        onValid: () => ensureDiagnosticsSessionId(),
    });
}

function initHomeButtons() {
    const backToHome = document.getElementById('back-to-home-1');
    if (backToHome) {
        backToHome.addEventListener('click', () => {
            clearSessionState();
            resetIdleTimer();
            navigateTo('screen-attract');
        });
    }
}

/**
 * Инициализирует пользовательский поток терминала и навешивает действия на UI.
 * @returns {void}
 */
export function initFlowController() {
    if (typeof document === 'undefined') {
        return;
    }
    initAttractScreen();
    initWelcomeScreen();
    initServiceSelection();
    initThicknessFlow();
    initDiagnosticsFlow();
    initContactForms();
    initHomeButtons();
}
