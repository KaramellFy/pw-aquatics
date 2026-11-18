import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from '@/lib/validation/zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';

/** Six digits from the app, or (where allowed) a recovery code; the server decides if it is right. */
function schemaFor(t: TFunction, allowRecovery: boolean) {
  const pattern = allowRecovery ? /^(\d{6}|[a-zA-Z2-7]{12})$/ : /^\d{6}$/;
  return z.object({
    code: z
      .string()
      .transform((value) => value.replace(/[\s-]/g, ''))
      .pipe(
        z
          .string()
          .regex(
            pattern,
            t(allowRecovery ? 'account.twoFactor.codeOrRecoveryShape' : 'auth.twoFactor.codeShape'),
          ),
      ),
  });
}
type Input = z.input<ReturnType<typeof schemaFor>>;
type Values = z.output<ReturnType<typeof schemaFor>>;

/** One field for a code from the authenticator app, with the server's answer shown below. */
export function TwoFactorCodeForm({
  label,
  hint,
  submitLabel,
  allowRecovery = false,
  isPending,
  error,
  onCode,
}: {
  label: string;
  hint?: string;
  submitLabel: string;
  allowRecovery?: boolean;
  isPending: boolean;
  error: unknown;
  onCode: (code: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const errorMessage = useApiErrorMessage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => schemaFor(t, allowRecovery), [t, i18n.language, allowRecovery]);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Input, unknown, Values>({ resolver: zodResolver(schema), defaultValues: { code: '' } });

  return (
    <form
      noValidate
      className="space-y-5"
      onSubmit={handleSubmit((values) => {
        if (!isPending) onCode(values.code);
      })}
    >
      {error != null && <Alert tone="error">{errorMessage(error)}</Alert>}
      <TextField
        label={label}
        hint={hint}
        inputMode={allowRecovery ? 'text' : 'numeric'}
        autoComplete="one-time-code"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={20}
        className="max-w-xs"
        error={errors.code?.message}
        {...register('code')}
      />
      <Button type="submit" aria-busy={isPending} aria-disabled={isPending}>
        {isPending ? t('account.twoFactor.checking') : submitLabel}
      </Button>
    </form>
  );
}
