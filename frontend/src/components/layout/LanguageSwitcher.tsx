import { useTranslation } from 'react-i18next';
import { changeLanguage, SUPPORTED_LANGUAGES } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export function LanguageSwitcher({ className }: { className?: string }) {
  const { t, i18n } = useTranslation();
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
            onClick={() => void changeLanguage(language)}
            aria-label={t(`common.languages.${language}`)}
            className={cn(
              'rounded-full px-2.5 py-1 text-xs font-medium tracking-widest uppercase transition-colors',
              active ? 'bg-current/10' : 'opacity-60 hover:opacity-100',
            )}
          >
            {language}
          </button>
        );
      })}
    </div>
  );
}
