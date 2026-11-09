import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { BrandMark, BrandWordmark } from './Brand';

interface LogoProps {
  className?: string;
  /** "horizontal" for the header, "stacked" for the footer. */
  layout?: 'horizontal' | 'stacked';
}

/** PW Aquatics logo lockup in the brand baby blue. */
export function Logo({ className, layout = 'horizontal' }: LogoProps) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex text-brand-400',
        layout === 'horizontal' ? 'items-center gap-3' : 'flex-col items-start gap-4',
        className,
      )}
    >
      <BrandMark className={layout === 'horizontal' ? 'h-11 w-auto' : 'h-16 w-auto'} />
      <BrandWordmark className={layout === 'horizontal' ? 'h-4 w-auto' : 'h-4 w-auto'} />
      <span className="sr-only">{t('common.brand')}</span>
    </span>
  );
}
