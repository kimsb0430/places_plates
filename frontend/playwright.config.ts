import { defineConfig, devices } from '@playwright/test';

const webOrigin = 'http://127.0.0.1:3210';
const apiOrigin = 'http://127.0.0.1:3211';
const browserChannel = process.env.PLAYWRIGHT_BROWSER_CHANNEL;
const localBrowser = browserChannel === 'chrome' || browserChannel === 'msedge'
  ? { channel: browserChannel }
  : {};

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: webOrigin,
    locale: 'ko-KR',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'desktop-chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...localBrowser,
        viewport: { width: 1440, height: 1000 },
      },
    },
    {
      name: 'mobile-chromium',
      use: {
        ...devices['Pixel 7'],
        ...localBrowser,
      },
    },
  ],
  webServer: [
    {
      command: 'node --experimental-strip-types tests/e2e/support/fake-api-server.ts',
      url: `${apiOrigin}/__e2e/health`,
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'pnpm exec next dev --hostname 127.0.0.1 --port 3210',
      url: `${webOrigin}/manage`,
      reuseExistingServer: false,
      timeout: 60_000,
      env: {
        NEXT_PUBLIC_API_BASE_URL: apiOrigin,
        NEXT_PUBLIC_GOOGLE_MAPS_API_KEY: 'e2e-placeholder',
        NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID: '',
      },
    },
  ],
});
