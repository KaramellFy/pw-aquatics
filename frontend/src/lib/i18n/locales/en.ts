import type { Messages } from './de';

/** English UI texts. Must provide exactly the keys defined in `de.ts`. */
export const en: Messages = {
  common: {
    brand: 'Wakup Wirbellose',
    skipToContent: 'Skip to content',
    loading: 'Loading …',
    close: 'Close',
    backToHome: 'Back to home',
    language: 'Language',
    languages: { de: 'Deutsch', en: 'English' },
    imagePlaceholder: 'Placeholder – photo coming soon',
    previousPrice: 'Instead of',
  },
  nav: {
    main: 'Main navigation',
    shop: 'Shop',
    gallery: 'Gallery',
    about: 'About us',
    contact: 'Contact',
    account: 'My account',
    cart: 'Cart',
    openMenu: 'Open menu',
    menu: 'Menu',
  },
  footer: {
    tagline: 'Selected dwarf shrimp and invertebrates – kept with patience, shipped with care.',
    shopTitle: 'Shop',
    infoTitle: 'Information',
    legalTitle: 'Legal',
    allAnimals: 'All animals',
    shipping: 'Shipping & live animals',
    copyright: '© {{year}} Wakup Wirbellose. All rights reserved.',
    vatNote: 'All prices incl. VAT, plus shipping.',
  },
  categories: {
    shrimp: { name: 'Shrimp', description: 'Neocaridina and Caridina in clear colours and lines.' },
    snails: { name: 'Snails', description: 'Hard-working helpers for a balanced tank.' },
    crayfish: { name: 'Crayfish', description: 'Dwarf crayfish with character – for experienced keepers.' },
    crabs: { name: 'Crabs', description: 'Fascinating residents for specialised aquariums.' },
  },
  home: {
    meta: {
      title: 'Wakup Wirbellose – Dwarf shrimp & invertebrates',
      description:
        'Premium dwarf shrimp, snails and crayfish for your aquarium. Healthy animals, transparent grading, careful shipping.',
    },
    hero: {
      eyebrow: 'Ornamental invertebrates for freshwater aquariums',
      title: 'Small creatures.\nGreat care.',
      lead: 'We keep and ship selected dwarf shrimp, snails and crayfish – vividly coloured, healthy, and handled with the calm they deserve.',
      primaryCta: 'Discover the animals',
      secondaryCta: 'Our philosophy',
    },
    categories: {
      eyebrow: 'Collection',
      title: 'From our water to yours',
      cta: 'Discover',
    },
    values: {
      eyebrow: 'Our standard',
      title: 'Quality you can see in the tank',
      items: {
        health: {
          title: 'Healthy animals',
          text: 'Our animals live in stable water conditions and are observed carefully before shipping.',
        },
        grading: {
          title: 'Honest grading',
          text: 'Clear, comprehensible grading of colour and pattern – you know what you get.',
        },
        shipping: {
          title: 'Thoughtful shipping',
          text: 'Insulated packaging, heat or cool packs depending on the weather, and dispatch only on safe weekdays.',
        },
      },
    },
    process: {
      eyebrow: 'The way to you',
      title: 'From our tank to yours',
      steps: {
        select: { title: 'Selection', text: 'We choose every animal for colour, vitality and size.' },
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
          text: 'Express shipping on selected weekdays so nobody spends the weekend in transit.',
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
      title: 'About us – Wakup Wirbellose',
      description: 'Who is behind Wakup Wirbellose and how we care for our animals.',
    },
    eyebrow: 'About us',
    title: 'A passion for the small things',
    lead: 'Wakup Wirbellose grew out of a passion for dwarf shrimp – and the wish to sell them the way we would like to buy them ourselves.',
    story: {
      title: 'Our story',
      paragraphs: [
        'What started with a single tank is now a small, carefully run facility. We keep our animals in stable, well-established aquariums and take our time with every line.',
        'We care about healthy animals, clear colours and happy keepers rather than volume. That is why we describe our grades honestly and only ship when the weather allows it.',
      ],
    },
    principles: {
      title: 'What we stand for',
      items: {
        care: {
          title: 'Species-appropriate care',
          text: 'Stable water parameters, a calm environment, high-quality food.',
        },
        transparency: { title: 'Transparency', text: 'Comprehensible grades, real photos, honest advice.' },
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
      title: 'Gallery – Wakup Wirbellose',
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
      moss: 'Moss and stones – our tank setup',
      yellowFire: 'Neocaridina davidi “Yellow Fire”',
      ramshornSnail: 'Ramshorn snail',
    },
  },
  contact: {
    meta: {
      title: 'Contact – Wakup Wirbellose',
      description: 'Write to us with questions about animals, care, shipping or your order.',
    },
    eyebrow: 'Contact',
    title: 'We look forward to hearing from you',
    lead: 'Whether it is about care, shipping or an order – we usually reply within one to two working days.',
    info: {
      emailTitle: 'Email',
      email: 'hallo@wakup-wirbellose.de',
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
        'When you send this form, your details are processed to handle your enquiry. Learn more in our <privacyLink>privacy policy</privacyLink>.',
      submit: 'Send message',
      submitting: 'Sending …',
      honeypot: 'Please leave this field empty',
    },
    validation: {
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
      impressum: { title: 'Legal notice (Impressum)' },
      datenschutz: { title: 'Privacy policy' },
      agb: { title: 'Terms and conditions' },
      widerruf: { title: 'Right of withdrawal' },
      versand: { title: 'Shipping & live animals' },
    },
  },
  comingSoon: {
    eyebrow: 'Coming soon',
    shop: {
      title: 'The shop is being built',
      text: 'We are preparing our collection. Soon you will find all animals here, with filters for species, colour, grade, price and availability.',
    },
    cart: {
      title: 'Your cart',
      text: 'The cart will open together with the shop.',
    },
    account: {
      title: 'Customer account',
      text: 'Registration, order history and parcel tracking are coming soon.',
    },
    cta: 'View gallery',
  },
  notFound: {
    meta: { title: 'Page not found – Wakup Wirbellose' },
    eyebrow: 'Error 404',
    title: 'This page has gone under',
    text: 'The page you are looking for does not exist (any more). The home page might help you find what you need.',
  },
  error: {
    title: 'Something went wrong',
    text: 'Please reload the page. If the problem persists, feel free to contact us.',
    reload: 'Reload page',
  },
};
