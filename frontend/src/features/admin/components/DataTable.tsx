import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

/**
 * A plain, accessible table in a horizontally scrolling frame, so wide admin tables stay
 * usable on a phone.
 */
export function DataTable({
  caption,
  head,
  children,
}: {
  caption: string;
  head: ReactNode;
  children: ReactNode;
}) {
  return (
    // `relative` keeps visually hidden labels inside the scrolling frame on narrow screens.
    <div className="relative overflow-x-auto rounded-card border border-line bg-surface">
      <table className="w-full min-w-[40rem] text-left text-[0.9375rem]">
        <caption className="sr-only">{caption}</caption>
        <thead className="border-b border-line text-xs tracking-wide text-muted uppercase">{head}</thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  );
}

export function Th({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th scope="col" className={cn('px-4 py-3 font-medium', className)}>
      {children}
    </th>
  );
}

export function Td({ children, className }: { children?: ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}
