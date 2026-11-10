import type { Session } from '../api/auth';

export const anonymous: Session = { authenticated: false, user: null };

export const anna: Session = {
  authenticated: true,
  user: { id: 'u1', email: 'anna@example.com', firstName: 'Anna', lastName: 'Schmidt', role: 'CUSTOMER' },
};
