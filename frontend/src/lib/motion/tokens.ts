/**
 * Motion vocabulary of the site. One place for curves and timings, so every animation
 * feels like it belongs to the same product.
 *
 * - Entrances ease out (fast start, gentle landing); things that move from A to B ease in
 *   and out; exits are 20 % shorter than entrances.
 * - Menus and popovers take 150–250 ms. Nothing in everyday UI bounces.
 * - List rows stagger by 30 ms.
 */

/** Cubic-bezier curves as Motion expects them. The same curves exist as CSS variables. */
export const ease = {
  /** Entrances: quick start, soft landing. */
  out: [0.23, 1, 0.32, 1],
  /** Moves from one place to another. */
  inOut: [0.77, 0, 0.175, 1],
} as const;

/** Durations in seconds. (The press feedback lives in CSS: the `press` utility, 120 ms.) */
export const duration = {
  fast: 0.15,
  menu: 0.2,
  base: 0.25,
  slow: 0.4,
} as const;

/** Exits are 20 % shorter than the matching entrance. */
export const exitDuration = (enter: number) => Math.round(enter * 0.8 * 1000) / 1000;

/** Delay between list rows. */
export const STAGGER = 0.03;

/** Popovers and dialogs start slightly smaller and grow from their trigger. */
export const POPOVER_START_SCALE = 0.95;

/** A curve as a CSS / Web Animations easing string. */
export function cssEase(curve: readonly [number, number, number, number]): string {
  return `cubic-bezier(${curve.join(', ')})`;
}

/**
 * Whether the visitor asked their system for less motion. Motion components respect this on
 * their own (`MotionConfig reducedMotion="user"`); animations started by hand must check it.
 */
export function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}
