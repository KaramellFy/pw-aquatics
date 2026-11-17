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
  /**
   * Languages the page's own texts are written in (`languages` from the API), for pages whose
   * content the owner translates. Defaults to every language. A page shown in a language it is
   * not written in (English text on a German link) is not indexed, and the alternates name only
   * the written languages.
   */
  languages?: readonly string[];
  /**
   * The page of a paged list (1-based) that is a page of its own for search engines; pages after
   * the first keep `?page=N` in their canonical and alternate links. Other query parameters are
   * never part of them.
   */
  page?: number;
}

/** Open Graph locale per language. */
const OG_LOCALES: Record<Language, string> = { en: 'en_GB', de: 'de_DE' };

/**
 * Per-page document metadata. React 19 hoists <title>, <meta> and <link> into <head>.
 *
 * Indexable pages also name their canonical link (without query string, except a list's page
 * number) and the same page in
 * every language (`hreflang`, English as `x-default`), so search engines show each visitor the
 * right language (docs/adr/012-language-in-links.md).
 */
export function PageMeta({
  title,
  description,
  noIndex: hidden = false,
  languages,
  page = 1,
}: PageMetaProps) {
  const language = useLanguage();
  const { pathname } = useLocation();
  const origin = window.location.origin;
  const query = page > 1 ? `?page=${page}` : '';
  const url = (lang: Language) => origin + pathInLanguage(pathname, lang) + query;
  const written = SUPPORTED_LANGUAGES.filter((lang) => !languages || languages.includes(lang));
  const noIndex = hidden || !written.includes(language);
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
        written.map((lang) => <link key={lang} rel="alternate" hrefLang={lang} href={url(lang)} />)}
      {!noIndex && written.includes(DEFAULT_LANGUAGE) && (
        <link rel="alternate" hrefLang="x-default" href={url(DEFAULT_LANGUAGE)} />
      )}
    </>
  );
}
