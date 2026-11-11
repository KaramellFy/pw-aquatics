import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

/** A white panel for a section of an admin page. */
export function AdminCard({
  title,
  actions,
  children,
  className,
  id,
}: {
  title?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}) {
  return (
    <section
      aria-labelledby={title && id ? `${id}-title` : undefined}
      className={cn('rounded-card border border-line bg-surface p-5 sm:p-6', className)}
    >
      {(title || actions) && (
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          {title && (
            <h2 id={id ? `${id}-title` : undefined} className="text-title">
              {title}
            </h2>
          )}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}
