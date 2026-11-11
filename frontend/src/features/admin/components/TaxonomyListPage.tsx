import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { AdminPageHeader } from './AdminPageHeader';
import { ConfirmDialog } from './ConfirmDialog';
import { DataTable, Td, Th } from './DataTable';
import { LoadState } from './LoadState';

export interface Column<T> {
  header: string;
  cell: (item: T) => ReactNode;
  className?: string;
}

interface TaxonomyListPageProps<T extends { id: string }> {
  title: string;
  lead: string;
  addLabel: string;
  emptyText: string;
  query: { data?: T[]; isPending: boolean; isError: boolean; refetch: () => unknown };
  columns: Column<T>[];
  nameOf: (item: T) => string;
  /** Opens the editor for a new (undefined) or existing item; ignored when `form` is not given. */
  form?: {
    addTitle: string;
    editTitle: string;
    render: (item: T | undefined, close: () => void) => ReactNode;
  };
  /** Link-based editing instead of a dialog (species). */
  editHref?: (item: T | undefined) => string;
  remove: {
    mutate: (id: string, options: { onSettled: () => void }) => void;
    isPending: boolean;
    error: unknown;
    reset: () => void;
  };
}

/**
 * The shared page for categories, species, colours and grades: a table, an add/edit dialog
 * and deletion with a confirmation. The server refuses to delete what is still in use.
 */
export function TaxonomyListPage<T extends { id: string }>({
  title,
  lead,
  addLabel,
  emptyText,
  query,
  columns,
  nameOf,
  form,
  editHref,
  remove,
}: TaxonomyListPageProps<T>) {
  const { t } = useTranslation();
  const errorMessage = useApiErrorMessage();
  const [editing, setEditing] = useState<{ item?: T } | null>(null);
  const [deleting, setDeleting] = useState<T | null>(null);
  const items = query.data ?? [];

  const addButton = editHref ? (
    <ButtonLink to={editHref(undefined)}>
      <Plus aria-hidden className="size-4" />
      {addLabel}
    </ButtonLink>
  ) : (
    <Button onClick={() => setEditing({})}>
      <Plus aria-hidden className="size-4" />
      {addLabel}
    </Button>
  );

  return (
    <>
      <AdminPageHeader title={title} lead={lead} actions={addButton} />
      {Boolean(remove.error) && (
        <Alert tone="error" className="mb-6">
          {errorMessage(remove.error)}
        </Alert>
      )}
      <LoadState
        isPending={query.isPending}
        isError={query.isError}
        hasData={Boolean(query.data)}
        onRetry={() => void query.refetch()}
      />
      {query.data && items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {emptyText}
        </p>
      )}
      {items.length > 0 && (
        <DataTable
          caption={title}
          head={
            <tr>
              {columns.map((column) => (
                <Th key={column.header} className={column.className}>
                  {column.header}
                </Th>
              ))}
              <Th>
                <span className="sr-only">{t('admin.common.actions')}</span>
              </Th>
            </tr>
          }
        >
          {items.map((item) => (
            <tr key={item.id}>
              {columns.map((column) => (
                <Td key={column.header} className={column.className}>
                  {column.cell(item)}
                </Td>
              ))}
              <Td>
                <div className="flex justify-end gap-1">
                  {editHref ? (
                    <ButtonLink
                      to={editHref(item)}
                      variant="ghost"
                      aria-label={t('admin.common.editLabel', { name: nameOf(item) })}
                    >
                      <Pencil aria-hidden className="size-4" />
                      {t('admin.common.edit')}
                    </ButtonLink>
                  ) : (
                    <Button
                      variant="ghost"
                      aria-label={t('admin.common.editLabel', { name: nameOf(item) })}
                      onClick={() => setEditing({ item })}
                    >
                      <Pencil aria-hidden className="size-4" />
                      {t('admin.common.edit')}
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-danger hover:bg-danger-soft"
                    aria-label={t('admin.common.deleteLabel', { name: nameOf(item) })}
                    onClick={() => {
                      remove.reset();
                      setDeleting(item);
                    }}
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </Button>
                </div>
              </Td>
            </tr>
          ))}
        </DataTable>
      )}

      {form && (
        <Dialog
          open={editing !== null}
          onOpenChange={(open) => !open && setEditing(null)}
          title={editing?.item ? form.editTitle : form.addTitle}
        >
          {editing && form.render(editing.item, () => setEditing(null))}
        </Dialog>
      )}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={deleting ? t('admin.common.deleteTitle', { name: nameOf(deleting) }) : ''}
        description={t('admin.common.deleteText')}
        confirmLabel={t('admin.common.delete')}
        danger
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSettled: () => setDeleting(null) })}
      />
    </>
  );
}
