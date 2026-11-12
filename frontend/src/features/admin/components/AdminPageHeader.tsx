import type { ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router';

interface AdminPageHeaderProps {
  title: string;
  lead?: string;
  actions?: ReactNode;
  back?: { to: string; label: string };
}

/** Title row of an admin page with optional back link and actions. */
export function AdminPageHeader({ title, lead, actions, back }: AdminPageHeaderProps) {
  return (
    <div className="mb-8">
      {back && (
        <Link
          to={back.to}
          className="mb-3 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
        >
          <ArrowLeft aria-hidden className="size-4" />
          {back.label}
        </Link>
      )}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-headline break-words">{title}</h1>
          {lead && <p className="mt-2 max-w-2xl text-ink-soft">{lead}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap gap-3">{actions}</div>}
      </div>
    </div>
  );
}
