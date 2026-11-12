import { ArrowLeft, ArrowRight, ImagePlus, Trash2 } from 'lucide-react';
import { useId, useState, type DragEvent, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonVariants } from '@/components/ui/Button';
import { ResponsiveImage } from '@/components/ui/ResponsiveImage';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { cn } from '@/lib/cn';
import {
  useDeleteImage,
  useReorderImages,
  useUpdateAltText,
  useUploadImage,
  type AdminImage,
  type AdminProduct,
} from '../api/products';
import { AdminCard } from './AdminCard';
import { ConfirmDialog } from './ConfirmDialog';

const MAX_BYTES = 15 * 1024 * 1024;
const ACCEPT = 'image/jpeg,image/png,image/webp';

/**
 * Upload, order, describe and delete a product's photos. The server checks, resizes and
 * re-encodes every upload; this component only collects files and the order.
 */
export function ImageManager({ product }: { product: AdminProduct }) {
  const { t } = useTranslation();
  const inputId = useId();
  const upload = useUploadImage(product.id);
  const reorder = useReorderImages(product.id);
  const remove = useDeleteImage(product.id);
  const errorMessage = useApiErrorMessage();
  const [progress, setProgress] = useState<{ current: number; total: number } | null>(null);
  const [problems, setProblems] = useState<string[]>([]);
  const [dragging, setDragging] = useState<number | null>(null);
  const [dropActive, setDropActive] = useState(false);
  const [deleting, setDeleting] = useState<AdminImage | null>(null);
  const images = product.images;

  const uploadFiles = async (files: File[]) => {
    const found: string[] = [];
    const accepted = files.filter((file) => {
      if (file.size > MAX_BYTES) found.push(t('admin.product.images.tooLarge', { name: file.name }));
      return file.size <= MAX_BYTES;
    });
    for (let i = 0; i < accepted.length; i++) {
      setProgress({ current: i + 1, total: accepted.length });
      try {
        // One after the other, so the photos keep the order they were chosen in.
        await upload.mutateAsync({ file: accepted[i]!, altText: product.name });
      } catch (error) {
        found.push(`${accepted[i]!.name}: ${errorMessage(error)}`);
      }
    }
    setProgress(null);
    setProblems(found);
  };

  const move = (from: number, to: number) => {
    if (to < 0 || to >= images.length || from === to) return;
    const ids = images.map((image) => image.id);
    const [moved] = ids.splice(from, 1);
    ids.splice(to, 0, moved!);
    reorder.mutate(ids);
  };

  const onDropFiles = (event: DragEvent) => {
    event.preventDefault();
    setDropActive(false);
    if (event.dataTransfer.files.length > 0) void uploadFiles([...event.dataTransfer.files]);
  };

  return (
    <AdminCard title={t('admin.product.sections.images')} id="product-images">
      <div
        onDragOver={(event) => {
          if (event.dataTransfer.types.includes('Files')) {
            event.preventDefault();
            setDropActive(true);
          }
        }}
        onDragLeave={() => setDropActive(false)}
        onDrop={onDropFiles}
        className={cn(
          'flex flex-col items-center gap-3 rounded-2xl border-2 border-dashed px-5 py-8 text-center transition-colors',
          dropActive ? 'border-brand-700 bg-brand-50' : 'border-line-strong',
        )}
      >
        <ImagePlus aria-hidden className="size-8 text-brand-700" strokeWidth={1.5} />
        <p id={`${inputId}-hint`} className="max-w-md text-sm text-ink-soft">
          {t('admin.product.images.dropHint')}
        </p>
        {/* The visible button is the file input's label, so there is one control for everyone. */}
        <label
          className={cn(
            buttonVariants({ variant: 'secondary' }),
            'cursor-pointer has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ink',
            progress !== null && 'pointer-events-none opacity-50',
          )}
        >
          {t('admin.product.images.choose')}
          <input
            id={inputId}
            type="file"
            accept={ACCEPT}
            multiple
            disabled={progress !== null}
            className="sr-only"
            aria-describedby={`${inputId}-hint`}
            onChange={(event) => {
              const files = [...(event.target.files ?? [])];
              event.target.value = '';
              if (files.length > 0) void uploadFiles(files);
            }}
          />
        </label>
        {progress && (
          <p role="status" className="text-sm text-ink-soft">
            {t('admin.product.images.uploading', progress)}
          </p>
        )}
      </div>

      {problems.length > 0 && (
        <Alert tone="error" className="mt-4">
          <ul className="list-disc pl-5">
            {problems.map((problem) => (
              <li key={problem}>{problem}</li>
            ))}
          </ul>
        </Alert>
      )}
      {(reorder.isError || remove.isError) && (
        <Alert tone="error" className="mt-4">
          {errorMessage(reorder.error ?? remove.error)}
        </Alert>
      )}

      {images.length === 0 ? (
        <p className="mt-5 text-ink-soft">{t('admin.product.images.empty')}</p>
      ) : (
        <>
          <p className="mt-5 text-sm text-muted">{t('admin.product.images.reorderHint')}</p>
          <ol className="mt-3 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {images.map((image, index) => (
              <li
                key={image.id}
                draggable
                onDragStart={(event) => {
                  setDragging(index);
                  event.dataTransfer.effectAllowed = 'move';
                }}
                onDragEnd={() => setDragging(null)}
                onDragOver={(event) => {
                  if (dragging !== null) event.preventDefault();
                }}
                onDrop={(event) => {
                  if (dragging === null) return;
                  event.preventDefault();
                  event.stopPropagation();
                  move(dragging, index);
                  setDragging(null);
                }}
                className={cn(
                  'flex flex-col rounded-2xl border bg-surface p-3',
                  dragging === index ? 'border-brand-700 opacity-60' : 'border-line',
                )}
              >
                <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-mist">
                  <ResponsiveImage
                    image={{ ...image, alt: image.altText }}
                    sizes="(min-width: 1280px) 20vw, 45vw"
                    className="size-full"
                  />
                  {index === 0 && (
                    <span className="absolute top-2 left-2 rounded-full bg-ink px-2.5 py-0.5 text-xs font-medium text-white">
                      {t('admin.product.images.main')}
                    </span>
                  )}
                </div>
                <AltTextForm productId={product.id} image={image} />
                <div className="mt-3 flex items-center justify-between gap-2 border-t border-line pt-3">
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={index === 0 || reorder.isPending}
                      aria-label={t('admin.product.images.moveEarlier', { number: index + 1 })}
                      onClick={() => move(index, index - 1)}
                    >
                      <ArrowLeft aria-hidden className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={index === images.length - 1 || reorder.isPending}
                      aria-label={t('admin.product.images.moveLater', { number: index + 1 })}
                      onClick={() => move(index, index + 1)}
                    >
                      <ArrowRight aria-hidden className="size-4" />
                    </Button>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-danger hover:bg-danger-soft"
                    aria-label={t('admin.product.images.deleteLabel', { number: index + 1 })}
                    onClick={() => setDeleting(image)}
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        </>
      )}

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t('admin.product.images.deleteTitle')}
        description={t('admin.common.deleteText')}
        confirmLabel={t('admin.common.delete')}
        danger
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSettled: () => setDeleting(null) })}
      />
    </AdminCard>
  );
}

function AltTextForm({ productId, image }: { productId: string; image: AdminImage }) {
  const { t } = useTranslation();
  const id = useId();
  const save = useUpdateAltText(productId);
  const [text, setText] = useState(image.altText);
  const changed = text.trim() !== image.altText;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (text.trim()) save.mutate({ imageId: image.id, altText: text.trim() });
  };

  return (
    <form onSubmit={onSubmit} className="mt-3 space-y-2">
      <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
        {t('admin.product.images.altText')}
      </label>
      <textarea
        id={id}
        rows={2}
        maxLength={255}
        value={text}
        onChange={(event) => setText(event.target.value)}
        aria-describedby={`${id}-hint`}
        className="block w-full resize-y rounded-xl border border-line-strong bg-surface px-3 py-2 text-sm focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none"
      />
      <p id={`${id}-hint`} className="sr-only">
        {t('admin.product.images.altTextHint')}
      </p>
      {changed && (
        <Button type="submit" size="sm" className="min-h-11" disabled={save.isPending || !text.trim()}>
          {t('admin.product.images.saveAltText')}
        </Button>
      )}
    </form>
  );
}
