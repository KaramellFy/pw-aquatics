import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { MotionGlobalConfig } from 'motion/react';
import { afterEach } from 'vitest';
import '@/lib/i18n';

// Journeys with several requests (sign out, checkout) need more than the default second
// when all test files run in parallel on a busy machine.
configure({ asyncUtilTimeout: 4000 });

// Animations finish instantly in tests: exits remove elements right away.
MotionGlobalConfig.skipAnimations = true;

afterEach(() => {
  cleanup();
  document.cookie.split('; ').forEach((cookie) => {
    const name = cookie.split('=')[0];
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
});
