import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Sheet } from '@/components/ui/Sheet';

interface MobileFiltersProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resultCount: number | undefined;
  children: ReactNode;
}

/**
 * Full-height filter sheet for small screens. It slides in from the left, where the Filters
 * button sits, and can be swiped back. Filters apply immediately; the button at the bottom
 * only closes the sheet and says how many animals match.
 */
export function MobileFilters({ open, onOpenChange, resultCount, children }: MobileFiltersProps) {
  const { t } = useTranslation();
  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('shop.filters.title')}
      side="left"
      className="max-w-sm"
    >
      <div className="flex-1 overflow-y-auto px-5 py-6">{children}</div>
      <div className="border-t border-line p-4">
        <Button className="w-full" onClick={() => onOpenChange(false)}>
          {resultCount === undefined
            ? t('shop.filters.title')
            : t('shop.filters.showResults', { count: resultCount })}
        </Button>
      </div>
    </Sheet>
  );
}
