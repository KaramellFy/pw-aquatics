import { LazyMotion, MotionConfig } from 'motion/react';
import type { ReactNode } from 'react';

const loadFeatures = () => import('./features').then((module) => module.default);

/**
 * Animation setup for the whole app. The engine loads lazily (components use `m.*`, which
 * render normally until it has arrived), and visitors who ask their system for reduced
 * motion get no transform animations — only opacity changes remain.
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  return (
    <LazyMotion features={loadFeatures} strict>
      <MotionConfig reducedMotion="user">{children}</MotionConfig>
    </LazyMotion>
  );
}
