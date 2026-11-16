import { useLocation } from 'react-router';
import {
  DEFAULT_LANGUAGE,
  pathInLanguage,
  SUPPORTED_LANGUAGES,
  useLanguage,
  type Language,
} from '@/lib/i18n';

interface PageMetaProps {
  title: string;
  description?: string;
  /** Set for pages that must not be indexed (account, cart, placeholders). */
  noIndex?: boolean;
}

/** Open Graph locale per language. */
const OG_LOCALES: Record<Language, string> = { en: 'en_GB', de: 'de_DE' };

/**
 * Per-page document metadata. React 19 hoists <title>, <meta> and <link> into <head>.
 *
 * Indexable pages also name their canonical link (without query string) and the same page in
 * every language (`hreflang`, English as `x-default`), so search engines show each visitor the
 * right language (docs/adr/012-language-in-links.md).
 */
export function PageMeta({ title, description, noIndex = false }: PageMetaProps) {
  const language = useLanguage();
  const { pathname } = useLocation();
  const origin = window.location.origin;
  const url = (lang: Language) => origin + pathInLanguage(pathname, lang);
  return (
    <>
      <title>{title}</title>
      {description && <meta name="description" content={description} />}
      {description && <meta property="og:description" content={description} />}
      <meta property="og:title" content={title} />
      <meta property="og:locale" content={OG_LOCALES[language]} />
      {noIndex && <meta name="robots" content="noindex" />}
      {!noIndex && <link rel="canonical" href={url(language)} />}
      {!noIndex && <meta property="og:url" content={url(language)} />}
      {!noIndex &&
        SUPPORTED_LANGUAGES.map((lang) => (
          <link key={lang} rel="alternate" hrefLang={lang} href={url(lang)} />
        ))}
      {!noIndex && <link rel="alternate" hrefLang="x-default" href={url(DEFAULT_LANGUAGE)} />}
    </>
  );
}
