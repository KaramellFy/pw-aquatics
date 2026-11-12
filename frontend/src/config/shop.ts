/**
 * Shop-wide constants that are not translated.
 * Values marked as placeholders must be confirmed by the owner before launch.
 */
export const shopConfig = {
  name: 'PW Aquatics',
  /** Placeholder — replace with the real contact address before launch. */
  email: 'philwakup@gmail.com',
  /**
   * Countries offered in the address form (ISO 3166-1 alpha-2): the EU member states and
   * the United Kingdom, where the shop delivers. This only shapes the form; which shipping
   * methods exist for a country is decided by the server at checkout.
   */
  addressCountries: [
    'AT',
    'BE',
    'BG',
    'CY',
    'CZ',
    'DE',
    'DK',
    'EE',
    'ES',
    'FI',
    'FR',
    'GB',
    'GR',
    'HR',
    'HU',
    'IE',
    'IT',
    'LT',
    'LU',
    'LV',
    'MT',
    'NL',
    'PL',
    'PT',
    'RO',
    'SE',
    'SI',
    'SK',
  ],
  defaultCountry: 'DE',
  /**
   * Weekdays (ISO, 1 = Monday) on which live animals are sent, as described on the home
   * page. Display text only: checkout uses the server's dispatch calendar.
   */
  liveDispatchWeekdays: [1, 2, 3],
  /** Calendar dates (payment deadline, order date) are shown in the shop's time zone. */
  timeZone: 'Europe/Berlin',
} as const;
