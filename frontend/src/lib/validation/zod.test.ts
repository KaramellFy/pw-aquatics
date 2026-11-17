import { describe, expect, it } from 'vitest';
import { z } from './zod';

describe('zod for the shop', () => {
  it('never tries eval, which the Content-Security-Policy forbids', () => {
    expect(z.config().jitless).toBe(true);
    const schema = z.object({ name: z.string().min(2) });
    expect(schema.safeParse({ name: 'Anna' }).success).toBe(true);
    expect(schema.safeParse({ name: 'A' }).success).toBe(false);
  });
});
