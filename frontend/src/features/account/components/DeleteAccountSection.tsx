import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { PasswordField } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useDeleteAccount } from '../api/account';

/** Account deletion behind a confirmation dialog that asks for the password. */
export function DeleteAccountSection() {
  const { t } = useTranslation();
  const remove = useDeleteAccount();
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');

  const close = () => {
    setOpen(false);
    setPassword('');
    remove.reset();
  };
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    remove.mutate({ currentPassword: password });
  };

  return (
    <>
      <p className="max-w-prose text-ink-soft">{t('account.profile.deleteText')}</p>
      <Button
        variant="secondary"
        className="mt-5 border-danger/40 text-danger hover:border-danger hover:bg-danger-soft"
        onClick={() => setOpen(true)}
      >
        {t('account.profile.deleteOpen')}
      </Button>

      <Dialog
        open={open}
        onOpenChange={(next) => (next ? setOpen(true) : close())}
        title={t('account.profile.deleteConfirmTitle')}
        description={t('account.profile.deleteConfirmText')}
      >
        <form onSubmit={onSubmit} className="space-y-5">
          {remove.isError && <Alert tone="error">{errorMessage(remove.error)}</Alert>}
          <PasswordField
            label={t('auth.fields.currentPassword')}
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
            <Button variant="secondary" onClick={close}>
              {t('account.profile.cancel')}
            </Button>
            <Button
              type="submit"
              className="bg-danger hover:bg-danger/90"
              disabled={remove.isPending || password.length === 0}
            >
              {t('account.profile.deleteConfirm')}
            </Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
