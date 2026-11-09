import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';

interface MobileFiltersProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resultCount: number | undefined;
  children: ReactNode;
}

/** Full-height filter drawer for small screens. Filters apply immediately. */
export function MobileFilters({ open, onOpenChange, resultCount, children }: MobileFiltersProps) {
  const { t } = useTranslation();
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-ink/30 backdrop-blur-sm" />
        <Dialog.Content className="fixed inset-y-0 left-0 z-50 flex w-full max-w-sm flex-col bg-surface shadow-lift focus:outline-none">
          <div className="flex items-center justify-between border-b border-line px-5 py-3">
            <Dialog.Title className="font-display text-xl">{t('shop.filters.title')}</Dialog.Title>
            <Dialog.Close
              className="-mr-2 inline-flex size-11 items-center justify-center rounded-full hover:bg-brand-50"
              aria-label={t('common.close')}
            >
              <X className="size-5" strokeWidth={1.5} />
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">{t('shop.filters.title')}</Dialog.Description>
          <div className="flex-1 overflow-y-auto px-5 py-6">{children}</div>
          <div className="border-t border-line p-4">
            <Dialog.Close asChild>
              <Button className="w-full">
                {resultCount === undefined
                  ? t('shop.filters.title')
                  : t('shop.filters.showResults', { count: resultCount })}
              </Button>
            </Dialog.Close>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
