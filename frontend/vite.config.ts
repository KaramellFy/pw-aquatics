/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';
import { ROUTE_PATHS } from './src/app/routePaths.ts';
import { de } from './src/lib/i18n/locales/de.ts';
import { en } from './src/lib/i18n/locales/en.ts';
import { pageTexts } from './src/lib/seo/pageTexts.ts';

// The backend runs on :8080 in development. Proxying /api and /media keeps the
// browser on a single origin, exactly like production behind Caddy, so session
// and CSRF cookies behave the same everywhere (see docs/ARCHITECTURE.md R9).
const backendUrl = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

/**
 * Writes `pages.json` next to index.html: the app's routes and the page titles, navigation and
 * labels the backend needs to fill in pages (docs/adr/013-pages-filled-in-by-the-backend.md).
 */
function pagesFile(): Plugin {
  return {
    name: 'pw-aquatics-pages',
    apply: 'build',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'pages.json',
        source: JSON.stringify({ routes: ROUTE_PATHS, texts: { en: pageTexts(en), de: pageTexts(de) } }),
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), pagesFile()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backendUrl, changeOrigin: false },
      '/media': { target: backendUrl, changeOrigin: false },
    },
  },
  build: {
    target: 'es2022',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
