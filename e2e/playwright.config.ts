import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  workers: 1,
  retries: 0,
  fullyParallel: false,
  reporter: [['list']],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    {
      name: 'mobile',
      use: { ...devices['Pixel 7'], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
    },
  ],
  // Postgres is started inside the backend command: webServer plugins start before globalSetup,
  // so a globalSetup could not guarantee the database exists when Spring Boot boots.
  webServer: [
    {
      // CI provides Postgres as a service container, so docker compose is skipped there.
      command: process.env.CI
        ? '../backend/mvnw -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=e2e'
        : 'docker compose -f ../docker-compose.yml up -d --wait postgres && ..\\backend\\mvnw.cmd -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=e2e',
      url: 'http://localhost:8080/v3/api-docs',
      timeout: 240_000,
      reuseExistingServer: true,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm --prefix ../frontend start',
      url: 'http://localhost:4200',
      timeout: 240_000,
      reuseExistingServer: true,
    },
  ],
});
