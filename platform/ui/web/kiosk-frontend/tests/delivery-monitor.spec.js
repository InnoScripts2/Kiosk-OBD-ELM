import { test, expect } from '@playwright/test';

const PANEL_SELECTOR = '#session-telemetry-panel';
const REPORT_META_SELECTOR = '#report-status-thk-meta';

test.describe('Delivery monitor visibility', () => {
    test('hides Supabase telemetry when monitor disabled', async ({ page }) => {
        await page.addInitScript(() => {
            window.__DISABLE_DELIVERY_MONITOR__ = true;
        });

        await page.goto('/');

        await expect(page.locator('body')).toHaveAttribute('data-delivery-monitor', 'false');
        await expect(page.locator(PANEL_SELECTOR)).toHaveCount(0);
        await expect(page.locator(REPORT_META_SELECTOR)).toHaveCount(0);
    });
});
