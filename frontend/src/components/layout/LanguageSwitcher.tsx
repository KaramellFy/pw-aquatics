import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { changeLanguage, SUPPORTED_LANGUAGES } from '@/lib/i18n';
import { cn } from '@/lib/cn';

/** EN / DE switch: a small segmented control whose knob slides to the chosen language. */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { t, i18n } = useTranslation();
  // One knob per switch (the footer and the mobile menu can both be on screen).
  const knob = useId();
  return (
    <div role="group" aria-label={t('common.language')} className={cn('flex items-center gap-1', className)}>
      {SUPPORTED_LANGUAGES.map((language) => {
        const active = i18n.resolvedLanguage === language;
        return (
          <button
            key={language}
            type="button"
            lang={language}
            aria-pressed={active}
            aria-label={t(`common.languages.${language}`)}
            onClick={() => void changeLanguage(language)}
            className={cn(
              'relative inline-flex h-11 min-w-11 press items-center justify-center rounded-full px-3 text-xs font-medium tracking-wide uppercase transition-opacity',
              active ? 'opacity-100' : 'opacity-70 hover:opacity-100',
            )}
          >
            {active && (
              <SlidingIndicator
                layoutId={`language-${knob}`}
                stretch="x"
                className="inset-1 rounded-full"
                markClassName="bg-current/12"
              />
            )}
            <span className="relative z-10">{language}</span>
          </button>
        );
      })}
    </div>
  );
}
