import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';

/** Loading text or an error with "try again" for a query; renders nothing once data is there. */
export function LoadState({
  isPending,
  isError,
  hasData,
  onRetry,
}: {
  isPending: boolean;
  isError: boolean;
  hasData: boolean;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  if (hasData) return null;
  if (isPending) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }
  if (isError) {
    return (
      <Alert tone="error">
        <p>{t('admin.common.loadError')}</p>
        <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
          {t('admin.common.retry')}
        </Button>
      </Alert>
    );
  }
  return null;
}
