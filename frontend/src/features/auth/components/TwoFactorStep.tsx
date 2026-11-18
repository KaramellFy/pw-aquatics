import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from '@/lib/validation/zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useCancelSignIn, useSecondStep } from '../api/auth';

type Mode = 'app' | 'recovery';

/** Only the shape is checked here; whether the code is right decides the server. */
function codeSchema(t: TFunction, mode: Mode) {
  return z.object({
    code: z
      .string()
      .transform((value) => value.replace(/[\s-]/g, ''))
      .pipe(
        mode === 'app'
          ? z.string().regex(/^\d{6}$/, t('auth.twoFactor.codeShape'))
          : z.string().regex(/^[a-zA-Z2-7]{12}$/, t('auth.twoFactor.recoveryShape')),
      ),
  });
}
type CodeInput = z.input<ReturnType<typeof codeSchema>>;
type CodeValues = z.output<ReturnType<typeof codeSchema>>;

/**
 * Second step of signing in for administrators: the six-digit code from the authenticator app,
 * or one of the recovery codes when the phone is not at hand.
 */
export function TwoFactorStep({ onSignedIn, onEnded }: { onSignedIn: () => void; onEnded: () => void }) {
  const { t, i18n } = useTranslation();
  const [mode, setMode] = useState<Mode>('app');
  const secondStep = useSecondStep();
  const cancel = useCancelSignIn();
  const errorMessage = useApiErrorMessage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => codeSchema(t, mode), [t, i18n.language, mode]);
  const {
    register,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors },
  } = useForm<CodeInput, unknown, CodeValues>({ resolver: zodResolver(schema), defaultValues: { code: '' } });

  const onSubmit = handleSubmit((values) => {
    secondStep.mutate(values.code, {
      onSuccess: (session) => {
        if (session.authenticated) onSignedIn();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.code === 'TWO_FACTOR_EXPIRED') {
          // The session no longer waits for a code; the page shows the password form again.
          onEnded();
          return;
        }
        reset({ code: '' });
        setFocus('code');
      },
    });
  });

  const switchMode = () => {
    setMode(mode === 'app' ? 'recovery' : 'app');
    secondStep.reset();
    reset({ code: '' });
  };

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <p className="text-ink-soft">
        {t(mode === 'app' ? 'auth.twoFactor.appLead' : 'auth.twoFactor.recoveryLead')}
      </p>
      {secondStep.isError && <Alert tone="error">{errorMessage(secondStep.error)}</Alert>}
      {mode === 'app' ? (
        <TextField
          key="app"
          label={t('auth.twoFactor.code')}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={7}
          autoFocus
          error={errors.code?.message}
          {...register('code')}
        />
      ) : (
        <TextField
          key="recovery"
          label={t('auth.twoFactor.recoveryCode')}
          hint={t('auth.twoFactor.recoveryHint')}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          maxLength={20}
          autoFocus
          error={errors.code?.message}
          {...register('code')}
        />
      )}
      <Button
        type="submit"
        size="lg"
        className="w-full"
        aria-busy={secondStep.isPending}
        aria-disabled={secondStep.isPending}
      >
        {secondStep.isPending ? t('auth.twoFactor.submitting') : t('auth.twoFactor.submit')}
      </Button>
      <div className="flex flex-col items-start gap-2 text-sm sm:flex-row sm:justify-between">
        <Button type="button" variant="link" className="text-sm" onClick={switchMode}>
          {t(mode === 'app' ? 'auth.twoFactor.useRecovery' : 'auth.twoFactor.useApp')}
        </Button>
        <Button
          type="button"
          variant="link"
          className="text-sm"
          disabled={cancel.isPending}
          onClick={() => cancel.mutate()}
        >
          {t('auth.twoFactor.otherAccount')}
        </Button>
      </div>
    </form>
  );
}
