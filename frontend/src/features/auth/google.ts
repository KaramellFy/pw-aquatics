import { API_BASE } from '@/lib/api/client';

/**
 * Why the customer goes to Google: to sign in (from the sign-in and registration pages) or, when
 * signed in already, to confirm who they are instead of typing a password.
 */
export type GoogleIntent = 'sign-in' | 'confirm';

/**
 * The address that starts signing in with Google. It is opened as a page (not with fetch): the
 * server sends the browser to Google, and Google sends it back to `returnTo`.
 *
 * @param returnTo where to come back to, as the browser shows it (from `useHref`, so German pages
 *     keep their `/de`)
 */
export function googleSignInUrl(returnTo: string, intent: GoogleIntent): string {
  const query = new URLSearchParams({ returnTo, intent });
  return `${API_BASE}/auth/google?${query.toString()}`;
}

/** The `?google=` codes the server sends the browser back with, and the text for each. */
const PROBLEMS = {
  cancelled: 'auth.google.problems.cancelled',
  failed: 'auth.google.problems.failed',
  expired: 'auth.google.problems.expired',
  unverified: 'auth.google.problems.unverified',
  locked: 'auth.google.problems.locked',
  password_only: 'auth.google.problems.passwordOnly',
  other_account: 'auth.google.problems.otherAccount',
  too_many: 'auth.google.problems.tooMany',
  not_connected: 'auth.google.problems.notConnected',
} as const;

export type GoogleProblemKey = (typeof PROBLEMS)[keyof typeof PROBLEMS];

/** The message for a `?google=` code, or null when there is nothing to report (or it was a success). */
export function googleProblem(code: string | null): GoogleProblemKey | null {
  if (!code || code === 'confirmed') {
    return null;
  }
  return code in PROBLEMS ? PROBLEMS[code as keyof typeof PROBLEMS] : PROBLEMS.failed;
}
