/**
 * English UI texts (default language). This object defines the shape of all
 * translations; `de.ts` must provide exactly the same keys (a test enforces it).
 *
 * Marketing copy is a first draft and must be reviewed by the owner.
 */
export const en = {
  common: {
    brand: 'PW Aquatics',
    skipToContent: 'Skip to content',
    loading: 'Loading …',
    close: 'Close',
    backToHome: 'Back to home',
    language: 'Language',
    languages: { en: 'English', de: 'Deutsch' },
    imagePlaceholder: 'Placeholder – photo coming soon',
    previousPrice: 'Was',
  },
  nav: {
    main: 'Main navigation',
    shop: 'Shop',
    gallery: 'Gallery',
    about: 'About',
    contact: 'Contact',
    account: 'My account',
    cart: 'Cart',
    openMenu: 'Open menu',
    menu: 'Menu',
  },
  footer: {
    tagline: 'Ornamental shrimp and aquatic invertebrates – kept with patience, shipped with care.',
    shopTitle: 'Shop',
    infoTitle: 'Information',
    legalTitle: 'Legal',
    allAnimals: 'All animals',
    shipping: 'Shipping & live arrival',
    copyright: '© {{year}} PW Aquatics. All rights reserved.',
    vatNote: 'All prices incl. VAT, plus shipping.',
  },
  categories: {
    shrimp: { name: 'Shrimp', description: 'Neocaridina and Caridina in clean colours and strong lines.' },
    snails: { name: 'Snails', description: 'Quiet helpers that keep a tank in balance.' },
    crayfish: { name: 'Crayfish', description: 'Dwarf crayfish with real character.' },
    crabs: { name: 'Crabs', description: 'Fascinating residents for specialised setups.' },
  },
  home: {
    meta: {
      title: 'PW Aquatics – Ornamental shrimp & aquatic invertebrates',
      description:
        'Premium dwarf shrimp, snails and crayfish for your aquarium. Healthy animals, honest grading and careful live shipping.',
    },
    hero: {
      eyebrow: 'Ornamental shrimp & invertebrates',
      title: 'Clear water.\nVivid life.',
      lead: 'Hand-selected dwarf shrimp, snails and crayfish – raised in stable water, graded honestly and shipped with care.',
      primaryCta: 'Discover the animals',
      secondaryCta: 'Our approach',
    },
    categories: {
      eyebrow: 'Collection',
      title: 'From our water to yours',
      cta: 'Explore',
    },
    featured: {
      eyebrow: 'Featured',
      title: 'Hand-picked from our tanks',
      cta: 'Shop all animals',
    },
    values: {
      eyebrow: 'Our standard',
      title: 'Quality you can see in the tank',
      items: {
        health: {
          title: 'Healthy animals',
          text: 'Our animals live in stable, mature tanks and are observed closely before they are offered for sale.',
        },
        grading: {
          title: 'Honest grading',
          text: 'Clear, consistent grading of colour and pattern, so you know exactly what you receive.',
        },
        shipping: {
          title: 'Thoughtful shipping',
          text: 'Insulated boxes, heat or cool packs depending on the weather, and dispatch only on safe weekdays.',
        },
      },
    },
    process: {
      eyebrow: 'The way to you',
      title: 'From our tank to yours',
      steps: {
        select: { title: 'Selection', text: 'Every animal is chosen for colour, vitality and size.' },
        acclimate: {
          title: 'Observation',
          text: 'New animals spend time in separate tanks before they go on sale.',
        },
        pack: {
          title: 'Packing',
          text: 'Oxygen-rich bags, insulated boxes and seasonal temperature protection.',
        },
        ship: {
          title: 'Shipping',
          text: 'Express shipping on selected weekdays, so nobody spends a weekend in transit.',
        },
      },
    },
    gallery: {
      eyebrow: 'Gallery',
      title: 'A look into our tanks',
      cta: 'View gallery',
    },
    closing: {
      title: 'Questions about care or your order?',
      text: 'We are happy to help – before you buy and after.',
      cta: 'Get in touch',
    },
  },
  about: {
    meta: {
      title: 'About – PW Aquatics',
      description: 'Who is behind PW Aquatics and how we care for our animals.',
    },
    eyebrow: 'About',
    title: 'Small animals, taken seriously',
    lead: 'PW Aquatics grew out of a passion for dwarf shrimp – and the wish to sell them the way we would want to buy them ourselves.',
    story: {
      title: 'Our story',
      paragraphs: [
        'What started with a single tank is now a small, carefully run facility. Our animals live in stable, well-established aquariums, and we take our time with every line.',
        'We care about healthy animals, clean colours and happy keepers rather than volume. That is why we grade honestly and only ship when the weather allows it.',
      ],
    },
    principles: {
      title: 'What we stand for',
      items: {
        care: {
          title: 'Species-appropriate care',
          text: 'Stable water parameters, a calm environment and high-quality food.',
        },
        transparency: { title: 'Transparency', text: 'Consistent grades, real photos and honest advice.' },
        responsibility: {
          title: 'Responsibility',
          text: 'No trade in invasive or protected species. Shipping only under safe conditions.',
        },
      },
    },
    note: 'Texts and photos on this page are placeholders and will be replaced before launch.',
  },
  gallery: {
    meta: {
      title: 'Gallery – PW Aquatics',
      description: 'Impressions from our tanks: shrimp, snails and crayfish.',
    },
    eyebrow: 'Gallery',
    title: 'Impressions from our tanks',
    lead: 'Colours, textures and small moments under water.',
    previous: 'Previous image',
    next: 'Next image',
    openImage: 'Enlarge image: {{title}}',
    counter: '{{current}} of {{total}}',
    items: {
      blueDream: 'Neocaridina davidi “Blue Dream”',
      redFire: 'Neocaridina davidi “Red Fire”',
      crystalRed: 'Caridina cantonensis “Crystal Red”',
      tigerShrimp: 'Caridina mariae “Tiger”',
      racingSnail: 'Nerite snail on driftwood',
      dwarfCrayfish: 'Dwarf crayfish in moss',
      moss: 'Moss and stone – our tank setup',
      yellowFire: 'Neocaridina davidi “Yellow Fire”',
      ramshornSnail: 'Ramshorn snail',
    },
  },
  contact: {
    meta: {
      title: 'Contact – PW Aquatics',
      description: 'Write to us with questions about animals, care, shipping or your order.',
    },
    eyebrow: 'Contact',
    title: 'We would love to hear from you',
    lead: 'Questions about care, shipping or an order? We usually reply within one to two working days.',
    info: {
      emailTitle: 'Email',
      responseTitle: 'Response time',
      response: '1–2 working days',
      shippingTitle: 'Shipping days',
      shipping: 'Monday to Wednesday',
    },
    form: {
      name: 'Name',
      email: 'Email address',
      subject: 'Subject',
      orderNumber: 'Order number (optional)',
      orderNumberHint: 'If your message is about an order.',
      message: 'Message',
      privacyNotice:
        'When you send this form, your details are processed to answer your enquiry. Learn more in our <privacyLink>privacy policy</privacyLink>.',
      submit: 'Send message',
      submitting: 'Sending …',
      honeypot: 'Please leave this field empty',
    },
    validation: {
      summary: 'Please correct the highlighted fields.',
      nameRequired: 'Please enter your name.',
      emailInvalid: 'Please enter a valid email address.',
      subjectRequired: 'Please enter a subject.',
      messageTooShort: 'Your message should be at least 20 characters long.',
      tooLong: 'This text is too long.',
    },
    success: {
      title: 'Thank you for your message!',
      text: 'We have received it and will get back to you as soon as possible.',
      again: 'Send another message',
    },
    errors: {
      RATE_LIMITED: 'You have sent several messages in a short time. Please try again later.',
      VALIDATION_FAILED: 'Please check your input.',
      NETWORK_ERROR: 'No connection to the server. Please check your internet connection.',
      generic: 'Your message could not be sent right now. Please try again later or email us directly.',
    },
  },
  legal: {
    placeholder:
      'This legal text will be prepared with legal advice before launch. Until then, this page is a placeholder.',
    pages: {
      imprint: { title: 'Legal notice' },
      privacy: { title: 'Privacy policy' },
      terms: { title: 'Terms and conditions' },
      withdrawal: { title: 'Right of withdrawal' },
      shipping: { title: 'Shipping & live arrival' },
    },
  },
  shop: {
    meta: {
      title: 'Shop – PW Aquatics',
      description:
        'Dwarf shrimp, snails, crayfish and crabs. Filter by species, colour, grade, price and availability.',
    },
    eyebrow: 'Shop',
    allAnimals: 'All animals',
    lead: 'Healthy animals from stable tanks, graded honestly and shipped with care.',
    results_one: '{{count}} animal',
    results_other: '{{count}} animals',
    searchLabel: 'Search the shop',
    searchPlaceholder: 'Search species or colour …',
    filters: {
      title: 'Filters',
      open: 'Filters',
      openWithCount: 'Filters ({{count}})',
      categories: 'Categories',
      species: 'Species',
      colour: 'Colour',
      grade: 'Grade',
      availability: 'Availability',
      availabilityAll: 'All',
      availabilityInStock: 'In stock',
      availabilityComingSoon: 'Coming soon',
      price: 'Price (€)',
      priceMin: 'Min',
      priceMax: 'Max',
      priceApply: 'Apply',
      clearAll: 'Clear all',
      remove: 'Remove filter: {{label}}',
      showResults_one: 'Show {{count}} animal',
      showResults_other: 'Show {{count}} animals',
      optionWithCount: '{{label}} ({{count}})',
    },
    sort: {
      label: 'Sort by',
      relevance: 'Recommended',
      newest: 'Newest',
      'price-asc': 'Price: low to high',
      'price-desc': 'Price: high to low',
      name: 'Name',
      grade: 'Best grade',
    },
    empty: {
      title: 'No animals match these filters',
      text: 'Try removing a filter or two – or have a look at everything we have right now.',
      reset: 'Clear filters',
    },
    error: {
      text: 'The shop could not be loaded right now. Please check your connection and try again.',
      retry: 'Try again',
    },
    pagination: {
      label: 'Pagination',
      previous: 'Previous',
      next: 'Next',
      status: 'Page {{page}} of {{total}}',
    },
  },
  product: {
    from: 'from {{price}}',
    availability: {
      IN_STOCK: 'In stock',
      LOW_STOCK: 'Only a few left',
      COMING_SOON: 'Coming soon',
      OUT_OF_STOCK: 'Sold out',
    },
    expectedFrom: 'Expected from {{date}}',
    grade: 'Grade {{grade}}',
    colours: 'Colours',
    packSize: 'Pack size',
    soldOut: 'Sold out',
    addToCart: 'Add to cart',
    cartSoon: 'Online ordering opens soon. Until then, contact us to reserve animals.',
    reserve: 'Reserve via contact form',
    priceNote: 'incl. VAT, plus <shippingLink>shipping</shippingLink>',
    description: 'About this animal',
    care: 'Care parameters',
    careFor: 'Keeping {{name}}',
    speciesLink: 'Species profile',
    related: 'You might also like',
    gallery: 'Product images',
    shipping: {
      days: 'Dispatched Monday to Wednesday only',
      packaging: 'Insulated box, heat or cool pack by season',
      acclimate: 'Acclimatise slowly – instructions included',
    },
    notFound: {
      title: 'This animal is not available',
      text: 'It may have been sold or removed from the shop. Have a look at what is available right now.',
      cta: 'Back to the shop',
    },
  },
  care: {
    temperature: 'Temperature',
    ph: 'pH',
    gh: 'GH',
    kh: 'KH',
    tds: 'TDS',
    size: 'Adult size',
    lifespan: 'Lifespan',
    difficulty: 'Difficulty',
    origin: 'Origin',
    months: '{{count}} months',
    millimetres: '{{count}} mm',
    levels: { BEGINNER: 'Beginner', INTERMEDIATE: 'Intermediate', ADVANCED: 'Advanced' },
  },
  species: {
    eyebrow: 'Species profile',
    care: 'Care parameters',
    available: 'Available now',
    viewAll: 'View all in the shop',
    none: 'No animals of this species are available right now.',
  },
  comingSoon: {
    eyebrow: 'Coming soon',
    cart: {
      title: 'Your cart',
      text: 'The cart opens together with the shop.',
    },
    account: {
      title: 'Customer account',
      text: 'Registration, order history and parcel tracking are coming soon.',
    },
    cta: 'View gallery',
  },
  notFound: {
    meta: { title: 'Page not found – PW Aquatics' },
    eyebrow: 'Error 404',
    title: 'This page has drifted away',
    text: 'The page you are looking for does not exist (any more). The home page might help you find what you need.',
  },
  error: {
    title: 'Something went wrong',
    text: 'Please reload the page. If the problem persists, feel free to contact us.',
    reload: 'Reload page',
  },
};

/** The shape every locale must implement. */
type DeepStringRecord<T> = {
  [K in keyof T]: T[K] extends string ? string : T[K] extends string[] ? string[] : DeepStringRecord<T[K]>;
};
export type Messages = DeepStringRecord<typeof en>;
