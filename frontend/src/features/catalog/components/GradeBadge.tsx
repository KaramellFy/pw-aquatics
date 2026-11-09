import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

export function GradeBadge({ name, className }: { name: string; className?: string }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex h-6 shrink-0 items-center rounded-full border border-brand-200 bg-brand-50 px-2.5 text-xs font-medium text-brand-800',
        className,
      )}
    >
      {t('product.grade', { grade: name })}
    </span>
  );
}
