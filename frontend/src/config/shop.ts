/**
 * Shop-wide constants that are not translated.
 * Values marked as placeholders must be confirmed by the owner before launch.
 */
export const shopConfig = {
  name: 'PW Aquatics',
  /** Placeholder — replace with the real contact address before launch. */
  email: 'philwakup@gmail.com',
  /**
   * Countries offered in the address form (ISO 3166-1 alpha-2): EU member states plus
   * Switzerland, Liechtenstein and Norway. Placeholder until the shipping countries are
   * decided (Phase 5); the server accepts any valid country code.
   */
  addressCountries: [
    'AT',
    'BE',
    'BG',
    'CH',
    'CY',
    'CZ',
    'DE',
    'DK',
    'EE',
    'ES',
    'FI',
    'FR',
    'GR',
    'HR',
    'HU',
    'IE',
    'IT',
    'LI',
    'LT',
    'LU',
    'LV',
    'MT',
    'NL',
    'NO',
    'PL',
    'PT',
    'RO',
    'SE',
    'SI',
    'SK',
  ],
  defaultCountry: 'DE',
} as const;
