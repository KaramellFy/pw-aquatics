import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { categoryLinks } from '@/components/layout/navigation';
import { Artwork } from '@/components/ui/Artwork';
import { Reveal } from '@/components/ui/Reveal';
import { Section, SectionHeading } from '@/components/ui/Section';

export function CategoryShowcase() {
  const { t } = useTranslation();
  return (
    <Section>
      <SectionHeading eyebrow={t('home.categories.eyebrow')} title={t('home.categories.title')} />
      <ul className="mt-16 grid gap-x-6 gap-y-14 sm:grid-cols-2 lg:mt-20 lg:grid-cols-4">
        {categoryLinks.map((category, index) => (
          <Reveal as="li" key={category.slug} delay={index * 90}>
            <Link to={`/shop/${category.slug}`} className="group block rounded-card">
              <div className="overflow-hidden rounded-card">
                <Artwork
                  tone={category.tone}
                  motif={category.motif}
                  label={t(`categories.${category.key}.name`)}
                  className="aspect-[4/5] transition-transform duration-(--duration-slow) ease-(--ease-out-soft) group-hover:scale-[1.03]"
                />
              </div>
              <h3 className="mt-6 text-2xl tracking-tight">{t(`categories.${category.key}.name`)}</h3>
              <p className="mt-2 leading-relaxed text-muted">{t(`categories.${category.key}.description`)}</p>
              <span className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-brand-700">
                {t('home.categories.cta')}
                <ArrowRight
                  aria-hidden
                  className="size-4 transition-transform duration-(--duration-base) group-hover:translate-x-1"
                />
              </span>
            </Link>
          </Reveal>
        ))}
      </ul>
    </Section>
  );
}
