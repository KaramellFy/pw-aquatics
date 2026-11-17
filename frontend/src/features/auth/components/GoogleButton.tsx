import { useTranslation } from 'react-i18next';
import { useHref } from 'react-router';
import { buttonVariants } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { googleSignInUrl, type GoogleIntent } from '../google';

interface GoogleButtonProps {
  /** Where to come back to, as an app path (`/checkout`); the language is added here. */
  returnTo: string;
  intent?: GoogleIntent;
  label?: string;
  className?: string;
}

/**
 * Google's sign-in button: a link that leaves the app for Google and comes back to `returnTo`.
 * Uses Google's own "G" (unchanged, as Google's branding rules require) next to our button style.
 */
export function GoogleButton({ returnTo, intent = 'sign-in', label, className }: GoogleButtonProps) {
  const { t } = useTranslation();
  const href = useHref(returnTo);
  return (
    <a
      href={googleSignInUrl(href, intent)}
      className={cn(buttonVariants({ variant: 'secondary', size: 'lg' }), 'w-full gap-3', className)}
    >
      <img src="/third-party/google-g.svg" alt="" width={18} height={18} className="size-[18px]" />
      {label ?? t('auth.google.continue')}
    </a>
  );
}
