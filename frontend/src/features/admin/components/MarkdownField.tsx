import { useId, useState, type ComponentProps } from 'react';
import { useTranslation } from 'react-i18next';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { cn } from '@/lib/cn';
import { useMarkdownPreview } from '../api/products';

type MarkdownFieldProps = Omit<ComponentProps<'textarea'>, 'value'> & {
  label: string;
  /** The current text (for the preview). */
  value: string;
  error?: string;
};

/**
 * A Markdown text area with a preview tab. The preview is rendered by the server with the
 * same sanitiser the shop uses, so it shows exactly what customers will see.
 */
export function MarkdownField({ label, value, error, className, rows = 8, ...props }: MarkdownFieldProps) {
  const { t } = useTranslation();
  const id = useId();
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const preview = useMarkdownPreview();

  const showPreview = () => {
    setTab('preview');
    preview.mutate(value);
  };

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex flex-wrap items-end justify-between gap-2">
        <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
          {label}
        </label>
        <div role="tablist" aria-label={label} className="flex rounded-full border border-line p-0.5 text-sm">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'write'}
            onClick={() => setTab('write')}
            className={cn(
              'relative min-h-9 press rounded-full px-3',
              tab === 'write' ? 'text-white' : 'text-ink-soft',
            )}
          >
            {tab === 'write' && (
              <SlidingIndicator
                layoutId={`markdown-tab-${id}`}
                className="inset-0 rounded-full"
                markClassName="bg-ink"
              />
            )}
            <span className="relative z-10">{t('admin.product.markdown.write')}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'preview'}
            onClick={showPreview}
            className={cn(
              'relative min-h-9 press rounded-full px-3',
              tab === 'preview' ? 'text-white' : 'text-ink-soft',
            )}
          >
            {tab === 'preview' && (
              <SlidingIndicator
                layoutId={`markdown-tab-${id}`}
                className="inset-0 rounded-full"
                markClassName="bg-ink"
              />
            )}
            <span className="relative z-10">{t('admin.product.markdown.preview')}</span>
          </button>
        </div>
      </div>
      <textarea
        id={id}
        rows={rows}
        value={value}
        hidden={tab !== 'write'}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-hint`}
        className="block w-full resize-y rounded-xl border border-line-strong bg-surface px-4 py-3 text-base leading-relaxed focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none aria-invalid:border-danger"
        {...props}
      />
      {tab === 'preview' && (
        <div
          role="tabpanel"
          aria-busy={preview.isPending}
          className="min-h-32 rounded-xl border border-line bg-mist/60 px-4 py-3"
        >
          {preview.data?.html ? (
            // The HTML is rendered from Markdown and sanitised on the server.
            <div className="rich-text" dangerouslySetInnerHTML={{ __html: preview.data.html }} />
          ) : (
            <p className="text-sm text-muted">
              {preview.isPending ? t('common.loading') : t('admin.product.markdown.empty')}
            </p>
          )}
        </div>
      )}
      <p id={`${id}-hint`} className="text-sm text-muted">
        {t('admin.product.markdown.hint')}
      </p>
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
