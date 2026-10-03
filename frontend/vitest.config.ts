import path from 'node:path';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'node',
    setupFiles: ['./vitest.setup.ts'],
    // Unit/contract tests only — Playwright specs under tests/ belong to
    // `npx playwright test` and must not be picked up by vitest.
    include: ['src/**/*.test.ts'],
  },
});
