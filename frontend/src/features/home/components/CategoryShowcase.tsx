import { ArrowUpRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { categoryLinks } from '@/components/layout/navigation';
import { Artwork } from '@/components/ui/Artwork';
import { Container } from '@/components/ui/Container';

/**
 * The animal groups as an index: one ruled row per group. On hover a small picture wipes open
 * from the right (a mask, not a fade) and the arrow nudges towards it.
 */
export function CategoryShowcase() {
  const { t } = useTranslation();
  return (
    <section className="py-20 sm:py-28">
      <Container className="grid gap-10 lg:grid-cols-12">
        <h2 className="text-headline lg:col-span-4">{t('home.categories.title')}</h2>
        <ul className="border-t border-line lg:col-span-8">
          {categoryLinks.map((category) => (
            <li key={category.slug} className="border-b border-line">
              <Link
                to={`/shop/${category.slug}`}
                className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-6 py-6 sm:py-7"
              >
                <span className="min-w-0">
                  <span className="block font-display text-3xl tracking-tight transition-colors duration-(--duration-base) group-hover:text-brand-700 sm:text-4xl">
                    {t(`categories.${category.key}.name`)}
                  </span>
                  <span className="mt-1 block text-ink-soft">
                    {t(`categories.${category.key}.description`)}
                  </span>
                </span>
                <span className="flex items-center gap-4">
                  <Artwork
                    tone={category.tone}
                    motif={category.motif}
                    className="hidden h-16 w-24 rounded-lg transition-[clip-path] duration-(--duration-slow) ease-(--ease-out) [clip-path:inset(0_0_0_100%_round_0.5rem)] group-hover:[clip-path:inset(0_0_0_0_round_0.5rem)] group-focus-visible:[clip-path:inset(0_0_0_0_round_0.5rem)] sm:block"
                  />
                  <ArrowUpRight
                    aria-hidden
                    className="size-6 text-ink-soft transition-transform duration-(--duration-base) ease-(--ease-out) group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-brand-700"
                    strokeWidth={1.5}
                  />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </Container>
    </section>
  );
}
