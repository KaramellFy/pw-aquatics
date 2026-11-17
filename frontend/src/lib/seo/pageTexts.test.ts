import { describe, expect, it } from 'vitest';
import { de } from '../i18n/locales/de';
import { en } from '../i18n/locales/en';
import { pageTexts } from './pageTexts';

describe('page texts for the backend', () => {
  it('takes the titles and headings of the app in both languages', () => {
    const english = pageTexts(en);
    expect(english.pages.home.title).toBe(en.home.meta.title);
    expect(english.pages.shop.heading).toBe(en.shop.allAnimals);
    expect(english.product.availability.IN_STOCK).toBe(en.product.availability.IN_STOCK);
    expect(pageTexts(de).nav.gallery).toBe(de.nav.gallery);
  });

  it('leaves out the markup of rich texts', () => {
    expect(en.home.hero.lead).toContain('<days>');
    const lead = pageTexts(en).pages.home.lead;
    expect(lead).not.toMatch(/<[a-z]/);
    expect(lead).toContain('Monday to Wednesday');
  });

  it('has a text for every key in both languages', () => {
    const keys = (value: unknown, prefix = ''): string[] =>
      typeof value === 'object' && value !== null
        ? Object.entries(value).flatMap(([key, child]) => keys(child, `${prefix}${key}.`))
        : [prefix];
    const english = keys(pageTexts(en));
    expect(keys(pageTexts(de))).toEqual(english);
    for (const language of [en, de]) {
      const texts = pageTexts(language);
      for (const key of english) {
        const text = key
          .slice(0, -1)
          .split('.')
          .reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], texts);
        expect(text, key).toEqual(expect.stringMatching(/\S/));
      }
    }
  });
});
