import { Eye, EyeOff } from 'lucide-react';
import { useId, useState, type ComponentProps, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

type PasswordFieldProps = Omit<ComponentProps<'input'>, 'type'> & {
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
};

/**
 * Password input with a show/hide toggle (instead of a "repeat password" field).
 * Works with password managers: set `autoComplete` to "current-password" or "new-password".
 */
export function PasswordField({ label, hint, error, className, id: idProp, ...props }: PasswordFieldProps) {
  const { t } = useTranslation();
  const generatedId = useId();
  const id = idProp ?? generatedId;
  const [visible, setVisible] = useState(false);
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div className={cn('space-y-2', className)}>
      <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'block h-12 w-full rounded-xl border border-line-strong bg-surface pr-13 pl-4 text-base text-ink',
            'transition-[border-color,box-shadow] duration-(--duration-fast)',
            'hover:border-ink-soft focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none',
            'aria-invalid:border-danger aria-invalid:focus:ring-danger/15',
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? t('auth.fields.hidePassword') : t('auth.fields.showPassword')}
          aria-pressed={visible}
          className="absolute top-0.5 right-0.5 inline-flex size-11 items-center justify-center rounded-[0.65rem] text-ink-soft hover:bg-brand-50 active:bg-brand-100"
        >
          {visible ? (
            <EyeOff aria-hidden className="size-5" strokeWidth={1.5} />
          ) : (
            <Eye aria-hidden className="size-5" strokeWidth={1.5} />
          )}
        </button>
      </div>
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
