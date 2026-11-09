import { ArrowRight, Shrimp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { ButtonLink } from '@/components/ui/Button';

export function Hero() {
  const { t } = useTranslation();
  const [firstLine, secondLine] = t('home.hero.title').split('\n');

  return (
    <section className="px-3 sm:px-5">
      <div className="relative isolate flex min-h-[min(88svh,56rem)] flex-col justify-end overflow-hidden rounded-[2rem] bg-deep-900 text-paper">
        <Artwork tone="deep" animated className="absolute inset-0 -z-10" />
        {/* faint line motif, decorative only */}
        <Shrimp
          aria-hidden
          strokeWidth={0.5}
          className="absolute top-[6%] right-[4%] -z-10 hidden size-[34rem] max-w-[42%] animate-drift text-paper/15 lg:block"
        />
        <div className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-gradient-to-t from-deep-900/90 to-transparent" />

        <div className="grid gap-12 px-6 pt-32 pb-10 sm:px-12 sm:pb-14 lg:grid-cols-12 lg:px-16 lg:pb-16">
          <div className="lg:col-span-8">
            <p className="animate-fade-up eyebrow text-paper/70">{t('home.hero.eyebrow')}</p>
            <h1 className="mt-6 text-display text-balance">
              <span className="block animate-fade-up [animation-delay:80ms]">{firstLine}</span>
              <span className="block animate-fade-up text-paper/75 italic [animation-delay:160ms]">
                {secondLine}
              </span>
            </h1>
            <p className="mt-8 max-w-xl animate-fade-up text-lg leading-relaxed text-paper/75 [animation-delay:240ms]">
              {t('home.hero.lead')}
            </p>
            <div className="mt-10 flex animate-fade-up flex-wrap gap-3 [animation-delay:320ms]">
              <ButtonLink to="/shop" variant="light" size="lg" className="group">
                {t('home.hero.primaryCta')}
                <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
              </ButtonLink>
              <ButtonLink to="/about" variant="outline-light" size="lg">
                {t('home.hero.secondaryCta')}
              </ButtonLink>
            </div>
          </div>

          {/* Editorial species card */}
          <aside className="hidden animate-fade-up self-end justify-self-end rounded-2xl border border-paper/15 bg-paper/[0.06] p-6 backdrop-blur-md [animation-delay:480ms] lg:col-span-4 lg:block lg:w-72">
            <p className="scientific text-xl">Neocaridina davidi</p>
            <p className="mt-1 text-sm text-paper/60">„Blue Dream“</p>
            <dl className="mt-6 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-paper/15 pt-5 text-sm">
              <dt className="text-paper/55">Temp.</dt>
              <dd className="text-right tabular-nums">18–28 °C</dd>
              <dt className="text-paper/55">pH</dt>
              <dd className="text-right tabular-nums">6,5–8,0</dd>
              <dt className="text-paper/55">GH</dt>
              <dd className="text-right tabular-nums">6–15</dd>
            </dl>
          </aside>
        </div>
      </div>
    </section>
  );
}
