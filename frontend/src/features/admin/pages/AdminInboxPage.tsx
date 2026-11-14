import { Mail } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatInstantDateTime } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import {
  useInbox,
  useInboxCounts,
  useSetMessageStatus,
  type ContactMessage,
  type ContactStatus,
} from '../api/inbox';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { FilterTabs } from '../components/FilterTabs';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

type View = 'INBOX' | ContactStatus;
const VIEWS: View[] = ['INBOX', 'NEW', 'ANSWERED', 'SPAM'];

/** Messages from the contact form. Replies are written in the mail program; here they are sorted. */
export function AdminInboxPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const viewParam = params.get('view') as View | null;
  const view: View = viewParam && VIEWS.includes(viewParam) ? viewParam : 'INBOX';
  const page = Number(params.get('page') ?? '0');
  const messages = useInbox(view === 'INBOX' ? null : view, page);
  const counts = useInboxCounts();
  const setStatus = useSetMessageStatus();
  const errorMessage = useApiErrorMessage();

  const go = (next: View, nextPage = 0) => {
    const search = new URLSearchParams();
    if (next !== 'INBOX') search.set('view', next);
    if (nextPage > 0) search.set('page', String(nextPage));
    setParams(search, { replace: true });
  };

  return (
    <>
      <AdminPageHeader title={t('admin.inbox.title')} lead={t('admin.inbox.lead')} />
      <FilterTabs
        id="inbox"
        label={t('admin.inbox.filter')}
        value={view}
        onChange={(next) => go(next)}
        options={VIEWS.map((value) => ({
          value,
          label: t(`admin.inbox.views.${value}`),
          count: value === 'NEW' ? counts.data?.NEW : undefined,
        }))}
        className="mb-6"
      />
      {setStatus.isError && (
        <Alert tone="error" className="mb-6">
          {errorMessage(setStatus.error)}
        </Alert>
      )}
      <LoadState
        isPending={messages.isPending}
        isError={messages.isError}
        hasData={Boolean(messages.data)}
        onRetry={() => void messages.refetch()}
      />
      {messages.data && messages.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.inbox.empty')}
        </p>
      )}
      {messages.data && messages.data.items.length > 0 && (
        <>
          <ul className="space-y-4">
            {messages.data.items.map((message) => (
              <MessageCard
                key={message.id}
                message={message}
                pending={setStatus.isPending && setStatus.variables?.id === message.id}
                onStatus={(status) => setStatus.mutate({ id: message.id, status })}
              />
            ))}
          </ul>
          <div className="mt-6">
            <Pagination
              page={messages.data.page + 1}
              totalPages={messages.data.totalPages}
              onChange={(next) => go(view, next - 1)}
            />
          </div>
        </>
      )}
    </>
  );
}

function MessageCard({
  message,
  pending,
  onStatus,
}: {
  message: ContactMessage;
  pending: boolean;
  onStatus: (status: ContactStatus) => void;
}) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const reply = `mailto:${message.email}?subject=${encodeURIComponent(`Re: ${message.subject}`)}`;
  const act = (status: ContactStatus) => {
    if (!pending) onStatus(status);
  };
  return (
    <li className="rounded-card border border-line bg-surface p-5 sm:p-6" aria-busy={pending || undefined}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-medium text-ink">{message.subject}</h2>
            <Pill tone={message.status === 'NEW' ? 'ok' : 'neutral'}>
              {t(`admin.inbox.status.${message.status}`)}
            </Pill>
          </div>
          <p className="mt-1 text-sm text-ink-soft">
            {message.name} · <span className="break-all">{message.email}</span>
          </p>
          <p className="text-sm text-muted">{formatInstantDateTime(message.createdAt, locale)}</p>
        </div>
        {message.orderNumber && (
          <Link
            to={`/admin/orders/${encodeURIComponent(message.orderNumber)}`}
            className="inline-flex min-h-11 items-center text-sm font-medium text-brand-700 tabular-nums underline-offset-4 hover:underline"
          >
            {t('orders.number', { number: message.orderNumber })}
          </Link>
        )}
      </div>
      <p className="mt-4 whitespace-pre-line text-ink">{message.message}</p>
      <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-4">
        <a
          href={reply}
          onClick={() => message.status === 'NEW' && act('READ')}
          className="inline-flex h-9 items-center gap-2 rounded-full bg-ink px-4 text-sm font-medium text-white hover:bg-ink-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        >
          <Mail aria-hidden className="size-4" />
          {t('admin.inbox.reply')}
        </a>
        {message.status !== 'ANSWERED' && (
          <Button
            size="sm"
            variant="secondary"
            aria-disabled={pending || undefined}
            onClick={() => act('ANSWERED')}
          >
            {t('admin.inbox.markAnswered')}
          </Button>
        )}
        {message.status === 'NEW' && (
          <Button size="sm" variant="ghost" aria-disabled={pending || undefined} onClick={() => act('READ')}>
            {t('admin.inbox.markRead')}
          </Button>
        )}
        {message.status === 'SPAM' ? (
          <Button size="sm" variant="ghost" aria-disabled={pending || undefined} onClick={() => act('NEW')}>
            {t('admin.inbox.notSpam')}
          </Button>
        ) : (
          <Button size="sm" variant="ghost" aria-disabled={pending || undefined} onClick={() => act('SPAM')}>
            {t('admin.inbox.markSpam')}
          </Button>
        )}
      </div>
    </li>
  );
}
