import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  confirmLabel: string;
  onConfirm: () => void;
  pending?: boolean;
  /** Red confirm button for destructive actions. */
  danger?: boolean;
  children?: ReactNode;
}

/** Asks before a destructive or far-reaching action. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  onConfirm,
  pending = false,
  danger = false,
  children,
}: ConfirmDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} description={description}>
      {children}
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={() => onOpenChange(false)}>
          {t('admin.common.cancel')}
        </Button>
        <Button
          className={danger ? 'bg-danger hover:bg-danger/90' : undefined}
          disabled={pending}
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
