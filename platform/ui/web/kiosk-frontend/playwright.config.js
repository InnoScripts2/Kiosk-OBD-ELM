import { defineConfig, devices } from '@playwright/test';

const ensureLocalhostBypass = () => {
  const targets = ['127.0.0.1', 'localhost'];
  const envKeys = ['NO_PROXY', 'no_proxy'];
  const currentValues = envKeys
    .map((key) => process.env[key] || '')
    .join(',')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

  const merged = new Set(currentValues);
  let changed = false;
  targets.forEach((entry) => {
    if (!merged.has(entry)) {
      merged.add(entry);
      changed = true;
    }
  });

  if (changed || merged.size) {
    const serialized = Array.from(merged).join(',');
    envKeys.forEach((key) => {
      process.env[key] = serialized;
    });
  }
};

ensureLocalhostBypass();

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],

  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 180000,
  },
});
