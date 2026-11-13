import { ArrowLeft, ArrowRight, ExternalLink } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { CheckboxField, TextAreaField, TextField } from '@/components/ui/Field';
import { ResponsiveImage } from '@/components/ui/ResponsiveImage';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import {
  useAdminGallery,
  useDeleteGalleryImage,
  useReorderGallery,
  useUpdateGalleryImage,
  useUploadGalleryImage,
  type GalleryImage,
} from '../api/gallery';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

const MAX_BYTES = 15 * 1024 * 1024;
const ACCEPT = 'image/jpeg,image/png,image/webp';

/** Photos for the public gallery page: upload, describe, publish and put in order. */
export function AdminGalleryPage() {
  const { t } = useTranslation();
  const gallery = useAdminGallery();
  const reorder = useReorderGallery();
  const remove = useDeleteGalleryImage();
  const update = useUpdateGalleryImage();
  const errorMessage = useApiErrorMessage();
  const [editing, setEditing] = useState<GalleryImage | null>(null);
  const [editKey, setEditKey] = useState(0);
  const [deleting, setDeleting] = useState<GalleryImage | null>(null);
  const images = gallery.data ?? [];
  const failed = reorder.error ?? remove.error ?? update.error;

  const move = (from: number, to: number) => {
    if (to < 0 || to >= images.length || reorder.isPending) return;
    const ids = images.map((image) => image.id);
    const [moved] = ids.splice(from, 1);
    ids.splice(to, 0, moved!);
    reorder.mutate(ids);
  };
  const togglePublished = (image: GalleryImage) => {
    if (update.isPending) return;
    update.mutate({
      id: image.id,
      input: {
        title: image.title ?? undefined,
        caption: image.caption ?? undefined,
        altText: image.altText,
        published: !image.published,
        version: image.version,
      },
    });
  };

  return (
    <>
      <AdminPageHeader
        title={t('admin.gallery.title')}
        lead={t('admin.gallery.lead')}
        actions={
          <Link
            to="/gallery"
            target="_blank"
            className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
          >
            {t('admin.gallery.viewPage')}
            <ExternalLink aria-hidden className="size-4" />
            <span className="sr-only">{t('common.opensInNewTab')}</span>
          </Link>
        }
      />
      <UploadForm />

      {failed && (
        <Alert tone="error" className="mt-6">
          {errorMessage(failed)}
        </Alert>
      )}
      <div className="mt-6">
        <LoadState
          isPending={gallery.isPending}
          isError={gallery.isError}
          hasData={Boolean(gallery.data)}
          onRetry={() => void gallery.refetch()}
        />
      </div>
      {gallery.data && images.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.gallery.empty')}
        </p>
      )}
      {images.length > 0 && (
        <ol className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {images.map((image, index) => (
            <li key={image.id} className="flex flex-col rounded-card border border-line bg-surface p-3">
              <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-mist">
                <ResponsiveImage
                  image={{ ...image.image, alt: image.altText }}
                  sizes="(min-width: 1280px) 22vw, (min-width: 640px) 45vw, 90vw"
                  className="size-full"
                />
                <span className="absolute top-2 left-2">
                  <Pill tone={image.published ? 'ok' : 'neutral'}>
                    {t(image.published ? 'admin.gallery.published' : 'admin.gallery.hidden')}
                  </Pill>
                </span>
              </div>
              <div className="mt-3 min-w-0 flex-1 px-1">
                <p className="font-medium text-ink">{image.title ?? t('admin.gallery.untitled')}</p>
                {image.caption && <p className="mt-1 line-clamp-2 text-sm text-ink-soft">{image.caption}</p>}
                <p className="mt-1 line-clamp-2 text-xs text-muted">
                  {t('admin.gallery.altShort')}: {image.altText}
                </p>
              </div>
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3">
                <div className="flex gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-disabled={index === 0 || reorder.isPending || undefined}
                    aria-label={t('admin.gallery.moveEarlier', { number: index + 1 })}
                    onClick={() => index > 0 && move(index, index - 1)}
                  >
                    <ArrowLeft aria-hidden className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-disabled={index === images.length - 1 || reorder.isPending || undefined}
                    aria-label={t('admin.gallery.moveLater', { number: index + 1 })}
                    onClick={() => index < images.length - 1 && move(index, index + 1)}
                  >
                    <ArrowRight aria-hidden className="size-4" />
                  </Button>
                </div>
                <div className="flex flex-wrap gap-1">
                  <Button variant="ghost" size="sm" onClick={() => togglePublished(image)}>
                    {t(image.published ? 'admin.gallery.hide' : 'admin.gallery.publish')}
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setEditKey((key) => key + 1);
                      setEditing(image);
                    }}
                  >
                    {t('admin.common.edit')}
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-danger"
                    onClick={() => setDeleting(image)}
                  >
                    {t('admin.common.delete')}
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}

      <EditDialog key={editKey} image={editing} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t('admin.gallery.deleteTitle')}
        description={t('admin.common.deleteText')}
        confirmLabel={t('admin.common.delete')}
        danger
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSettled: () => setDeleting(null) })}
      />
    </>
  );
}

/** Upload one photo with its description; it starts hidden. */
function UploadForm() {
  const { t } = useTranslation();
  const fileId = useId();
  const upload = useUploadGalleryImage();
  const errorMessage = useApiErrorMessage();
  const [file, setFile] = useState<File | null>(null);
  const [altText, setAltText] = useState('');
  const [title, setTitle] = useState('');
  const [caption, setCaption] = useState('');
  const [errors, setErrors] = useState<{ file?: string; altText?: string }>({});
  const [formKey, setFormKey] = useState(0);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (upload.isPending) return;
    const found: { file?: string; altText?: string } = {};
    if (!file) found.file = t('admin.gallery.fileRequired');
    else if (file.size > MAX_BYTES) found.file = t('admin.gallery.fileTooLarge');
    if (!altText.trim()) found.altText = t('admin.common.required');
    setErrors(found);
    if (found.file || found.altText || !file) return;
    upload.mutate(
      {
        file,
        altText: altText.trim(),
        title: title.trim() || undefined,
        caption: caption.trim() || undefined,
      },
      {
        onSuccess: () => {
          setFile(null);
          setAltText('');
          setTitle('');
          setCaption('');
          setFormKey((key) => key + 1);
        },
      },
    );
  };

  return (
    <AdminCard title={t('admin.gallery.upload')} id="gallery-upload">
      <form key={formKey} onSubmit={onSubmit} noValidate className="grid gap-5 lg:grid-cols-2">
        {upload.isError && (
          <Alert tone="error" className="lg:col-span-2">
            {errorMessage(upload.error)}
          </Alert>
        )}
        <div className="space-y-2">
          <label htmlFor={fileId} className="block text-sm font-medium text-ink-soft">
            {t('admin.gallery.file')}
          </label>
          <input
            id={fileId}
            type="file"
            accept={ACCEPT}
            aria-describedby={`${fileId}-hint`}
            aria-invalid={errors.file ? true : undefined}
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            className="block w-full text-sm text-ink-soft file:mr-4 file:h-11 file:cursor-pointer file:rounded-full file:border file:border-ink/20 file:bg-surface file:px-5 file:text-sm file:font-medium file:text-ink hover:file:bg-mist"
          />
          <p id={`${fileId}-hint`} className="text-sm text-muted">
            {t('admin.gallery.fileHint')}
          </p>
          {errors.file && <p className="text-sm text-danger">{errors.file}</p>}
        </div>
        <TextField
          label={t('admin.gallery.altText')}
          hint={t('admin.gallery.altTextHint')}
          maxLength={255}
          value={altText}
          onChange={(event) => setAltText(event.target.value)}
          error={errors.altText}
        />
        <TextField
          label={`${t('admin.gallery.titleLabel')} (${t('admin.common.optional')})`}
          maxLength={150}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
        <TextAreaField
          label={`${t('admin.gallery.caption')} (${t('admin.common.optional')})`}
          rows={2}
          maxLength={500}
          value={caption}
          onChange={(event) => setCaption(event.target.value)}
        />
        <div className="lg:col-span-2">
          <Button
            type="submit"
            aria-disabled={upload.isPending || undefined}
            aria-busy={upload.isPending || undefined}
          >
            {upload.isPending ? t('admin.gallery.uploading') : t('admin.gallery.uploadButton')}
          </Button>
        </div>
      </form>
    </AdminCard>
  );
}

function EditDialog({ image, onClose }: { image: GalleryImage | null; onClose: () => void }) {
  const { t } = useTranslation();
  const update = useUpdateGalleryImage();
  const errorMessage = useApiErrorMessage();
  const [title, setTitle] = useState(image?.title ?? '');
  const [caption, setCaption] = useState(image?.caption ?? '');
  const [altText, setAltText] = useState(image?.altText ?? '');
  const [published, setPublished] = useState(image?.published ?? false);
  const [missingAlt, setMissingAlt] = useState(false);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!image || update.isPending) return;
    if (!altText.trim()) {
      setMissingAlt(true);
      return;
    }
    update.mutate(
      {
        id: image.id,
        input: {
          title: title.trim() || undefined,
          caption: caption.trim() || undefined,
          altText: altText.trim(),
          published,
          version: image.version,
        },
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog
      open={image !== null}
      onOpenChange={(open) => !open && onClose()}
      title={t('admin.gallery.editTitle')}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {update.isError && <Alert tone="error">{errorMessage(update.error)}</Alert>}
        <TextField
          label={t('admin.gallery.altText')}
          hint={t('admin.gallery.altTextHint')}
          maxLength={255}
          value={altText}
          onChange={(event) => setAltText(event.target.value)}
          error={missingAlt ? t('admin.common.required') : undefined}
        />
        <TextField
          label={`${t('admin.gallery.titleLabel')} (${t('admin.common.optional')})`}
          maxLength={150}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
        <TextAreaField
          label={`${t('admin.gallery.caption')} (${t('admin.common.optional')})`}
          rows={3}
          maxLength={500}
          value={caption}
          onChange={(event) => setCaption(event.target.value)}
        />
        <CheckboxField
          label={t('admin.gallery.showOnPage')}
          checked={published}
          onChange={(event) => setPublished(event.target.checked)}
        />
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={onClose}>
            {t('admin.common.cancel')}
          </Button>
          <Button
            type="submit"
            aria-disabled={update.isPending || undefined}
            aria-busy={update.isPending || undefined}
          >
            {update.isPending ? t('admin.common.saving') : t('admin.common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
