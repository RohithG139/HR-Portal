// Import defineConfig from vitest/config so the `test` property is typed.
// Using vitest/config instead of vite re-exports all Vite configuration options
// plus the Vitest-specific `test` block.
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  server: {
    // Polling instead of native fs events: found live, 2026-10-18 — on this Windows host,
    // edits made outside the container to a bind-mounted source file were not reaching the
    // watcher inside the hr-frontend dev container at all (confirmed via `docker logs
    // hr-frontend` showing zero HMR updates for hours of edits), so CLAUDE.md's documented
    // "Vite HMR hot-reloads the browser on every source change" silently wasn't happening —
    // every edit required a manual `docker compose restart hr-frontend` to take effect.
    watch: {
      usePolling: true,
    },
  },
  test: {
    // Use jsdom to simulate a browser DOM environment for component tests
    environment: 'jsdom',
    // Expose describe/it/expect/vi globally so tests don't need explicit imports
    globals: true,
    // Import jest-dom matchers and any other global test setup before each suite
    setupFiles: './src/test-setup.ts',
    // Increase timeout for async UI tests that rely on React Query resolving —
    // the default 5000ms can be tight when many test files run concurrently in
    // the same Vitest worker pool (observed with >10 test files in Wave 3).
    testTimeout: 15000,
  },
})
