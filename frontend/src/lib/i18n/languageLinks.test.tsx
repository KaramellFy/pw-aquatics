import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher';
import i18n, { languageOfPath, pathInLanguage, pathWithoutLanguage } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import { renderWithProviders } from '@/test/render';

afterEach(async () => {
  await act(() => i18n.changeLanguage('en'));
  localStorage.clear();
});

describe('language links', () => {
  it('reads the language from the link and maps a page between languages', () => {
    expect(languageOfPath('/')).toBe('en');
    expect(languageOfPath('/shop')).toBe('en');
    expect(languageOfPath('/de')).toBe('de');
    expect(languageOfPath('/de/products/blue-dream-high')).toBe('de');
    expect(languageOfPath('/deals')).toBe('en');

    expect(pathInLanguage('/', 'de')).toBe('/de/');
    expect(pathInLanguage('/shop/neocaridina', 'de')).toBe('/de/shop/neocaridina');
    expect(pathInLanguage('/shop', 'en')).toBe('/shop');
    expect(pathWithoutLanguage('/de/shop')).toBe('/shop');
    expect(pathWithoutLanguage('/de')).toBe('/');
    expect(pathWithoutLanguage('/shop')).toBe('/shop');
  });

  it('switches with real links to the same page and remembers the choice', async () => {
    renderWithProviders(<LanguageSwitcher />, { path: '/shop/neocaridina?grade=high' });

    const english = screen.getByRole('link', { name: 'English' });
    const german = screen.getByRole('link', { name: 'Deutsch' });
    expect(english).toHaveAttribute('aria-current', 'true');
    expect(german).toHaveAttribute('href', '/de/shop/neocaridina?grade=high');
    expect(german).toHaveAttribute('hreflang', 'de');

    // jsdom does not load pages; the click only has to remember the choice.
    german.addEventListener('click', (event) => event.preventDefault());
    await userEvent.click(german);
    expect(localStorage.getItem('pwa.language')).toBe('de');
  });

  it('names the canonical link and the page in both languages', async () => {
    await act(() => i18n.changeLanguage('de'));
    renderWithProviders(<PageMeta title="Blue Dream" />, { path: '/products/blue-dream-high' });

    const origin = window.location.origin;
    const head = document.head;
    expect(head.querySelector('link[rel="canonical"]')).toHaveAttribute(
      'href',
      `${origin}/de/products/blue-dream-high`,
    );
    expect(head.querySelector('link[hreflang="en"]')).toHaveAttribute(
      'href',
      `${origin}/products/blue-dream-high`,
    );
    expect(head.querySelector('link[hreflang="de"]')).toHaveAttribute(
      'href',
      `${origin}/de/products/blue-dream-high`,
    );
    expect(head.querySelector('link[hreflang="x-default"]')).toHaveAttribute(
      'href',
      `${origin}/products/blue-dream-high`,
    );
    expect(head.querySelector('meta[property="og:locale"]')).toHaveAttribute('content', 'de_DE');
  });

  it('leaves pages that are not indexed without language alternates', () => {
    renderWithProviders(<PageMeta title="Cart" noIndex />, { path: '/cart' });
    expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
    expect(document.head.querySelector('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
  });

  it('keeps a page out of the index in a language its texts are not written in', async () => {
    const english = renderWithProviders(<PageMeta title="Beta Red" languages={['en']} />, {
      path: '/products/beta-red',
    });
    const head = document.head;
    expect(head.querySelector('meta[name="robots"]')).toBeNull();
    expect(head.querySelector('link[hreflang="en"]')).not.toBeNull();
    expect(head.querySelector('link[hreflang="x-default"]')).not.toBeNull();
    expect(head.querySelector('link[hreflang="de"]')).toBeNull();
    english.unmount();

    // The German link shows the English text until the owner writes the German one.
    await act(() => i18n.changeLanguage('de'));
    renderWithProviders(<PageMeta title="Beta Red" languages={['en']} />, {
      path: '/products/beta-red',
    });
    expect(head.querySelector('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
    expect(head.querySelector('link[rel="canonical"]')).toBeNull();
    expect(head.querySelector('link[rel="alternate"]')).toBeNull();
  });
});
