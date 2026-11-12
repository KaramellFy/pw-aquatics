import { cubicBezier } from 'motion/react';
import { cssEase, duration, ease, prefersReducedMotion } from '@/lib/motion/tokens';

const DOT_SIZE = 14;
/** Flight time in seconds: long enough to follow, short enough not to hold up the drawer. */
const FLIGHT = 0.5;
/** How far the path bows away from the straight line, as a share of the distance. */
const BOW = 0.3;
/** Space kept between the path and the window edges. */
const EDGE = 8;

/**
 * Sends a small dot from `from` (the button that was pressed) to the cart icon in the header
 * along a curved path, then gives the icon a small bump as it "catches" the dot. The path is a
 * quadratic Bézier curve whose control point sits beside the straight line, on the upper
 * side, so the dot travels like something tossed into the cart. It stays inside the window.
 *
 * Resolves when the dot has landed; immediately with reduced motion or when there is no visible
 * cart icon. It runs on requestAnimationFrame and the browser's animation API rather than
 * Motion's `animate`, which would pull Motion's whole animation engine into the first download.
 */
export async function flyToCart(from: HTMLElement): Promise<void> {
  const target = document.querySelector<HTMLElement>('[data-cart-target]');
  // `animate` is missing in some test environments (jsdom).
  if (!target || typeof target.animate !== 'function' || prefersReducedMotion()) return;
  const start = centreOf(from.getBoundingClientRect());
  const endRect = target.getBoundingClientRect();
  if (endRect.width === 0 || endRect.bottom < 0) return;
  const end = centreOf(endRect);

  const control = controlPoint(start, end);
  const dot = document.createElement('div');
  dot.setAttribute('aria-hidden', 'true');
  dot.className = 'pointer-events-none fixed top-0 left-0 z-[60] rounded-full bg-brand-500 shadow-soft';
  Object.assign(dot.style, { width: `${DOT_SIZE}px`, height: `${DOT_SIZE}px` });
  const place = (progress: number) => {
    const point = bezier(start, control, end, progress);
    // Shrinks over the last part so it seems to drop into the icon.
    const scale = 1 - 0.6 * Math.max(0, (progress - 0.7) / 0.3);
    dot.style.transform = `translate(${point.x - DOT_SIZE / 2}px, ${point.y - DOT_SIZE / 2}px) scale(${scale})`;
  };
  place(0);
  document.body.appendChild(dot);

  const easing = cubicBezier(...ease.inOut);
  await new Promise<void>((resolve) => {
    const startedAt = performance.now();
    const step = (now: number) => {
      const elapsed = Math.min(1, (now - startedAt) / (FLIGHT * 1000));
      place(easing(elapsed));
      if (elapsed < 1) requestAnimationFrame(step);
      else resolve();
    };
    requestAnimationFrame(step);
  });
  dot.remove();
  target.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.12)' }, { transform: 'scale(1)' }], {
    duration: duration.base * 1000,
    easing: cssEase(ease.out),
  });
}

interface Point {
  x: number;
  y: number;
}

function centreOf(rect: DOMRect): Point {
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

/** A point beside the middle of the line, on the side facing up, clamped into the window. */
function controlPoint(start: Point, end: Point): Point {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  // Both perpendiculars have the same length as the line; take the one pointing upwards.
  const normal = -dx <= 0 ? { x: dy, y: -dx } : { x: -dy, y: dx };
  const point = { x: start.x + dx / 2 + normal.x * BOW, y: start.y + dy / 2 + normal.y * BOW };
  return {
    x: Math.min(Math.max(point.x, EDGE), window.innerWidth - EDGE),
    y: Math.min(Math.max(point.y, EDGE), window.innerHeight - EDGE),
  };
}

function bezier(p0: Point, p1: Point, p2: Point, t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
    y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y,
  };
}
