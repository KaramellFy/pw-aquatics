/** Primary site navigation. Labels are i18n keys. */
export const mainNavigation = [
  { to: '/shop', labelKey: 'nav.shop' },
  { to: '/gallery', labelKey: 'nav.gallery' },
  { to: '/about', labelKey: 'nav.about' },
  { to: '/contact', labelKey: 'nav.contact' },
] as const;

export const legalNavigation = [
  { to: '/legal/impressum', labelKey: 'legal.pages.impressum.title' },
  { to: '/legal/datenschutz', labelKey: 'legal.pages.datenschutz.title' },
  { to: '/legal/agb', labelKey: 'legal.pages.agb.title' },
  { to: '/legal/widerruf', labelKey: 'legal.pages.widerruf.title' },
] as const;

/** Category landing links; slugs must match the backend category slugs (Phase 2). */
export const categoryLinks = [
  { slug: 'garnelen', key: 'shrimp', motif: 'shrimp', tone: 'deep' },
  { slug: 'schnecken', key: 'snails', motif: 'snail', tone: 'moss' },
  { slug: 'krebse', key: 'crayfish', motif: 'shell', tone: 'ember' },
  { slug: 'krabben', key: 'crabs', motif: 'waves', tone: 'night' },
] as const;
