import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'FLOWOPS_');

  return {
    plugins: [react(), tailwindcss()],
    // Four workers rather than one per core, and it is a correctness fix before it is a speed one.
    // Unbounded, the pool starved itself on this machine class: a full run failed five tests across
    // three files that every one of them passed in isolation, and it failed a *different* set on each
    // run — the signature of contention rather than of a defect. It has also, earlier in this project,
    // reported `Test Files 24 passed (24)` with 35 files on disk, which is the same starvation printing
    // the word `passed` next to a number that was simply wrong.
    //
    // Capped: 66 files, 592 tests, green, 114s. Unbounded: 5 failures and 216s. Faster and true.
    test: {
      maxWorkers: 4,
      // The browser suite is `vitest.browser.config.ts`'s, and it has to be said here or the default
      // include pattern claims those files too — running layout assertions in a DOM that has no
      // layout, where they do not fail honestly so much as become meaningless.
      exclude: ['**/node_modules/**', '**/dist/**', '**/*.browser.test.tsx'],
    },
    server: {
      port: 5173,
      proxy: {
        // 8081 rather than the framework default. Tomcat9 is installed on this machine class and
        // holds 8080, and a proxy pointed at it answers 404 for every call — which reads as a
        // missing route rather than a missing backend, and has cost real debugging time twice.
        // FLOWOPS_API_URL overrides it; FLOWOPS_SERVER_PORT is the backend's matching knob.
        '/api': {
          target: env.FLOWOPS_API_URL ?? 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
  };
});
