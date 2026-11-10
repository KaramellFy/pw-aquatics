import type { ReactNode } from 'react';
import { BrandMark } from '@/components/layout/Brand';
import { Container } from '@/components/ui/Container';

interface AuthCardProps {
  title: string;
  lead?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}

/** Centred card used by all sign-in related pages. */
export function AuthCard({ title, lead, children, footer }: AuthCardProps) {
  return (
    <div className="bg-mist">
      <Container className="flex min-h-[70dvh] items-center justify-center py-14 sm:py-20">
        <div className="w-full max-w-md">
          <div className="rounded-card bg-surface p-6 shadow-soft ring-1 ring-line sm:p-10">
            <BrandMark className="h-12 w-auto text-brand-400" />
            <h1 className="mt-6 text-title">{title}</h1>
            {lead && <p className="mt-3 leading-relaxed text-ink-soft">{lead}</p>}
            <div className="mt-8">{children}</div>
          </div>
          {footer && <div className="mt-6 text-center text-[0.9375rem] text-ink-soft">{footer}</div>}
        </div>
      </Container>
    </div>
  );
}
