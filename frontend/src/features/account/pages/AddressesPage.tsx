import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { useSession } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useAddresses, useDeleteAddress, useSetDefaultAddress, type Address } from '../api/account';
import { AddressBlock } from '../components/AddressBlock';
import { AddressFormDialog } from '../components/AddressFormDialog';

const MAX_ADDRESSES = 10;

type Editing = { mode: 'closed' } | { mode: 'add' } | { mode: 'edit'; address: Address };

export function AddressesPage() {
  const { t } = useTranslation();
  const session = useSession();
  const addresses = useAddresses();
  const setDefault = useSetDefaultAddress();
  const remove = useDeleteAddress();
  const errorMessage = useApiErrorMessage();
  const [editing, setEditing] = useState<Editing>({ mode: 'closed' });
  const [deleting, setDeleting] = useState<Address | null>(null);

  const user = session.data?.user;
  const list = addresses.data ?? [];
  const limitReached = list.length >= MAX_ADDRESSES;
  const actionError = setDefault.error ?? remove.error;
  const nameOf = (address: Address) =>
    address.label ?? `${address.street} ${address.houseNumber}, ${address.city}`;

  return (
    <>
      <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-headline">{t('address.title')}</h1>
          <p className="mt-3 text-lg text-ink-soft">{t('address.lead')}</p>
        </div>
        <Button
          className="self-start sm:self-auto"
          disabled={limitReached}
          onClick={() => setEditing({ mode: 'add' })}
        >
          <Plus aria-hidden className="size-4" />
          {t('address.add')}
        </Button>
      </div>
      {limitReached && <p className="mt-3 text-sm text-muted">{t('address.limit')}</p>}

      {addresses.isError && (
        <Alert tone="error" className="mt-8">
          {errorMessage(addresses.error)}
        </Alert>
      )}
      {actionError && (
        <Alert tone="error" className="mt-8">
          {errorMessage(actionError)}
        </Alert>
      )}

      {addresses.isSuccess && list.length === 0 && (
        <div className="mt-10 rounded-card border border-line bg-mist px-6 py-14 text-center">
          <h2 className="text-title">{t('address.emptyTitle')}</h2>
          <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('address.emptyText')}</p>
        </div>
      )}

      <ul className="mt-10 grid gap-5 md:grid-cols-2">
        {list.map((address) => (
          <li key={address.id} className="flex flex-col rounded-card border border-line bg-surface p-6">
            <div className="flex flex-wrap items-center gap-2">
              {address.label && <h2 className="mr-auto font-display text-lg font-normal">{address.label}</h2>}
              {address.defaultShipping && <DefaultBadge>{t('address.defaultShipping')}</DefaultBadge>}
              {address.defaultBilling && <DefaultBadge>{t('address.defaultBilling')}</DefaultBadge>}
            </div>
            <AddressBlock address={address} className="mt-4 flex-1" />

            {(!address.defaultShipping || !address.defaultBilling) && (
              <div className="mt-5 flex flex-wrap gap-2">
                {!address.defaultShipping && (
                  <Button
                    variant="secondary"
                    size="sm"
                    className="min-h-11"
                    disabled={setDefault.isPending}
                    onClick={() => setDefault.mutate({ id: address.id, shipping: true, billing: false })}
                  >
                    {t('address.makeDefaultShipping')}
                  </Button>
                )}
                {!address.defaultBilling && (
                  <Button
                    variant="secondary"
                    size="sm"
                    className="min-h-11"
                    disabled={setDefault.isPending}
                    onClick={() => setDefault.mutate({ id: address.id, shipping: false, billing: true })}
                  >
                    {t('address.makeDefaultBilling')}
                  </Button>
                )}
              </div>
            )}

            <div className="mt-5 flex gap-2 border-t border-line pt-4">
              <Button
                variant="ghost"
                aria-label={t('address.editLabel', { name: nameOf(address) })}
                onClick={() => setEditing({ mode: 'edit', address })}
              >
                <Pencil aria-hidden className="size-4" />
                {t('address.edit')}
              </Button>
              <Button
                variant="ghost"
                className="text-danger hover:bg-danger-soft"
                aria-label={t('address.deleteLabel', { name: nameOf(address) })}
                onClick={() => setDeleting(address)}
              >
                <Trash2 aria-hidden className="size-4" />
                {t('address.delete')}
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <AddressFormDialog
        open={editing.mode !== 'closed'}
        onOpenChange={(open) => !open && setEditing({ mode: 'closed' })}
        address={editing.mode === 'edit' ? editing.address : undefined}
        defaultNames={{ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' }}
      />

      <Dialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t('address.deleteConfirmTitle')}
      >
        {deleting && (
          <>
            <AddressBlock address={deleting} />
            <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Button variant="secondary" onClick={() => setDeleting(null)}>
                {t('address.cancel')}
              </Button>
              <Button
                className="bg-danger hover:bg-danger/90"
                disabled={remove.isPending}
                onClick={() => remove.mutate(deleting.id, { onSettled: () => setDeleting(null) })}
              >
                {t('address.deleteConfirm')}
              </Button>
            </div>
          </>
        )}
      </Dialog>
    </>
  );
}

function DefaultBadge({ children }: { children: string }) {
  return (
    <span className="inline-flex h-6 items-center rounded-full border border-brand-200 bg-brand-50 px-2.5 text-xs font-medium text-brand-800">
      {children}
    </span>
  );
}
