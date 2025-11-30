(() => {
    const SCREEN_LABELS = {
        'screen-attract': 'Attract',
        'screen-welcome': 'Welcome',
        'screen-services': 'Services',
        'screen-thk-intro': 'Толщиномер · ввод',
        'screen-thk-contact': 'Толщиномер · контакты',
        'screen-thk-payment': 'Толщиномер · оплата',
        'screen-thk-prep': 'Толщиномер · подготовка',
        'screen-thk-measure': 'Толщиномер · измерения',
        'screen-thk-done': 'Толщиномер · отчёт',
        'screen-obd-intro': 'Диагностика · ввод',
        'screen-obd-contact': 'Диагностика · контакты',
        'screen-obd-vehicle': 'Диагностика · марка',
        'screen-obd-prep': 'Диагностика · подготовка',
        'screen-obd-scan': 'Диагностика · сканирование',
        'screen-obd-paywall': 'Диагностика · оплата',
        'screen-obd-results': 'Диагностика · результаты',
        'screen-obd-done': 'Диагностика · отчёт'
    };

    const EVENT_CHANNELS = ['UI', 'BLE', 'OBD', 'PAYMENT', 'REPORT'];
    const SETTINGS_STORAGE_KEY = 'ui-monitor:data-source';
    const INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000;
    const AUTO_RETURN_DELAY_MS = 30 * 1000;

    const formatCurrency = (value) => new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        maximumFractionDigits: 0
    }).format(value);

    const randomBetween = (min, max) => Math.round(min + Math.random() * (max - min));

    const uuid = (prefix) => `${prefix}-${Date.now().toString(16)}-${Math.floor(Math.random() * 1e4)}`;

    const select = (id) => {
        if (!id) {
            return null;
        }
        const normalized = id.startsWith('#') ? id.slice(1) : id;
        return document.getElementById(normalized);
    };
    const selectAll = (selector, scope = document) => Array.from(scope.querySelectorAll(selector));

    const dom = {
        sections: selectAll('.screen'),
        backButtons: selectAll('[data-back]'),
        hero: select('screen-attract'),
        heroButtons: selectAll('[data-hero-action]'),
        welcomeAgree: select('welcome-agree'),
        welcomeContinue: select('welcome-continue'),
        serviceCards: selectAll('.service-card'),
        serviceContinue: select('service-continue'),
        thk: {
            cards: selectAll('.thk-type-card'),
            continueIntro: select('thk-continue-1'),
            continueContact: select('thk-continue-2'),
            continuePrep: select('thk-continue-3'),
            contactInput: select('thk-contact'),
            contactError: select('thk-contact-error'),
            amount: select('thk-amount'),
            status: select('thk-status'),
            start: select('thk-start'),
            grid: select('thk-points-grid'),
            progress: select('thk-progress'),
            sessionCard: select('thk-session'),
            devMark: select('thk-dev-mark'),
            finish: select('thk-finish'),
            summaryTitle: select('thk-summary-title'),
            summaryPrice: select('thk-summary-price'),
            summaryMeta: select('thk-summary-meta'),
            summaryNote: select('thk-summary-note'),
            contactSummary: select('thk-selection-summary'),
            reportStatus: select('report-status-thk'),
            credits: select('credits-thk')
        },
        obd: {
            modeCards: selectAll('.obd-mode-card'),
            modeContinue: select('obd-continue-1'),
            contactInput: select('obd-contact'),
            contactError: select('obd-contact-error'),
            contactContinue: select('obd-continue-2'),
            makeCards: selectAll('.obd-make-card'),
            makeContinue: select('obd-continue-3'),
            prepContinue: select('obd-continue-4'),
            status: select('obd-status'),
            start: select('obd-start'),
            finish: select('obd-finish'),
            resultsLead: select('obd-results-lead'),
            resultsWrapper: select('obd-results-wrapper'),
            resultsList: select('obd-results-list'),
            statusSummary: select('obd-status-summary'),
            liveData: select('obd-live-data'),
            summaryGrid: select('obd-summary-grid'),
            connectionMeta: selectAll('[data-obd-connection-meta]'),
            paywallAmount: select('obd-paywall-amount'),
            paywallInfo: select('obd-payment-info'),
            paywallLead: select('obd-paywall-lead'),
            paywallSelfCheck: select('obd-self-check'),
            selfCheckButton: select('obd-self-check-rerun'),
            liveRefresh: select('obd-live-refresh'),
            clearButton: select('obd-clear'),
            contactSummary: select('obd-selection-summary'),
            summaryMake: select('obd-summary-make'),
            summaryMode: select('obd-summary-mode'),
            summaryMeta: select('obd-summary-meta'),
            summaryNote: select('obd-summary-note'),
            paywallMeta: select('obd-payment-info'),
            reportStatus: select('report-status-obd'),
            credits: select('credits-obd'),
            portSelect: select('obd-port'),
            refreshPorts: select('obd-refresh'),
            portStatus: select('obd-port-status'),
            portLastRefresh: select('obd-port-last-refresh')
        },
        admin: {
            drawerToggle: select('admin-bridge-toggle'),
            drawer: select('admin-bridge-drawer'),
            drawerClose: select('admin-bridge-close'),
            screenLabel: select('admin-screen-label'),
            dataSource: select('admin-data-source'),
            dataSourceMeta: select('admin-data-source-meta'),
            devFlag: select('admin-dev-flag'),
            sessionThk: select('admin-session-thk'),
            sessionObd: select('admin-session-obd'),
            deviceThkStatus: select('admin-device-thickness-status'),
            deviceThkDetail: select('admin-device-thickness-detail'),
            deviceObdStatus: select('admin-device-obd-status'),
            deviceObdDetail: select('admin-device-obd-detail'),
            paymentThkStatus: select('admin-payment-thk-status'),
            paymentThkMeta: select('admin-payment-thk-meta'),
            paymentObdStatus: select('admin-payment-obd-status'),
            paymentObdMeta: select('admin-payment-obd-meta'),
            reportThkStatus: select('admin-report-thk-status'),
            reportThkMeta: select('admin-report-thk-meta'),
            reportObdStatus: select('admin-report-obd-status'),
            reportObdMeta: select('admin-report-obd-meta'),
            timelineReset: select('admin-timeline-reset'),
            timelineList: select('admin-session-steps'),
            eventsInfo: select('admin-events-info'),
            eventsList: select('admin-events-list'),
            eventsEmpty: select('admin-events-empty'),
            eventsFilters: select('admin-events-filters'),
            eventsClear: select('admin-events-clear'),
            eventsReset: select('admin-events-filter-reset')
        },
        preview: {
            modal: select('report-preview-modal'),
            close: select('report-preview-close'),
            frame: select('report-preview-frame'),
            openButtons: selectAll('[data-preview-trigger]')
        },
        settings: {
            button: select('settings-button'),
            modal: select('settings-modal'),
            close: select('settings-close'),
            save: select('settings-save'),
            reset: select('settings-reset'),
            source: select('settings-source'),
            supabaseFields: select('settings-supabase-fields'),
            supabaseUrl: select('settings-supabase-url'),
            supabaseKey: select('settings-supabase-anon-key'),
            status: select('settings-status')
        }
    };

    function updateDataSourceSummary() {
        const isSupabase = state.settings?.source === 'supabase';
        if (dom.admin.dataSource) {
            dom.admin.dataSource.textContent = isSupabase ? 'Supabase (read-only)' : 'Локальный агент';
        }
        if (dom.admin.dataSourceMeta) {
            dom.admin.dataSourceMeta.textContent = isSupabase
                ? 'нужен публичный ANON KEY'
                : 'режим по умолчанию';
        }
    }

    function hydrateSettings() {
        try {
            const raw = localStorage.getItem(SETTINGS_STORAGE_KEY);
            if (raw) {
                const parsed = JSON.parse(raw);
                Object.assign(state.settings, parsed);
            }
        } catch (error) {
            console.error('settings load failed', error);
        }
        updateDataSourceSummary();
    }

    function persistSettings() {
        try {
            localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(state.settings));
        } catch (error) {
            console.error('settings save failed', error);
        }
    }

    const state = {
        activeScreen: 'screen-attract',
        service: null,
        devMode: true,
        navHistory: ['screen-attract'],
        thk: {
            type: null,
            price: 0,
            contact: '',
            sessionId: '',
            measurementQueue: [],
            measurementTimer: null,
            results: []
        },
        obd: {
            mode: null,
            make: null,
            contact: '',
            sessionId: '',
            scanTimer: null,
            scanComplete: false,
            dtc: [],
            live: []
        },
        admin: {
            events: [],
            filters: new Map(EVENT_CHANNELS.map((channel) => [channel, false]))
        },
        settings: {
            source: 'agent',
            supabaseUrl: '',
            supabaseAnonKey: ''
        }
    };

    let inactivityTimerId = null;
    const autoReturnState = {
        timerId: null,
        tickerId: null,
        targetElement: null,
        context: null,
        baseText: ''
    };

    const thicknessTemplate = Array.from({ length: 48 }, (_, idx) => ({
        id: `PT-${idx + 1}`,
        label: `Зона ${String(idx + 1).padStart(2, '0')}`
    }));

    const obdSampleDtc = [
        { code: 'P0420', description: 'Низкая эффективность катализатора', severity: 'warning' },
        { code: 'P0301', description: 'Пропуски воспламенения цилиндра 1', severity: 'critical' },
        { code: 'P0171', description: 'Обеднённая смесь (банк 1)', severity: 'info' },
        { code: 'C1234', description: 'Датчик ABS переднего правого колеса', severity: 'warning' }
    ];

    const obdLiveTemplate = [
        { label: 'Напряжение бортсети', value: '12.4 В' },
        { label: 'Обороты холостого хода', value: '780 об/мин' },
        { label: 'Температура охлаждайки', value: '86 °C' },
        { label: 'Нагрузка двигателя', value: '32 %' }
    ];

    const TERMS_COPY = `
        <p><strong>1. Назначение услуги.</strong> Киоск предоставляет две самостоятельные услуги: толщиномер ЛКП и OBD‑II диагностику. Каждая услуга выполняется только после явного выбора клиента и подтверждения согласия.</p>
        <p><strong>2. Персональные данные.</strong> Мы запрашиваем только телефон или email для доставки отчёта. Данные хранятся до 30 дней, после чего удаляются автоматически или сразу по запросу.</p>
        <p><strong>3. Платёжная политика.</strong> Оплата производится через QR‑код. Средства списываются провайдером платежей, терминал получает только статус подтверждения.</p>
        <p><strong>4. Устройства.</strong> Толщиномер и OBD‑адаптер выдаются автоматически после оплаты/выбора услуги. Клиент обязуется вернуть оборудование после завершения сессии.</p>
        <p><strong>5. Ответственность.</strong> Терминал проводит диагностику без вмешательства персонала. Итоговый отчёт носит информационный характер и не заменяет полноценный сервисный визит.</p>
        <p>Нажимая «Понятно», вы подтверждаете ознакомление с условиями и разрешаете обработку введённых контактов для отправки отчёта.</p>
    `;

    function setDevMode(enabled) {
        state.devMode = enabled;
        document.body.dataset.devMode = enabled ? 'on' : 'off';
        dom.admin.devFlag.textContent = enabled ? 'Вкл' : 'Выкл';
        selectAll('[data-dev-only]').forEach((element) => {
            if (enabled) {
                element.removeAttribute('hidden');
            } else {
                element.setAttribute('hidden', 'true');
            }
        });
    }

    const randomId = () => {
        if (globalThis.crypto?.randomUUID) {
            return globalThis.crypto.randomUUID();
        }
        return `evt-${Date.now().toString(16)}-${Math.floor(Math.random() * 1e6)}`;
    };

    function buildReportLink(kind) {
        const sessionId = kind === 'thk' ? state.thk.sessionId : state.obd.sessionId;
        return `https://kiosk.local/reports/${kind}/${sessionId || 'preview'}`;
    }

    function logEvent(channel, title, description, extra = {}) {
        const entry = {
            id: randomId(),
            channel,
            title,
            description,
            timestamp: new Date(),
            extra
        };
        state.admin.events.unshift(entry);
        while (state.admin.events.length > 50) {
            state.admin.events.pop();
        }
        renderEvents();
    }

    function renderEvents() {
        const list = dom.admin.eventsList;
        if (!list) {
            return;
        }
        list.innerHTML = '';
        const filters = Array.from(state.admin.filters.entries())
            .filter(([, isOn]) => isOn)
            .map(([channel]) => channel);
        const visible = state.admin.events.filter((event) => {
            if (!filters.length) {
                return true;
            }
            return filters.includes(event.channel);
        });
        visible.forEach((event) => {
            const item = document.createElement('li');
            item.className = 'admin-event';
            item.innerHTML = `
        <div class="admin-event-header">
          <b>${event.channel}</b>
          <span>${event.timestamp.toLocaleTimeString('ru-RU')}</span>
        </div>
        <div class="admin-event-body">
          <p>${event.title}</p>
          <div class="admin-event-desc">${event.description}</div>
        </div>
      `;
            list.appendChild(item);
        });
        dom.admin.eventsEmpty?.toggleAttribute('hidden', visible.length > 0);
        dom.admin.eventsInfo.textContent = filters.length
            ? 'Применены фильтры событий'
            : 'Показаны все события';
    }

    function buildEventFilters() {
        const container = dom.admin.eventsFilters;
        if (!container) {
            return;
        }
        container.innerHTML = '';
        state.admin.filters.forEach((checked, channel) => {
            const label = document.createElement('label');
            label.className = 'chip-input';
            label.innerHTML = `
        <input type="checkbox" ${checked ? 'checked' : ''} aria-label="${channel}" />
        <span>${channel}</span>
      `;
            const input = label.querySelector('input');
            input?.addEventListener('change', (event) => {
                state.admin.filters.set(channel, event.target.checked);
                renderEvents();
            });
            container.appendChild(label);
        });
    }

    function updateAdminScreenLabel() {
        const label = SCREEN_LABELS[state.activeScreen] || state.activeScreen;
        if (dom.admin.screenLabel) {
            dom.admin.screenLabel.textContent = label;
        }
    }

    function showScreen(id, reason = '') {
        if (state.activeScreen === id) {
            return;
        }
        const target = select(`#${id}`);
        if (!target) {
            return;
        }
        dom.sections.forEach((section) => {
            section.classList.toggle('active', section === target);
        });
        state.activeScreen = id;
        state.navHistory.push(id);
        updateAdminScreenLabel();
        markTimeline(id);
        logEvent('UI', `Экран: ${SCREEN_LABELS[id] || id}`, reason || 'навигация');
    }

    function goBack() {
        if (state.navHistory.length < 2) {
            return;
        }
        state.navHistory.pop();
        const previous = state.navHistory[state.navHistory.length - 1];
        dom.sections.forEach((section) => {
            section.classList.toggle('active', section.id === previous);
        });
        state.activeScreen = previous;
        updateAdminScreenLabel();
        markTimeline(previous);
        logEvent('UI', `Назад: ${SCREEN_LABELS[previous] || previous}`, 'Кнопка «Назад»');
    }

    function markTimeline(stepId) {
        const items = selectAll('#admin-session-steps [data-step]');
        let activeFound = false;
        items.forEach((item) => {
            const target = item.getAttribute('data-step');
            if (!activeFound && target === stepId) {
                item.classList.add('active');
                item.classList.remove('done');
                activeFound = true;
                return;
            }
            if (!activeFound) {
                item.classList.add('done');
                item.classList.remove('active');
            } else {
                item.classList.remove('done');
                item.classList.remove('active');
            }
        });
    }

    function resetServicesSelection() {
        dom.serviceCards.forEach((card) => {
            card.classList.remove('active');
            card.setAttribute('aria-pressed', 'false');
        });
        if (dom.serviceContinue) {
            dom.serviceContinue.dataset.targetScreen = '';
            dom.serviceContinue.setAttribute('disabled', 'true');
        }
    }

    function resetThicknessState() {
        Object.assign(state.thk, {
            type: null,
            price: 0,
            contact: '',
            sessionId: '',
            measurementQueue: [],
            measurementTimer: null,
            results: []
        });
        dom.thk.cards.forEach((card) => {
            card.classList.remove('active');
            card.setAttribute('aria-pressed', 'false');
        });
        dom.thk.continueIntro?.setAttribute('disabled', 'true');
        dom.thk.continueContact?.setAttribute('disabled', 'true');
        if (dom.thk.contactInput) {
            dom.thk.contactInput.value = '';
        }
        if (dom.thk.contactError) {
            dom.thk.contactError.textContent = '';
        }
        dom.thk.status.textContent = 'Толщиномер: нет соединения';
        dom.thk.status.classList.remove('badge-ok', 'badge-warn');
        dom.thk.status.classList.add('badge-danger');
        dom.thk.sessionCard?.classList.add('hidden');
        dom.thk.start?.setAttribute('disabled', 'true');
        dom.thk.finish?.setAttribute('disabled', 'true');
        dom.thk.amount.textContent = '';
        dom.thk.reportStatus.textContent = '';
        setCreditsMessage(dom.thk.credits, '');
        updateThkSummary();
        resetThicknessGrid();
    }

    function resetObdState() {
        Object.assign(state.obd, {
            mode: null,
            make: null,
            contact: '',
            sessionId: '',
            scanTimer: null,
            scanComplete: false,
            dtc: [],
            live: []
        });
        dom.obd.modeCards.forEach((card) => {
            card.classList.remove('active');
            card.setAttribute('aria-pressed', 'false');
        });
        dom.obd.makeCards.forEach((card) => {
            card.classList.remove('active');
            card.setAttribute('aria-pressed', 'false');
        });
        dom.obd.modeContinue?.setAttribute('disabled', 'true');
        dom.obd.makeContinue?.setAttribute('disabled', 'true');
        dom.obd.contactContinue?.setAttribute('disabled', 'true');
        if (dom.obd.contactInput) {
            dom.obd.contactInput.value = '';
        }
        if (dom.obd.contactError) {
            dom.obd.contactError.textContent = '';
        }
        dom.obd.summaryMake.textContent = 'Марка не выбрана';
        dom.obd.summaryMode.textContent = 'Выберите режим проверки';
        dom.obd.summaryMeta.innerHTML = '<li>После выбора подберём схему подключения и проверок.</li>';
        dom.obd.summaryNote.textContent = 'Режим «OBD‑II» раскрывает стандартизированные команды, «Общая» — быстрый обзор систем.';
        dom.obd.contactSummary?.classList.add('selection-summary--pending');
        dom.obd.status.textContent = 'OBD‑адаптер: нет соединения';
        dom.obd.status.classList.remove('badge-ok', 'badge-warn');
        dom.obd.status.classList.add('badge-danger');
        dom.obd.paywallAmount.textContent = '';
        dom.obd.paywallInfo.textContent = '';
        dom.obd.paywallSelfCheck.textContent = '';
        dom.obd.resultsLead.textContent = 'Здесь будут коды DTC и статусы при подключённом устройстве.';
        dom.obd.resultsList.textContent = 'Отсутствуют данные без устройства.';
        dom.obd.liveData.innerHTML = '';
        dom.obd.summaryGrid?.classList.add('hidden');
        dom.obd.statusSummary.textContent = '';
        dom.obd.clearButton?.setAttribute('disabled', 'true');
        dom.obd.start?.setAttribute('disabled', 'true');
        dom.obd.reportStatus.textContent = '';
        setCreditsMessage(dom.obd.credits, '');
        if (dom.obd.portSelect) {
            dom.obd.portSelect.innerHTML = '<option value="">Выберите порт…</option>';
        }
        if (dom.obd.portStatus) {
            dom.obd.portStatus.textContent = 'Подключите адаптер и обновите список.';
        }
        if (dom.obd.portLastRefresh) {
            dom.obd.portLastRefresh.textContent = '';
        }
    }

    function updateHomeScreen(reason) {
        dom.sections.forEach((section) => {
            section.classList.toggle('active', section.id === 'screen-attract');
        });
        state.activeScreen = 'screen-attract';
        state.navHistory = ['screen-attract'];
        updateAdminScreenLabel();
        markTimeline('screen-attract');
        logEvent('UI', 'Возврат на главный экран', reason);
    }

    function resetAdminWidgets() {
        updateAdminSessions();
        updateAdminDevice('thk', 'offline', 'Ожидаем действия клиента.');
        updateAdminDevice('obd', 'offline', 'Ожидаем действия клиента.');
        updateAdminPayment('thk', 'idle', 'Нет активных intent.');
        updateAdminPayment('obd', 'idle', 'Нет активных intent.');
        updateAdminReport('thk', 'idle', 'Отчёт не сформирован.');
        updateAdminReport('obd', 'idle', 'Отчёт не сформирован.');
    }

    function setCreditsMessage(element, text) {
        if (!element) {
            return;
        }
        element.dataset.baseText = text || '';
        element.textContent = text || '';
    }

    function clearAutoReturnCountdown() {
        if (autoReturnState.timerId) {
            clearTimeout(autoReturnState.timerId);
            autoReturnState.timerId = null;
        }
        if (autoReturnState.tickerId) {
            clearInterval(autoReturnState.tickerId);
            autoReturnState.tickerId = null;
        }
        if (autoReturnState.targetElement) {
            const hint = autoReturnState.targetElement.querySelector('.auto-reset-hint');
            hint?.remove();
        }
        autoReturnState.targetElement = null;
        autoReturnState.context = null;
        autoReturnState.baseText = '';
    }

    function renderAutoReturnHint(secondsLeft) {
        const element = autoReturnState.targetElement;
        if (!element) {
            return;
        }
        let hint = element.querySelector('.auto-reset-hint');
        if (!hint) {
            hint = document.createElement('span');
            hint.className = 'auto-reset-hint';
            element.appendChild(hint);
        }
        hint.textContent = `Авто-возврат на главный экран через ${secondsLeft} с`;
    }

    function startAutoReturnCountdown(context) {
        clearAutoReturnCountdown();
        const targetElement = context === 'thk' ? dom.thk.credits : dom.obd.credits;
        autoReturnState.targetElement = targetElement;
        autoReturnState.context = context;
        autoReturnState.baseText = targetElement?.dataset?.baseText || targetElement?.textContent || '';
        const deadline = Date.now() + AUTO_RETURN_DELAY_MS;
        const update = () => {
            const secondsLeft = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
            renderAutoReturnHint(secondsLeft);
        };
        update();
        autoReturnState.tickerId = setInterval(update, 1000);
        autoReturnState.timerId = setTimeout(() => {
            clearAutoReturnCountdown();
            logEvent('UI', 'Авто-возврат выполнен', context === 'thk' ? 'Толщинометрия' : 'Диагностика');
            resetExperience('Авто-сброс после завершения услуги');
        }, AUTO_RETURN_DELAY_MS);
        logEvent('UI', 'Авто-возврат запущен', context === 'thk' ? 'Толщинометрия' : 'Диагностика');
    }

    function resetExperience(reason) {
        clearAutoReturnCountdown();
        state.service = null;
        resetServicesSelection();
        resetThicknessState();
        resetObdState();
        resetAdminWidgets();
        updateHomeScreen(reason);
        resetInactivityTimer();
    }

    function resetInactivityTimer() {
        if (inactivityTimerId) {
            clearTimeout(inactivityTimerId);
        }
        inactivityTimerId = setTimeout(() => {
            logEvent('UI', 'Авто-сброс активности', 'Нет касаний 5 минут');
            resetExperience('Авто-сброс: нет активности');
        }, INACTIVITY_TIMEOUT_MS);
    }

    function setupInactivityWatchers() {
        const handler = () => resetInactivityTimer();
        ['pointerdown', 'touchstart', 'keydown'].forEach((eventName) => {
            document.addEventListener(eventName, handler, { passive: true });
        });
    }

    function validateContact(value) {
        const trimmed = value.trim();
        if (!trimmed.length) {
            return { ok: false, error: 'Укажите телефон или email' };
        }
        const digits = trimmed.replace(/\D+/g, '');
        if (digits.length >= 10) {
            return { ok: true, normalized: `+7 ${digits.slice(-10)}` };
        }
        if (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(trimmed)) {
            return { ok: true, normalized: trimmed };
        }
        return { ok: false, error: 'Формат не распознан' };
    }

    function resetThicknessGrid() {
        if (!dom.thk.grid) {
            return;
        }
        dom.thk.grid.innerHTML = '';
        state.thk.measurementQueue = thicknessTemplate.map((template) => ({
            ...template,
            status: 'pending',
            value: null,
            element: null
        }));
        state.thk.measurementQueue.forEach((point) => {
            const cell = document.createElement('div');
            cell.className = 'thk-point thk-point--pending';
            cell.innerHTML = `
        <span>${point.label}</span>
        <b>—</b>
      `;
            dom.thk.grid.appendChild(cell);
            point.element = cell;
        });
        dom.thk.progress.textContent = '0 / ' + state.thk.measurementQueue.length;
        dom.thk.sessionCard?.classList.add('hidden');
        dom.thk.start?.setAttribute('disabled', 'true');
        dom.thk.finish?.setAttribute('disabled', 'true');
    }

    function setupHero() {
        dom.heroButtons.forEach((button) => {
            button.addEventListener('click', () => {
                const action = button.dataset.heroAction;
                const target = action === 'learn' ? 'screen-services' : 'screen-welcome';
                showScreen(target, 'hero action');
            });
        });
        dom.hero?.addEventListener('click', (event) => {
            if (event.target.closest('.hero-actions')) {
                return;
            }
            showScreen('screen-welcome', 'tap-to-start');
        });
    }

    function setupSettingsPanel() {
        const { button, modal, close, save, reset, source, supabaseFields, supabaseUrl, supabaseKey, status } = dom.settings;

        if (!modal || !source) {
            return;
        }

        const syncFields = () => {
            source.value = state.settings.source;
            if (supabaseUrl) {
                supabaseUrl.value = state.settings.supabaseUrl || '';
            }
            if (supabaseKey) {
                supabaseKey.value = state.settings.supabaseAnonKey || '';
            }
            if (supabaseFields) {
                supabaseFields.style.display = state.settings.source === 'supabase' ? 'block' : 'none';
            }
            if (status) {
                status.textContent = '';
            }
        };

        const open = () => {
            syncFields();
            modal.classList.remove('hidden');
            logEvent('UI', 'Открыты настройки источника данных', 'DEV панель');
        };

        const closeModal = () => {
            modal.classList.add('hidden');
        };

        const displayStatus = (message) => {
            if (status) {
                status.textContent = message;
            }
        };

        source.addEventListener('change', () => {
            if (supabaseFields) {
                const supabaseSelected = source.value === 'supabase';
                supabaseFields.style.display = supabaseSelected ? 'block' : 'none';
            }
        });

        save?.addEventListener('click', () => {
            const nextSource = source.value || 'agent';
            if (nextSource === 'supabase') {
                const url = supabaseUrl?.value.trim();
                const anonKey = supabaseKey?.value.trim();
                if (!url || !anonKey) {
                    displayStatus('Укажите URL и ANON KEY от Supabase.');
                    return;
                }
                state.settings.supabaseUrl = url;
                state.settings.supabaseAnonKey = anonKey;
            } else {
                state.settings.supabaseUrl = '';
                state.settings.supabaseAnonKey = '';
            }
            state.settings.source = nextSource;
            persistSettings();
            updateDataSourceSummary();
            displayStatus('Настройки сохранены.');
            logEvent('UI', 'Источник данных обновлён', nextSource);
            setTimeout(() => closeModal(), 400);
        });

        reset?.addEventListener('click', () => {
            state.settings.source = 'agent';
            state.settings.supabaseUrl = '';
            state.settings.supabaseAnonKey = '';
            persistSettings();
            updateDataSourceSummary();
            syncFields();
            displayStatus('Настройки сброшены к значениям по умолчанию.');
            logEvent('UI', 'Настройки сброшены', 'Возврат к локальному агенту');
        });

        button?.addEventListener('click', open);
        close?.addEventListener('click', closeModal);
        modal.addEventListener('click', (event) => {
            if (event.target === modal) {
                closeModal();
            }
        });
    }

    function setupWelcome() {
        const checkbox = select('welcome-agree');
        const continueBtn = select('welcome-continue');
        checkbox?.addEventListener('change', () => {
            if (checkbox.checked) {
                continueBtn?.removeAttribute('disabled');
            } else {
                continueBtn?.setAttribute('disabled', 'true');
            }
        });
        continueBtn?.addEventListener('click', () => {
            showScreen('screen-services', 'согласие принято');
        });
    }

    function setupTermsModal() {
        const modal = select('terms-modal');
        const openButton = select('open-terms-link');
        const closeButton = select('terms-close');
        const okButton = select('terms-ok');
        const content = select('terms-content');

        const open = () => {
            if (!modal) {
                return;
            }
            content.innerHTML = TERMS_COPY;
            modal.classList.remove('hidden');
            logEvent('UI', 'Показаны условия обслуживания', 'Модальное окно «Условия»');
        };

        const close = (reason = 'Закрытие модального окна') => {
            modal?.classList.add('hidden');
            logEvent('UI', 'Окно «Условия» закрыто', reason);
        };

        openButton?.addEventListener('click', () => {
            open();
        });
        closeButton?.addEventListener('click', () => close('Кнопка «Закрыть»'));
        okButton?.addEventListener('click', () => close('Кнопка «Понятно»'));
        modal?.addEventListener('click', (event) => {
            if (event.target === modal) {
                close('Клик по фону');
            }
        });
    }

    function setupBackButtons() {
        dom.backButtons.forEach((button) => {
            button.addEventListener('click', (event) => {
                event.stopPropagation();
                goBack();
            });
        });
    }

    function highlightCard(collection, target) {
        collection.forEach((card) => {
            const pressed = card === target;
            card.classList.toggle('active', pressed);
            card.setAttribute('aria-pressed', pressed ? 'true' : 'false');
        });
    }

    function setupServices() {
        dom.serviceCards.forEach((card) => {
            card.addEventListener('click', () => {
                highlightCard(dom.serviceCards, card);
                dom.serviceContinue?.removeAttribute('disabled');
                state.service = card.dataset.service === 'diagnostics' ? 'diagnostics' : 'thickness';
                const autoAdvance = card.dataset.autoAdvance === 'true';
                if (autoAdvance) {
                    const targetScreen = card.dataset.targetScreen;
                    if (targetScreen) {
                        dom.serviceContinue.dataset.targetScreen = targetScreen;
                    }
                    dom.serviceContinue?.click();
                }
            });
        });
        dom.serviceContinue?.addEventListener('click', () => {
            const target = dom.serviceContinue?.dataset.targetScreen
                || (state.service === 'diagnostics' ? 'screen-obd-intro' : 'screen-thk-intro');
            showScreen(target, 'выбор услуги');
        });
    }

    function updateThkSummary() {
        if (!dom.thk.summaryTitle) {
            return;
        }
        if (!state.thk.type) {
            dom.thk.summaryTitle.textContent = 'Тип кузова пока не выбран';
            dom.thk.summaryPrice.textContent = '—';
            dom.thk.summaryMeta.innerHTML = '<li>Выберите карточку, чтобы увидеть подсказки.</li>';
            dom.thk.summaryNote.textContent = 'После выбора подсветим слот выдачи.';
            dom.thk.contactSummary?.classList.add('selection-summary--pending');
            return;
        }
        dom.thk.contactSummary?.classList.remove('selection-summary--pending');
        const titleMap = {
            sedan: 'Седан / лифтбек',
            hatchback: 'Хэтчбек',
            minivan: 'Минивэн',
            suv: 'SUV'
        };
        dom.thk.summaryTitle.textContent = titleMap[state.thk.type] || 'Выбран тип кузова';
        dom.thk.summaryPrice.textContent = formatCurrency(state.thk.price);
        dom.thk.summaryMeta.innerHTML = `
      <li>Среднее время: ${state.thk.price >= 450 ? '12' : '8'} минут.</li>
      <li>Слот выдачи №${state.thk.type === 'minivan' ? '2' : '1'} будет открыт автоматически.</li>
    `;
        dom.thk.summaryNote.textContent = 'Статус покажем после оплаты.';
    }

    function setupThicknessFlow() {
        dom.thk.cards.forEach((card) => {
            card.addEventListener('click', () => {
                highlightCard(dom.thk.cards, card);
                dom.thk.continueIntro?.removeAttribute('disabled');
                state.thk.type = card.dataset.type || 'sedan';
                state.thk.price = Number(card.dataset.price) || 350;
                updateThkSummary();
            });
        });

        dom.thk.continueIntro?.addEventListener('click', () => {
            showScreen('screen-thk-contact', 'thk type ok');
        });

        dom.thk.contactInput?.addEventListener('input', () => {
            const value = dom.thk.contactInput.value;
            const { ok, error, normalized } = validateContact(value);
            if (ok) {
                dom.thk.continueContact?.removeAttribute('disabled');
                dom.thk.contactError.textContent = '';
                state.thk.contact = normalized || value.trim();
            } else {
                dom.thk.continueContact?.setAttribute('disabled', 'true');
                dom.thk.contactError.textContent = error;
            }
        });

        dom.thk.continueContact?.addEventListener('click', () => {
            showScreen('screen-thk-payment', 'thk контакт');
            startPaymentSimulation('thickness');
        });

        dom.thk.continuePrep?.addEventListener('click', () => {
            showScreen('screen-thk-measure', 'thk подготовка завершена');
            prepareThicknessMeasurement();
        });

        dom.thk.start?.addEventListener('click', () => {
            dom.thk.start.setAttribute('disabled', 'true');
            dom.thk.sessionCard?.classList.remove('hidden');
            runThicknessMeasurements();
        });

        dom.thk.finish?.addEventListener('click', () => {
            finalizeThicknessReport();
        });

        dom.thk.devMark?.addEventListener('click', () => {
            completeSingleThicknessPoint();
        });
    }

    function setupHomeButtons() {
        ['back-to-home-1', 'back-to-home-2'].forEach((id) => {
            const button = select(id);
            button?.addEventListener('click', () => {
                resetExperience('Кнопка «На главный экран»');
            });
        });
    }

    function prepareThicknessMeasurement() {
        resetThicknessGrid();
        dom.thk.status.textContent = 'Толщиномер: подключаем...';
        dom.thk.status.classList.remove('badge-danger');
        dom.thk.status.classList.add('badge-warn');
        setTimeout(() => {
            dom.thk.status.textContent = 'Толщиномер: готов к работе';
            dom.thk.status.classList.remove('badge-warn');
            dom.thk.status.classList.add('badge-ok');
            dom.thk.start?.removeAttribute('disabled');
            state.thk.sessionId = state.thk.sessionId || uuid('THK');
            updateAdminSessions();
            updateAdminDevice('thk', 'online', 'BLE связь установлена.');
            logEvent('BLE', 'Толщиномер подключён', 'Готов к измерениям');
        }, 1200);
    }

    function runThicknessMeasurements() {
        if (!state.thk.measurementQueue.length) {
            return;
        }
        const queue = state.thk.measurementQueue;
        let index = 0;
        const total = queue.length;

        const iterate = () => {
            if (index >= total) {
                dom.thk.finish?.removeAttribute('disabled');
                logEvent('BLE', 'Толщинометрия завершена', 'Все точки заполнены');
                return;
            }
            const point = queue[index];
            point.status = 'measuring';
            point.element?.classList.remove('thk-point--pending');
            point.element?.classList.add('thk-point--active');
            point.element?.querySelector('b').textContent = '...';
            const duration = 600 + Math.random() * 500;
            setTimeout(() => {
                const value = randomBetween(90, 240);
                point.value = value;
                point.status = 'done';
                point.element?.classList.remove('thk-point--active');
                point.element?.classList.add(value > 190 ? 'thk-point--warn' : 'thk-point--done');
                point.element?.querySelector('b').textContent = `${value} µm`;
                dom.thk.progress.textContent = `${index + 1} / ${total}`;
                index += 1;
                iterate();
            }, duration);
        };

        iterate();
    }

    function completeSingleThicknessPoint() {
        const next = state.thk.measurementQueue.find((point) => point.status === 'pending');
        if (!next) {
            return;
        }
        const idx = state.thk.measurementQueue.indexOf(next);
        const point = state.thk.measurementQueue[idx];
        point.status = 'done';
        const value = randomBetween(100, 210);
        point.value = value;
        point.element?.classList.add(value > 190 ? 'thk-point--warn' : 'thk-point--done');
        point.element?.querySelector('b').textContent = `${value} µm`;
        const completed = state.thk.measurementQueue.filter((p) => p.status === 'done').length;
        dom.thk.progress.textContent = `${completed} / ${state.thk.measurementQueue.length}`;
        if (completed === state.thk.measurementQueue.length) {
            dom.thk.finish?.removeAttribute('disabled');
        }
    }

    function finalizeThicknessReport() {
        showScreen('screen-thk-done', 'Толщинометрия завершена');
        updateAdminReport('thk', 'ready', 'Отчёт сохранён и готов к отправке.');
        const summary = `Отчёт #${state.thk.sessionId || uuid('THK')} · получатель: ${state.thk.contact || '—'}`;
        dom.thk.reportStatus.textContent = summary;
        setCreditsMessage(dom.thk.credits, 'Имитация DEV · PDF по ссылке предпросмотра.');
        logEvent('REPORT', 'Толщинометрия завершена', summary);
        startAutoReturnCountdown('thk');
    }

    function setupObdFlow() {
        dom.obd.modeCards.forEach((card) => {
            card.addEventListener('click', () => {
                highlightCard(dom.obd.modeCards, card);
                dom.obd.modeContinue?.removeAttribute('disabled');
                state.obd.mode = card.dataset.mode || 'general';
                updateObdSummary();
            });
        });

        dom.obd.modeContinue?.addEventListener('click', () => {
            showScreen('screen-obd-contact', 'Выбран режим OBD');
        });

        dom.obd.contactInput?.addEventListener('input', () => {
            const value = dom.obd.contactInput.value;
            const { ok, error, normalized } = validateContact(value);
            if (ok) {
                dom.obd.contactContinue?.removeAttribute('disabled');
                dom.obd.contactError.textContent = '';
                state.obd.contact = normalized || value.trim();
            } else {
                dom.obd.contactContinue?.setAttribute('disabled', 'true');
                dom.obd.contactError.textContent = error;
            }
        });

        dom.obd.contactContinue?.addEventListener('click', () => {
            showScreen('screen-obd-vehicle', 'Контакт подтверждён');
        });

        dom.obd.makeCards.forEach((card) => {
            card.addEventListener('click', () => {
                highlightCard(dom.obd.makeCards, card);
                dom.obd.makeContinue?.removeAttribute('disabled');
                state.obd.make = card.dataset.make || 'Toyota';
                updateObdSummary();
            });
        });

        dom.obd.makeContinue?.addEventListener('click', () => {
            showScreen('screen-obd-prep', 'Марка выбрана');
        });

        dom.obd.prepContinue?.addEventListener('click', () => {
            showScreen('screen-obd-scan', 'Переход к сканированию');
            prepareObdScan();
        });

        dom.obd.start?.addEventListener('click', () => {
            startObdScan();
        });

        dom.obd.liveRefresh?.addEventListener('click', () => {
            refreshObdLiveData();
        });

        dom.obd.selfCheckButton?.addEventListener('click', () => {
            runObdSelfCheck();
        });

        dom.obd.clearButton?.addEventListener('click', () => {
            clearObdCodes();
        });

        dom.obd.finish?.addEventListener('click', () => {
            showScreen('screen-obd-done', 'Диагностика завершена');
            const summary = `Отчёт #${state.obd.sessionId || uuid('OBD')} · получатель: ${state.obd.contact || '—'}`;
            dom.obd.reportStatus.textContent = summary;
            setCreditsMessage(dom.obd.credits, 'DEV симуляция · PDF доступен в предпросмотре.');
            updateAdminReport('obd', 'ready', 'Отчёт сформирован и отправлен.');
            logEvent('REPORT', 'Диагностика завершена', summary);
            startAutoReturnCountdown('obd');
        });

        dom.obd.refreshPorts?.addEventListener('click', () => {
            populateObdPorts(true);
        });
    }

    function updateObdSummary() {
        const make = state.obd.make ? `Марка: ${state.obd.make}` : 'Марка не выбрана';
        dom.obd.summaryMake.textContent = make;
        dom.obd.summaryMode.textContent = state.obd.mode ? `Режим: ${state.obd.mode}` : 'Выберите режим проверки';
        const meta = [];
        if (state.obd.mode === 'obd2') {
            meta.push('Стандартизированные PID', 'Глубокая проверка MIL');
        } else if (state.obd.mode === 'general') {
            meta.push('Быстрая сводка основных блоков', 'Live-данные напряжения и температуры');
        } else {
            meta.push('После выбора подберём сценарий опроса.');
        }
        dom.obd.summaryMeta.innerHTML = meta.map((item) => `<li>${item}</li>`).join('');
        if (state.obd.mode && state.obd.make) {
            dom.obd.contactSummary?.classList.remove('selection-summary--pending');
            dom.obd.summaryNote.textContent = 'Слот адаптера и подсказки подключения включены.';
        }
    }

    function populateObdPorts(manual = false) {
        if (!dom.obd.portSelect) {
            return;
        }
        dom.obd.portSelect.innerHTML = '<option value="">Выберите порт…</option>';
        const ports = ['COM3', 'COM4', 'COM5'];
        ports.forEach((port) => {
            const option = document.createElement('option');
            option.value = port;
            option.textContent = `${port} · ELM327`;
            dom.obd.portSelect.appendChild(option);
        });
        dom.obd.portStatus.textContent = manual
            ? 'Порты обновлены вручную.'
            : 'Обнаружены доступные адаптеры.';
        dom.obd.portLastRefresh.textContent = new Date().toLocaleTimeString('ru-RU');
        dom.obd.portSelect.value = ports[0];
        dom.obd.start?.removeAttribute('disabled');
    }

    function prepareObdScan() {
        dom.obd.status.textContent = 'OBD‑адаптер: подключаем…';
        dom.obd.status.classList.remove('badge-danger');
        dom.obd.status.classList.add('badge-warn');
        populateObdPorts();
        state.obd.sessionId = state.obd.sessionId || uuid('OBD');
        updateAdminSessions();
        updateAdminDevice('obd', 'connecting', 'Ожидаем подтверждение адаптера.');
        setTimeout(() => {
            dom.obd.status.textContent = 'OBD‑адаптер: готов к сканированию';
            dom.obd.status.classList.remove('badge-warn');
            dom.obd.status.classList.add('badge-ok');
            logEvent('OBD', 'Адаптер готов', 'ELM327 зарегистрирован, порт активен');
        }, 1200);
    }

    function startObdScan() {
        dom.obd.start?.setAttribute('disabled', 'true');
        dom.obd.status.textContent = 'OBD‑адаптер: сканирование 0%';
        dom.obd.status.classList.add('badge-warn');
        updateAdminDevice('obd', 'scanning', 'Выполняем запросы к ECU.');
        const phases = [
            { label: 'Чтение кодов DTC', channel: 'OBD' },
            { label: 'Проверка статусов', channel: 'OBD' },
            { label: 'Получение live-данных', channel: 'OBD' }
        ];
        let index = 0;

        const tick = () => {
            if (index >= phases.length) {
                dom.obd.status.textContent = 'OBD‑адаптер: сканирование завершено';
                dom.obd.status.classList.remove('badge-warn');
                dom.obd.status.classList.add('badge-ok');
                finishObdScan();
                return;
            }
            const progress = Math.round(((index + 1) / phases.length) * 100);
            dom.obd.status.textContent = `OBD‑адаптер: ${phases[index].label} (${progress}%)`;
            logEvent(phases[index].channel, phases[index].label, 'Этап в процессе');
            index += 1;
            setTimeout(tick, 1200);
        };

        tick();
    }

    function finishObdScan() {
        state.obd.scanComplete = true;
        state.obd.dtc = JSON.parse(JSON.stringify(obdSampleDtc));
        state.obd.live = [...obdLiveTemplate];
        refreshObdLiveData();
        showScreen('screen-obd-paywall', 'Сканирование завершено');
        dom.obd.paywallAmount.textContent = formatCurrency(480);
        dom.obd.paywallInfo.textContent = 'Оплата доступна в DEV. QR заменён плейсхолдером.';
        updateAdminPayment('obd', 'pending', 'Ожидаем подтверждение клиента.');
        logEvent('PAYMENT', 'Счёт на оплату диагностики', '480 ₽');
        setTimeout(() => {
            updateAdminPayment('obd', 'succeeded', 'Платёж подтверждён, открываем результаты.');
            showScreen('screen-obd-results', 'Оплата диагностики подтверждена');
            renderObdResults();
        }, 2000);
    }

    function refreshObdLiveData() {
        if (!state.obd.live.length) {
            return;
        }
        state.obd.live = state.obd.live.map((entry) => ({
            ...entry,
            value: entry.value.replace(/\d+/, (num) => Number(num) + randomBetween(-2, 2))
        }));
        dom.obd.liveData.innerHTML = state.obd.live
            .map((entry) => `<div><span>${entry.label}</span><b>${entry.value}</b></div>`)
            .join('');
    }

    function renderObdResults() {
        const hasErrors = state.obd.dtc.length > 0;
        dom.obd.resultsList.innerHTML = state.obd.dtc
            .map((item) => `
        <div class="obd-dtc obd-severity-${item.severity}">
          <div>${item.code}</div>
          <div>${item.description}</div>
        </div>
      `)
            .join('');
        dom.obd.statusSummary.textContent = hasErrors
            ? `Найдено ${state.obd.dtc.length} код(ов)`
            : 'Система в норме';
        dom.obd.clearButton?.toggleAttribute('disabled', !hasErrors);
    }

    function clearObdCodes() {
        if (!state.obd.dtc.length) {
            return;
        }
        logEvent('OBD', 'Сброс ошибок', 'Команда Clear DTC выполнена');
        state.obd.dtc = [];
        renderObdResults();
        dom.obd.resultsLead.textContent = 'Ошибки сброшены, наблюдайте за live-данными.';
    }

    function runObdSelfCheck() {
        dom.obd.paywallSelfCheck.textContent = 'Самопроверка выполняется…';
        setTimeout(() => {
            dom.obd.paywallSelfCheck.textContent = 'Адаптер: OK · Напряжение 12.2В';
        }, 1000);
    }

    function startPaymentSimulation(service) {
        if (service === 'thickness') {
            dom.thk.amount.textContent = formatCurrency(state.thk.price);
            updateAdminPayment('thk', 'pending', 'QR отправлен клиенту.');
            logEvent('PAYMENT', 'Счёт на толщинометрию', formatCurrency(state.thk.price));
            setTimeout(() => {
                updateAdminPayment('thk', 'succeeded', 'Платёж подтверждён.');
                showScreen('screen-thk-prep', 'Оплата толщинометра подтверждена');
            }, 2000);
        }
    }

    function updateAdminSessions() {
        dom.admin.sessionThk.textContent = `Толщиномер: ${state.thk.sessionId || '—'}`;
        dom.admin.sessionObd.textContent = `Диагностика: ${state.obd.sessionId || '—'}`;
    }

    function updateAdminDevice(kind, status, detail) {
        if (kind === 'thk') {
            if (dom.admin.deviceThkStatus) {
                dom.admin.deviceThkStatus.dataset.tone = status;
                dom.admin.deviceThkStatus.textContent = status;
            }
            if (dom.admin.deviceThkDetail) {
                dom.admin.deviceThkDetail.textContent = detail;
            }
        } else {
            if (dom.admin.deviceObdStatus) {
                dom.admin.deviceObdStatus.dataset.tone = status;
                dom.admin.deviceObdStatus.textContent = status;
            }
            if (dom.admin.deviceObdDetail) {
                dom.admin.deviceObdDetail.textContent = detail;
            }
        }
    }

    function updateAdminPayment(kind, status, detail) {
        if (kind === 'thk') {
            if (dom.admin.paymentThkStatus) {
                dom.admin.paymentThkStatus.dataset.tone = status;
                dom.admin.paymentThkStatus.textContent = status;
            }
            if (dom.admin.paymentThkMeta) {
                dom.admin.paymentThkMeta.textContent = detail;
            }
        } else {
            if (dom.admin.paymentObdStatus) {
                dom.admin.paymentObdStatus.dataset.tone = status;
                dom.admin.paymentObdStatus.textContent = status;
            }
            if (dom.admin.paymentObdMeta) {
                dom.admin.paymentObdMeta.textContent = detail;
            }
        }
    }

    function updateAdminReport(kind, status, detail) {
        if (kind === 'thk') {
            if (dom.admin.reportThkStatus) {
                dom.admin.reportThkStatus.dataset.tone = status;
                dom.admin.reportThkStatus.textContent = status;
            }
            if (dom.admin.reportThkMeta) {
                dom.admin.reportThkMeta.textContent = detail;
            }
        } else {
            if (dom.admin.reportObdStatus) {
                dom.admin.reportObdStatus.dataset.tone = status;
                dom.admin.reportObdStatus.textContent = status;
            }
            if (dom.admin.reportObdMeta) {
                dom.admin.reportObdMeta.textContent = detail;
            }
        }
    }

    function toggleAdminDrawer(forceOpen) {
        const drawer = dom.admin.drawer;
        if (!drawer) {
            return;
        }
        const shouldOpen = typeof forceOpen === 'boolean' ? forceOpen : drawer.classList.contains('hidden');
        drawer.classList.toggle('hidden', !shouldOpen);
        logEvent('UI', shouldOpen ? 'Открыт мониторинг' : 'Скрыт мониторинг', 'Admin bridge');
    }

    function setupAdminBridge() {
        dom.admin.drawerToggle?.addEventListener('click', () => {
            toggleAdminDrawer(true);
        });
        dom.admin.drawerClose?.addEventListener('click', () => {
            toggleAdminDrawer(false);
        });
        dom.admin.eventsClear?.addEventListener('click', () => {
            state.admin.events = [];
            renderEvents();
        });
        dom.admin.eventsReset?.addEventListener('click', () => {
            state.admin.filters.forEach((_, channel) => state.admin.filters.set(channel, false));
            buildEventFilters();
            renderEvents();
        });
        dom.admin.timelineReset?.addEventListener('click', () => {
            resetExperience('Сброс таймлайна в мониторинге');
        });
        buildEventFilters();
    }

    function setupHotkeys() {
        document.addEventListener('keydown', (event) => {
            if (!event.ctrlKey || !event.shiftKey) {
                return;
            }
            if (event.code === 'KeyA') {
                event.preventDefault();
                const drawer = dom.admin.drawer;
                const isHidden = drawer?.classList.contains('hidden');
                toggleAdminDrawer(Boolean(isHidden));
            } else if (event.code === 'KeyD') {
                event.preventDefault();
                setDevMode(!state.devMode);
                logEvent('UI', 'DEV режим', state.devMode ? 'Включён' : 'Выключен');
            }
        });
    }

    function setupReportPreview() {
        const modal = select('report-preview-modal');
        const frame = select('report-preview-frame');
        const closeBtn = select('report-preview-close');
        const sendSmsBtn = select('report-send-sms');
        const sendEmailBtn = select('report-send-email');
        const copyBtn = select('report-copy-link');
        const skipBtn = select('report-skip-send');
        let currentPreview = null;

        const open = (html, context) => {
            if (!modal || !frame) {
                return;
            }
            modal.classList.remove('hidden');
            frame.srcdoc = html;
            currentPreview = context;
            logEvent('REPORT', `Открыт предпросмотр (${context === 'thk' ? 'Толщиномер' : 'Диагностика'})`, 'Режим предпросмотра');
        };

        const close = () => {
            modal?.classList.add('hidden');
            currentPreview = null;
        };

        closeBtn?.addEventListener('click', close);
        modal?.addEventListener('click', (event) => {
            if (event.target === modal) {
                close();
            }
        });

        select('thk-preview')?.addEventListener('click', () => {
            open(buildPreviewHtml('Толщинометрия', state.thk), 'thk');
        });
        select('obd-preview')?.addEventListener('click', () => {
            open(buildPreviewHtml('Диагностика OBD', state.obd), 'obd');
        });

        const recordDelivery = (action, detail) => {
            if (!currentPreview) {
                return;
            }
            const label = currentPreview === 'thk' ? 'Отчёт толщиномера' : 'Отчёт диагностики';
            logEvent('REPORT', `${label}: ${action}`, detail);
            const status = action === 'Пропущено' ? 'skipped' : 'sent';
            updateAdminReport(currentPreview, status, detail);
        };

        sendSmsBtn?.addEventListener('click', () => {
            recordDelivery('SMS отправлена', 'Отправка по SMS из предпросмотра');
        });
        sendEmailBtn?.addEventListener('click', () => {
            recordDelivery('Email отправлен', 'Отправка по email из предпросмотра');
        });
        copyBtn?.addEventListener('click', async () => {
            if (!currentPreview) {
                return;
            }
            const link = buildReportLink(currentPreview);
            try {
                await navigator.clipboard?.writeText(link);
                recordDelivery('Ссылка скопирована', `Скопирована ссылка ${link}`);
            } catch (error) {
                console.error('copy failed', error);
            }
        });
        skipBtn?.addEventListener('click', () => {
            recordDelivery('Пропущено', 'Отправка отчёта пропущена оператором');
            close();
        });
    }

    function buildPreviewHtml(title, payload) {
        const rows = Object.entries(payload)
            .filter(([, value]) => typeof value === 'string' && value)
            .map(([key, value]) => `<tr><th>${key}</th><td>${value}</td></tr>`)
            .join('');
        return `
      <!doctype html>
      <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <style>
            body { font-family: Inter, sans-serif; margin: 0; padding: 24px; }
            h1 { font-size: 20px; margin-bottom: 16px; }
            table { width: 100%; border-collapse: collapse; }
            th, td { text-align: left; padding: 8px; border-bottom: 1px solid #eee; }
            th { width: 30%; }
          </style>
        </head>
        <body>
          <h1>${title}</h1>
          <table>${rows}</table>
        </body>
      </html>
    `;
    }

    function init() {
        setDevMode(true);
        hydrateSettings();
        setupHero();
        setupWelcome();
        setupTermsModal();
        setupSettingsPanel();
        setupServices();
        setupBackButtons();
        setupThicknessFlow();
        setupObdFlow();
        setupHomeButtons();
        setupAdminBridge();
        setupHotkeys();
        setupReportPreview();
        setupInactivityWatchers();
        resetInactivityTimer();
        updateAdminScreenLabel();
        resetThicknessGrid();
        logEvent('UI', 'DEV монитор запущен', 'Симуляция готова к работе');
    }

    init();
})();
