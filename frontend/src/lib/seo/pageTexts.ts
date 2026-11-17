import type { Messages } from '../i18n/locales/en.ts';

/**
 * Interface texts of the pages the backend fills in for the first page load
 * (docs/adr/013-pages-filled-in-by-the-backend.md). The build writes them for every language
 * into `pages.json`, so the backend's page shows the same words as the app.
 *
 * Imported by vite.config.ts: only relative imports, with file extensions (Vite's config loader).
 */
export function pageTexts(t: Messages) {
  const page = (meta: { title: string; description: string }, heading: string, lead: string) => ({
    title: meta.title,
    description: meta.description,
    heading: withoutMarkup(heading),
    lead: withoutMarkup(lead),
  });
  return {
    brand: t.common.brand,
    backToHome: t.common.backToHome,
    nav: {
      main: t.nav.main,
      shop: t.nav.shop,
      gallery: t.nav.gallery,
      about: t.nav.about,
      contact: t.nav.contact,
    },
    pages: {
      home: page(t.home.meta, t.home.hero.title, t.home.hero.lead),
      shop: page(t.shop.meta, t.shop.allAnimals, t.shop.lead),
      about: page(t.about.meta, t.about.title, t.about.lead),
      gallery: page(t.gallery.meta, t.gallery.title, t.gallery.lead),
      contact: page(t.contact.meta, t.contact.title, t.contact.lead),
    },
    notFound: { title: t.notFound.meta.title, heading: t.notFound.title, text: t.notFound.text },
    product: { from: t.product.from, availability: t.product.availability },
    species: { available: t.species.available },
    pagination: {
      label: t.shop.pagination.label,
      previous: t.shop.pagination.previous,
      next: t.shop.pagination.next,
    },
  };
}

/** Drops the tags `<Trans>` fills in ("<days>Monday</days>" → "Monday"). */
function withoutMarkup(text: string): string {
  return text.replace(/<\/?[a-zA-Z][^>]*>/g, '');
}
