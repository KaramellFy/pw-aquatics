import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface AlertProps {
  tone?: 'info' | 'error' | 'success';
  title?: string;
  children?: ReactNode;
  className?: string;
}

const tones = {
  info: 'border-brand-200 bg-brand-50 text-ink',
  error: 'border-danger/30 bg-danger-soft text-ink',
  success: 'border-success/30 bg-success-soft text-ink',
};

export function Alert({ tone = 'info', title, children, className }: AlertProps) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn('rounded-2xl border px-5 py-4 text-sm leading-relaxed', tones[tone], className)}
    >
      {title && <p className="mb-1 font-medium">{title}</p>}
      {children}
    </div>
  );
}
