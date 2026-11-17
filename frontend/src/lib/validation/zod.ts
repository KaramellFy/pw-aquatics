import { z } from 'zod';

/**
 * Zod for the shop's forms. Import `z` from here, never from 'zod' (ESLint enforces it).
 *
 * Without `jitless`, Zod tries `new Function` once to compile faster validators. The shop's
 * Content-Security-Policy forbids that, so the browser reports a policy violation on every form
 * page even though Zod carries on without it.
 */
z.config({ jitless: true });

export { z };
