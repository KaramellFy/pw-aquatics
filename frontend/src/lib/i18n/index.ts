import i18n from 'i18next';
import { initReactI18next, useTranslation } from 'react-i18next';
import { de } from './locales/de';
import { en } from './locales/en';

export const SUPPORTED_LANGUAGES = ['en', 'de'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'en';
const STORAGE_KEY = 'pwa.language';

/** Intl locale per UI language (used for money and date formatting). */
const INTL_LOCALES: Record<Language, string> = { en: 'en-GB', de: 'de-DE' };

export const resources = {
  en: { translation: en },
  de: { translation: de },
} as const;

function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/**
 * Language prefix of the links: English pages live at "/…", German pages at "/de/…" with the
 * same path words (e.g. "/de/products/blue-dream-high"). The link decides the language, so a
 * shared link keeps its language and search engines can find both versions.
 */
const PREFIXES: Record<Language, string> = { en: '', de: '/de' };

/** The language a path belongs to: "/de" and "/de/…" are German, everything else English. */
export function languageOfPath(pathname: string): Language {
  return pathname === '/de' || pathname.startsWith('/de/') ? 'de' : DEFAULT_LANGUAGE;
}

/** The router basename for a language (`undefined` for English, which has no prefix). */
export function basenameOf(language: Language): string | undefined {
  return PREFIXES[language] || undefined;
}

/**
 * The same page in a language: `pathInLanguage("/shop", "de")` → "/de/shop". The path is
 * the router path without a language prefix (as `useLocation()` reports it).
 */
export function pathInLanguage(path: string, language: Language): string {
  return PREFIXES[language] + (path.startsWith('/') ? path : `/${path}`);
}

/** Removes the language prefix: "/de/shop" → "/shop", "/de" → "/". */
export function pathWithoutLanguage(pathname: string): string {
  const prefix = PREFIXES[languageOfPath(pathname)];
  return pathname.slice(prefix.length) || '/';
}

function storedLanguage(): Language | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return isLanguage(value) ? value : null;
  } catch {
    return null;
  }
}

/** Remembers the visitor's choice (a UI preference, not tracking). */
export function rememberLanguage(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // storage may be unavailable (private mode): the link still decides the language
  }
}

/**
 * The language of this page load, from the link. Someone who chose German before and opens
 * the bare home page ("/") is taken to the German home page; every other link keeps its
 * language. (Search engines keep no stored choice, so they always see the page they asked for.)
 */
function languageOfThisPage(): Language {
  if (typeof window === 'undefined') return DEFAULT_LANGUAGE;
  const { pathname, search, hash } = window.location;
  if (pathname === '/' && storedLanguage() === 'de') {
    window.history.replaceState(window.history.state, '', pathInLanguage('/', 'de') + search + hash);
    return 'de';
  }
  return languageOfPath(pathname);
}

void i18n.use(initReactI18next).init({
  resources,
  lng: languageOfThisPage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: SUPPORTED_LANGUAGES,
  interpolation: { escapeValue: false }, // React already escapes
  returnNull: false,
});

function syncDocumentLanguage(language: string) {
  document.documentElement.lang = language;
}
syncDocumentLanguage(i18n.language);
i18n.on('languageChanged', syncDocumentLanguage);

export function currentLanguage(): Language {
  return isLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : DEFAULT_LANGUAGE;
}

/** The active UI language (follows the link: "/de/…" is German). */
export function useLanguage(): Language {
  const { i18n: instance } = useTranslation();
  return isLanguage(instance.resolvedLanguage) ? instance.resolvedLanguage : DEFAULT_LANGUAGE;
}

/** Returns the Intl locale (e.g. "de-DE") for the active UI language. */
export function useIntlLocale(): string {
  const { i18n: instance } = useTranslation();
  const language = isLanguage(instance.resolvedLanguage) ? instance.resolvedLanguage : DEFAULT_LANGUAGE;
  return INTL_LOCALES[language];
}

export default i18n;
