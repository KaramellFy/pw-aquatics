import { createBrowserRouter } from 'react-router';
import { basenameOf, languageOfPath } from '@/lib/i18n';
import { routes } from './routes';

/**
 * German pages are the same routes under "/de" (docs/adr/012-language-in-links.md): the
 * language of this page load picks the basename, so every link, redirect and `navigate`
 * stays in that language. Switching languages loads the other link (LanguageSwitcher).
 * Importing `@/lib/i18n` first has already moved a returning German visitor from "/" to "/de/".
 */
export const router = createBrowserRouter(routes, {
  basename: basenameOf(languageOfPath(window.location.pathname)),
});
