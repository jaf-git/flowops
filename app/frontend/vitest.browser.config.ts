import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vite';

/**
 * The suite that runs in a real browser, and it exists because three defects in a row got past 801
 * tests that could not see them.
 *
 * Every one was invisible to jsdom for the same reason: jsdom lays nothing out. The dialogs that
 * rendered in the viewport's top-left corner, the call to action at the wrong font weight, and the
 * dialog that discarded a half-written process when somebody touched its scrollbar were all found by
 * a person opening a browser, never by this project's tests.
 *
 * Kept separate from `vite.config.ts` on purpose. The jsdom suite is 801 tests in about a minute and
 * stays the one that runs on every save; this one starts Chromium and is for the handful of claims
 * that are about **layout, scrolling, focus or hit-testing** — the things a DOM without a renderer
 * cannot answer. A test that would pass in jsdom does not belong here.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    include: ['src/**/*.browser.test.tsx'],
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      // Named rather than defaulted: these tests are about where things sit, and a suite whose
      // assertions depend on a viewport nobody wrote down is a suite that changes meaning when the
      // default does.
      instances: [{ browser: 'chromium', viewport: { width: 1280, height: 800 } }],
    },
  },
});
