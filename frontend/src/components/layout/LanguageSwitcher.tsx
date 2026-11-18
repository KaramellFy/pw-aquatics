import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { cn } from '@/lib/cn';
import { pathInLanguage, rememberLanguage, SUPPORTED_LANGUAGES } from '@/lib/i18n';

/**
 * EN / DE switch: a small segmented control whose knob slides to the chosen language. Each
 * option is a real link to the same page in that language (e.g. "/shop" ↔ "/de/shop"), so
 * search engines find both versions; following it loads the page in the other language.
 */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { t, i18n } = useTranslation();
  const { pathname, search, hash } = useLocation();
  // One knob per switch (the footer and the mobile menu can both be on screen).
  const knob = useId();
  return (
    <nav aria-label={t('common.language')} className={cn('flex items-center gap-1', className)}>
      {SUPPORTED_LANGUAGES.map((language) => {
        const active = i18n.resolvedLanguage === language;
        return (
          <a
            key={language}
            href={pathInLanguage(pathname, language) + search + hash}
            hrefLang={language}
            lang={language}
            aria-current={active ? 'true' : undefined}
            onClick={(event) => {
              if (active) {
                event.preventDefault();
                return;
              }
              rememberLanguage(language);
            }}
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
            {/* The name read out starts with the visible "EN"/"DE" (WCAG 2.5.3), then the full name. */}
            <span className="relative z-10">{language}</span>{' '}
            <span className="sr-only">{t(`common.languages.${language}`)}</span>
          </a>
        );
      })}
    </nav>
  );
}
