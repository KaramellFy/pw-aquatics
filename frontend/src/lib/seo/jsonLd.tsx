/**
 * Structured data for search engines (schema.org JSON-LD). Rendered as an inline
 * script tag; content is JSON-encoded with "<" escaped so it cannot close the tag.
 */
export function JsonLd({ data }: { data: Record<string, unknown> }) {
  const json = JSON.stringify(data).replace(/</g, '\\u003c');
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: json }} />;
}
