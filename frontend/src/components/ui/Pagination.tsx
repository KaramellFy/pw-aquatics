import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';

interface PaginationProps {
  /** 1-based. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  const { t } = useTranslation();
  if (totalPages <= 1) return null;
  return (
    <nav aria-label={t('shop.pagination.label')} className="flex items-center justify-center gap-4">
      <Button variant="secondary" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        <ChevronLeft aria-hidden className="size-4" />
        {t('shop.pagination.previous')}
      </Button>
      <p className="text-sm text-muted tabular-nums" aria-live="polite">
        {t('shop.pagination.status', { page, total: totalPages })}
      </p>
      <Button variant="secondary" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        {t('shop.pagination.next')}
        <ChevronRight aria-hidden className="size-4" />
      </Button>
    </nav>
  );
}
