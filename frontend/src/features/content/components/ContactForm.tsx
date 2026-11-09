import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { CheckCircle2 } from 'lucide-react';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextAreaField, TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useSendContactMessage } from '../api/contact';

/**
 * Client-side rules mirror the server's limits for instant feedback only.
 * The backend validates everything again and is authoritative.
 */
function contactSchema(t: TFunction) {
  return z.object({
    name: z
      .string()
      .trim()
      .min(1, t('contact.validation.nameRequired'))
      .max(150, t('contact.validation.tooLong')),
    email: z.email(t('contact.validation.emailInvalid')).max(254, t('contact.validation.tooLong')),
    subject: z
      .string()
      .trim()
      .min(1, t('contact.validation.subjectRequired'))
      .max(200, t('contact.validation.tooLong')),
    orderNumber: z.string().trim().max(20, t('contact.validation.tooLong')),
    message: z
      .string()
      .trim()
      .min(20, t('contact.validation.messageTooShort'))
      .max(5000, t('contact.validation.tooLong')),
    website: z.string(),
  });
}

type ContactFormValues = z.infer<ReturnType<typeof contactSchema>>;
const fieldNames = ['name', 'email', 'subject', 'orderNumber', 'message'] as const;

const emptyValues: ContactFormValues = {
  name: '',
  email: '',
  subject: '',
  orderNumber: '',
  message: '',
  website: '',
};

export function ContactForm() {
  const { t, i18n } = useTranslation();
  // Rebuild the schema when the language changes so messages are translated.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => contactSchema(t), [t, i18n.language]);
  const mutation = useSendContactMessage();

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<ContactFormValues>({ resolver: zodResolver(schema), defaultValues: emptyValues });

  const onSubmit = handleSubmit(async ({ orderNumber, ...values }) => {
    try {
      await mutation.mutateAsync({ ...values, orderNumber: orderNumber || undefined });
      reset(emptyValues);
    } catch (error) {
      // Show server-side field errors next to the matching inputs.
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          const field = fieldNames.find((name) => name === fieldError.field);
          if (field) setError(field, { message: fieldError.message });
        }
      }
    }
  });

  if (mutation.isSuccess) {
    return (
      <div role="status" className="rounded-card border border-brand-100 bg-brand-50 p-10 text-center">
        <CheckCircle2 aria-hidden className="mx-auto size-10 text-brand-700" strokeWidth={1.2} />
        <h2 className="mt-6 text-title">{t('contact.success.title')}</h2>
        <p className="mt-3 text-ink-soft">{t('contact.success.text')}</p>
        <Button variant="secondary" className="mt-8" onClick={() => mutation.reset()}>
          {t('contact.success.again')}
        </Button>
      </div>
    );
  }

  const errorCode = mutation.error instanceof ApiError ? mutation.error.code : undefined;
  const errorMessage =
    errorCode === 'RATE_LIMITED' || errorCode === 'VALIDATION_FAILED' || errorCode === 'NETWORK_ERROR'
      ? t(`contact.errors.${errorCode}`)
      : t('contact.errors.generic');

  // Errors are shown next to each field; the summary tells screen-reader and
  // keyboard users that something needs fixing (the first invalid field gets focus).
  const hasFieldErrors = fieldNames.some((name) => errors[name]);

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-6">
      {mutation.isError ? (
        <Alert tone="error">{errorMessage}</Alert>
      ) : (
        hasFieldErrors && <Alert tone="error">{t('contact.validation.summary')}</Alert>
      )}

      <div className="grid gap-6 sm:grid-cols-2">
        <TextField
          label={t('contact.form.name')}
          autoComplete="name"
          error={errors.name?.message}
          {...register('name')}
        />
        <TextField
          label={t('contact.form.email')}
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
      </div>
      <TextField label={t('contact.form.subject')} error={errors.subject?.message} {...register('subject')} />
      <TextField
        label={t('contact.form.orderNumber')}
        hint={t('contact.form.orderNumberHint')}
        placeholder="PW-2027-000123"
        error={errors.orderNumber?.message}
        {...register('orderNumber')}
      />
      <TextAreaField
        label={t('contact.form.message')}
        error={errors.message?.message}
        {...register('message')}
      />

      {/* Honeypot — visually hidden, ignored by humans and screen readers */}
      <div aria-hidden className="absolute -left-[9999px] h-px w-px overflow-hidden">
        <label htmlFor="contact-website">{t('contact.form.honeypot')}</label>
        <input id="contact-website" type="text" tabIndex={-1} autoComplete="off" {...register('website')} />
      </div>

      <div className="flex flex-col gap-6 pt-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="max-w-md text-sm leading-relaxed text-muted">
          <Trans
            i18nKey="contact.form.privacyNotice"
            components={{
              privacyLink: <Link to="/legal/datenschutz" className="underline underline-offset-4" />,
            }}
          />
        </p>
        <Button type="submit" size="lg" disabled={mutation.isPending} className="shrink-0">
          {mutation.isPending ? t('contact.form.submitting') : t('contact.form.submit')}
        </Button>
      </div>
    </form>
  );
}
