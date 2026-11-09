import i18n from 'i18next';
import { initReactI18next, useTranslation } from 'react-i18next';
import { de } from './locales/de';
import { en } from './locales/en';

export const SUPPORTED_LANGUAGES = ['de', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'de';
const STORAGE_KEY = 'ww.language';

/** Intl locale per UI language (used for money and date formatting). */
const INTL_LOCALES: Record<Language, string> = { de: 'de-DE', en: 'en-GB' };

export const resources = {
  de: { translation: de },
  en: { translation: en },
} as const;

function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

function storedLanguage(): Language {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return isLanguage(value) ? value : DEFAULT_LANGUAGE;
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

void i18n.use(initReactI18next).init({
  resources,
  lng: storedLanguage(),
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

/** Switches the UI language and remembers the choice (a UI preference, not tracking). */
export async function changeLanguage(language: Language): Promise<void> {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // storage may be unavailable (private mode) — language still changes for this visit
  }
  await i18n.changeLanguage(language);
}

export function currentLanguage(): Language {
  return isLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : DEFAULT_LANGUAGE;
}

/** Returns the Intl locale (e.g. "de-DE") for the active UI language. */
export function useIntlLocale(): string {
  const { i18n: instance } = useTranslation();
  const language = isLanguage(instance.resolvedLanguage) ? instance.resolvedLanguage : DEFAULT_LANGUAGE;
  return INTL_LOCALES[language];
}

export default i18n;
