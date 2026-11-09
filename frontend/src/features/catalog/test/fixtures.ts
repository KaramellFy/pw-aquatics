import type { Facets, ProductDetail, ProductPage, ProductSummary } from '../api/catalog';

export const blueDream: ProductSummary = {
  id: '1',
  slug: 'blue-dream-high',
  name: 'Blue Dream',
  species: {
    slug: 'neocaridina-davidi',
    scientificName: 'Neocaridina davidi',
    commonName: 'Neocaridina shrimp',
  },
  category: { slug: 'neocaridina', name: 'Neocaridina' },
  grade: { slug: 'high', name: 'High' },
  colours: [{ slug: 'blue', name: 'Blue', hexCode: '#1565C0' }],
  priceFrom: { amount: 390, currency: 'EUR' },
  compareAtPriceFrom: null,
  hasMultipleVariants: true,
  availability: 'IN_STOCK',
  liveAnimal: true,
  primaryImage: null,
  secondaryImage: null,
};

export const blackRose: ProductSummary = {
  ...blueDream,
  id: '2',
  slug: 'black-rose-premium',
  name: 'Black Rose',
  grade: { slug: 'premium', name: 'Premium' },
  colours: [{ slug: 'black', name: 'Black', hexCode: '#212121' }],
  priceFrom: { amount: 490, currency: 'EUR' },
  hasMultipleVariants: false,
  availability: 'OUT_OF_STOCK',
};

export function page(items: ProductSummary[]): ProductPage {
  return { items, page: 0, size: 24, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

export const facets: Facets = {
  species: [{ slug: 'neocaridina-davidi', name: 'Neocaridina shrimp', hexCode: null, count: 2 }],
  colours: [
    { slug: 'blue', name: 'Blue', hexCode: '#1565C0', count: 1 },
    { slug: 'black', name: 'Black', hexCode: '#212121', count: 1 },
  ],
  grades: [{ slug: 'high', name: 'High', hexCode: null, count: 1 }],
  availability: { inStock: 1, comingSoon: 0 },
  price: { min: { amount: 390, currency: 'EUR' }, max: { amount: 490, currency: 'EUR' } },
};

export const taxonomy = {
  '/categories': [
    {
      slug: 'shrimp',
      name: 'Shrimp',
      description: 'All shrimp.',
      children: [{ slug: 'neocaridina', name: 'Neocaridina', description: null, children: [] }],
    },
  ],
  '/species': [
    {
      slug: 'neocaridina-davidi',
      scientificName: 'Neocaridina davidi',
      commonName: 'Neocaridina shrimp',
      difficulty: 'BEGINNER',
    },
  ],
  '/colours': [
    { slug: 'blue', name: 'Blue', hexCode: '#1565C0' },
    { slug: 'black', name: 'Black', hexCode: '#212121' },
  ],
  '/grades': [{ slug: 'high', name: 'High', scale: 'NEOCARIDINA', rank: 2, description: null }],
};

export const blueDreamDetail: ProductDetail = {
  id: '1',
  slug: 'blue-dream-premium',
  name: 'Blue Dream',
  shortDescription: 'Our most intense Blue Dream selection.',
  descriptionHtml: '<p>Solid, <strong>intense blue</strong>.</p>',
  careNotesHtml: null,
  species: {
    slug: 'neocaridina-davidi',
    scientificName: 'Neocaridina davidi',
    commonName: 'Neocaridina shrimp',
    origin: 'Taiwan',
    descriptionHtml: null,
    care: {
      tempMinC: 18,
      tempMaxC: 28,
      phMin: 6.5,
      phMax: 8,
      ghMin: 6,
      ghMax: 15,
      khMin: null,
      khMax: null,
      tdsMin: null,
      tdsMax: null,
      adultSizeMm: 30,
      lifespanMonths: 18,
      difficulty: 'BEGINNER',
    },
  },
  categoryPath: [
    { slug: 'shrimp', name: 'Shrimp' },
    { slug: 'neocaridina', name: 'Neocaridina' },
  ],
  grade: { slug: 'premium', name: 'Premium' },
  colours: [{ slug: 'blue', name: 'Blue', hexCode: '#1565C0' }],
  priceFrom: { amount: 590, currency: 'EUR' },
  availability: 'LOW_STOCK',
  availableFrom: null,
  liveAnimal: true,
  variants: [
    {
      id: 'v1',
      sku: 'BD-1',
      name: '1 animal',
      packSize: 1,
      price: { amount: 590, currency: 'EUR' },
      compareAtPrice: null,
      available: true,
      maxQuantity: 6,
    },
    {
      id: 'v10',
      sku: 'BD-10',
      name: '10 animals',
      packSize: 10,
      price: { amount: 5200, currency: 'EUR' },
      compareAtPrice: null,
      available: false,
      maxQuantity: 0,
    },
  ],
  images: [],
  seo: { title: 'Blue Dream', description: 'Our most intense Blue Dream selection.' },
};
