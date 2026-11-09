/** Primary site navigation. Labels are i18n keys. */
export const mainNavigation = [
  { to: '/shop', labelKey: 'nav.shop' },
  { to: '/gallery', labelKey: 'nav.gallery' },
  { to: '/about', labelKey: 'nav.about' },
  { to: '/contact', labelKey: 'nav.contact' },
] as const;

export const legalNavigation = [
  { to: '/legal/imprint', labelKey: 'legal.pages.imprint.title' },
  { to: '/legal/privacy', labelKey: 'legal.pages.privacy.title' },
  { to: '/legal/terms', labelKey: 'legal.pages.terms.title' },
  { to: '/legal/withdrawal', labelKey: 'legal.pages.withdrawal.title' },
] as const;

/** Category landing links; slugs must match the backend category slugs (Phase 2). */
export const categoryLinks = [
  { slug: 'shrimp', key: 'shrimp', motif: 'shrimp', tone: 'sky' },
  { slug: 'snails', key: 'snails', motif: 'snail', tone: 'ice' },
  { slug: 'crayfish', key: 'crayfish', motif: 'shell', tone: 'lagoon' },
  { slug: 'crabs', key: 'crabs', motif: 'waves', tone: 'deep' },
] as const;
