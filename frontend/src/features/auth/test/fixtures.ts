import type { Session } from '../api/auth';

export const anonymous: Session = {
  authenticated: false,
  user: null,
  twoFactorRequired: false,
  twoFactorSetupRequired: false,
};

/** The password was right; the code from the authenticator app is still missing. */
export const awaitingCode: Session = { ...anonymous, twoFactorRequired: true };

export const anna: Session = {
  authenticated: true,
  user: { id: 'u1', email: 'anna@example.com', firstName: 'Anna', lastName: 'Schmidt', role: 'CUSTOMER' },
  twoFactorRequired: false,
  twoFactorSetupRequired: false,
};

/** An administrator signed in with both steps. */
export const owner: Session = { ...anna, user: { ...anna.user!, role: 'ADMIN' } };

/** An administrator signed in with the password only, who must set up two-step sign-in first. */
export const ownerWithoutTwoFactor: Session = { ...owner, twoFactorSetupRequired: true };
