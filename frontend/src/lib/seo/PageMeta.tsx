interface PageMetaProps {
  title: string;
  description?: string;
  /** Set for pages that must not be indexed (account, cart, placeholders). */
  noIndex?: boolean;
}

/**
 * Per-page document metadata. React 19 hoists <title> and <meta> into <head>.
 * Structured data (JSON-LD) for products follows with the catalog (Phase 2).
 */
export function PageMeta({ title, description, noIndex = false }: PageMetaProps) {
  return (
    <>
      <title>{title}</title>
      {description && <meta name="description" content={description} />}
      {description && <meta property="og:description" content={description} />}
      <meta property="og:title" content={title} />
      {noIndex && <meta name="robots" content="noindex" />}
    </>
  );
}
