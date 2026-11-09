import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { cn } from '@/lib/cn';

export interface Crumb {
  label: string;
  to?: string;
}

export function Breadcrumbs({
  items,
  label,
  className,
}: {
  items: Crumb[];
  label: string;
  className?: string;
}) {
  return (
    <nav aria-label={label} className={cn('text-sm text-muted', className)}>
      <ol className="flex flex-wrap items-center gap-1.5">
        {items.map((item, index) => {
          const last = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`} className="flex items-center gap-1.5">
              {item.to && !last ? (
                <Link to={item.to} className="rounded transition-colors hover:text-ink">
                  {item.label}
                </Link>
              ) : (
                <span aria-current={last ? 'page' : undefined} className={last ? 'text-ink' : undefined}>
                  {item.label}
                </span>
              )}
              {!last && <ChevronRight aria-hidden className="size-3.5 text-line-strong" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
