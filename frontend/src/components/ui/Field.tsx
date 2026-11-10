import { useId, type ComponentProps, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

const controlBase = [
  'block w-full rounded-xl border border-line-strong bg-surface px-4 text-base text-ink placeholder:text-muted/80',
  'transition-[border-color,box-shadow] duration-(--duration-fast)',
  'hover:border-ink-soft focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none',
  'aria-invalid:border-danger aria-invalid:focus:ring-danger/15',
  'disabled:cursor-not-allowed disabled:opacity-60',
];

interface FieldShellProps {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
  className?: string;
  children: ReactNode;
}

function FieldShell({ id, label, hint, error, className, children }: FieldShellProps) {
  return (
    <div className={cn('space-y-2', className)}>
      <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
        {label}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-hint`} className="text-sm text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function describedBy(id: string, hint?: ReactNode, error?: string) {
  return error ? `${id}-error` : hint ? `${id}-hint` : undefined;
}

type TextFieldProps = ComponentProps<'input'> & { label: ReactNode; hint?: ReactNode; error?: string };

/** Labelled text input with hint and error message wired up for screen readers. */
export function TextField({ label, hint, error, className, id: idProp, ...props }: TextFieldProps) {
  const generatedId = useId();
  const id = idProp ?? generatedId;
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={className}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={cn(controlBase, 'h-12')}
        {...props}
      />
    </FieldShell>
  );
}

type TextAreaFieldProps = ComponentProps<'textarea'> & { label: ReactNode; hint?: ReactNode; error?: string };

export function TextAreaField({
  label,
  hint,
  error,
  className,
  id: idProp,
  rows = 6,
  ...props
}: TextAreaFieldProps) {
  const generatedId = useId();
  const id = idProp ?? generatedId;
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={className}>
      <textarea
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={cn(controlBase, 'resize-y py-3 leading-relaxed')}
        {...props}
      />
    </FieldShell>
  );
}

type SelectFieldProps = ComponentProps<'select'> & { label: ReactNode; hint?: ReactNode; error?: string };

/** Labelled native select (best keyboard and mobile behaviour). */
export function SelectField({
  label,
  hint,
  error,
  className,
  id: idProp,
  children,
  ...props
}: SelectFieldProps) {
  const generatedId = useId();
  const id = idProp ?? generatedId;
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={className}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={cn(controlBase, 'h-12')}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  );
}

type CheckboxFieldProps = Omit<ComponentProps<'input'>, 'type'> & { label: ReactNode; error?: string };

export function CheckboxField({ label, error, className, id: idProp, ...props }: CheckboxFieldProps) {
  const generatedId = useId();
  const id = idProp ?? generatedId;
  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex items-start gap-3">
        <input
          id={id}
          type="checkbox"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className="mt-0.5 size-5 shrink-0 cursor-pointer rounded border-line-strong accent-brand-700"
          {...props}
        />
        <label htmlFor={id} className="cursor-pointer text-sm leading-relaxed text-ink-soft">
          {label}
        </label>
      </div>
      {error && (
        <p id={`${id}-error`} className="pl-8 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
