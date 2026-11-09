import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { BrandMark } from '@/components/layout/Brand';
import { Artwork } from '@/components/ui/Artwork';
import { ButtonLink } from '@/components/ui/Button';
import { useIntlLocale } from '@/lib/i18n';

/** Editorial example data for the hero card (replaced by species data from the API in Phase 2). */
const featuredSpecies = {
  scientificName: 'Neocaridina davidi',
  variety: 'Blue Dream',
  ph: [6.5, 8],
  temp: [18, 28],
  gh: [6, 15],
};

export function Hero() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [firstLine, secondLine] = t('home.hero.title').split('\n');
  const decimal = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const range = ([min, max]: number[], format: (n: number) => string = String) =>
    `${format(min ?? 0)}–${format(max ?? 0)}`;

  return (
    <section className="px-3 sm:px-5">
      <div className="relative isolate flex min-h-[min(86svh,54rem)] flex-col justify-end overflow-hidden rounded-[2rem] bg-ice">
        <Artwork tone="ice" animated className="absolute inset-0 -z-10" />
        {/* soft baby-blue light and the brand mark, decorative only */}
        <div className="absolute -top-[25%] -right-[15%] -z-10 aspect-square w-[75%] animate-drift rounded-full bg-brand-300/45 blur-3xl" />
        <BrandMark className="absolute top-[5%] right-[6%] -z-10 h-[24%] w-auto animate-drift text-brand-400/25 lg:top-[6%] lg:right-[7%] lg:h-[58%]" />

        <div className="grid gap-12 px-6 pt-28 pb-10 sm:px-12 sm:pb-14 lg:grid-cols-12 lg:px-16 lg:pb-16">
          <div className="lg:col-span-8">
            <p className="animate-fade-up eyebrow text-brand-700">{t('home.hero.eyebrow')}</p>
            <h1 className="mt-6 text-display text-balance">
              <span className="block animate-fade-up [animation-delay:80ms]">{firstLine}</span>
              <span className="block animate-fade-up text-brand-600 [animation-delay:160ms]">
                {secondLine}
              </span>
            </h1>
            <p className="mt-8 max-w-xl animate-fade-up text-lg leading-relaxed text-ink-soft [animation-delay:240ms]">
              {t('home.hero.lead')}
            </p>
            <div className="mt-10 flex animate-fade-up flex-wrap gap-3 [animation-delay:320ms]">
              <ButtonLink to="/shop" size="lg" className="group">
                {t('home.hero.primaryCta')}
                <ArrowRight aria-hidden className="size-4 transition-transform group-hover:translate-x-0.5" />
              </ButtonLink>
              <ButtonLink to="/about" variant="secondary" size="lg">
                {t('home.hero.secondaryCta')}
              </ButtonLink>
            </div>
          </div>

          {/* Editorial species card */}
          <aside className="hidden animate-fade-up self-end justify-self-end rounded-2xl border border-white/80 bg-white/65 p-6 shadow-soft backdrop-blur-md [animation-delay:480ms] lg:col-span-4 lg:block lg:w-72">
            <p className="scientific text-lg text-ink">{featuredSpecies.scientificName}</p>
            <p className="mt-1 text-sm text-muted">“{featuredSpecies.variety}”</p>
            <dl className="mt-6 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-line pt-5 text-sm">
              <dt className="text-muted">Temp.</dt>
              <dd className="text-right tabular-nums">{range(featuredSpecies.temp)} °C</dd>
              <dt className="text-muted">pH</dt>
              <dd className="text-right tabular-nums">
                {range(featuredSpecies.ph, (n) => decimal.format(n))}
              </dd>
              <dt className="text-muted">GH</dt>
              <dd className="text-right tabular-nums">{range(featuredSpecies.gh)}</dd>
            </dl>
          </aside>
        </div>
      </div>
    </section>
  );
}
