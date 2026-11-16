import { BrowserContext, Page, test as base } from '@playwright/test';
import { resetBackend, setBackendClock } from './api';

/** Fixed business day (a Friday, in the future). Slot times are Asia/Ho_Chi_Minh wall time. */
export const DAY = '2026-11-20';
export const wall = (hhmm: string, day: string = DAY): Date => new Date(`${day}T${hhmm}:00+07:00`);

export interface World {
  /** Moves the backend clock and the browser clock of every open context to the same instant. */
  setNow(hhmm: string, day?: string): Promise<void>;
  /** A page in a fresh, isolated browser context (own localStorage) with the same viewport and clock. */
  newPage(): Promise<Page>;
}

export const test = base.extend<{ world: World }>({
  world: [
    async ({ request, context, browser, baseURL }, use, testInfo) => {
      const contexts: BrowserContext[] = [context];
      let current = wall('08:00');
      const pin = async (d: Date) => {
        current = d;
        await setBackendClock(request, d);
        for (const c of contexts) await c.clock.setFixedTime(d);
      };
      await resetBackend(request);
      await pin(wall('08:00'));
      const p = testInfo.project.use;
      await use({
        setNow: (hhmm, day = DAY) => pin(wall(hhmm, day)),
        newPage: async () => {
          const ctx = await browser.newContext({
            baseURL,
            locale: p.locale,
            timezoneId: p.timezoneId,
            viewport: p.viewport,
            isMobile: p.isMobile,
            hasTouch: p.hasTouch,
            userAgent: p.userAgent,
            deviceScaleFactor: p.deviceScaleFactor,
          });
          await ctx.clock.setFixedTime(current);
          contexts.push(ctx);
          return ctx.newPage();
        },
      });
      for (const c of contexts.slice(1)) await c.close();
    },
    { auto: true },
  ],
});

/** Skips every test of the calling spec file outside the desktop project. */
export function desktopOnly(reason: string): void {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop', reason);
  });
}

export { expect } from '@playwright/test';
