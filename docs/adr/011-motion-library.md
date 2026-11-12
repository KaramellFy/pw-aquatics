# ADR-011: Motion for React for interface animation

- **Status:** accepted
- **Date:** 2026-11-12

## Context

The shop worked but felt generic: fade-up-on-scroll everywhere, a selection highlight that
blinked from one option to the next, dialogs that popped in from the middle of the screen, and
an "Add to cart" that gave no sense of where the animal went. The owner asked for a reworked
look with purposeful animation, following these rules: ease-out entrances, ease-in-out moves,
exits 20 % shorter than entrances, 150–250 ms menus, 30 ms list staggers, 0.97 press scale,
popovers from 0.95 that enter from and exit to their trigger, no bounce, stretched knobs,
arcs for moving objects, pulses to confirm, overlapping old and new states, masked reveals,
crossfading icons, rubber banding past bounds and shared layout ids.

CSS alone covers hover, press and scroll-driven reveals. It cannot animate an element that is
being removed (exits), move one element between two places in the tree (shared layout), or
follow a finger with rubber banding (drag). Those need a library.

## Decision

Use `motion` (Motion for React) for exits (`AnimatePresence`), shared-element transitions
(`layoutId`) and drag, and keep CSS for everything simpler.

- Components use `m.*` inside `LazyMotion` (`strict`); the animation features (`domMax`) are a
  separate chunk loaded after the first render. The first download grows by about 3 KB gzip;
  the features chunk is about 34 KB gzip.
- Imperative animations (the add-to-cart arc, the knob stretch, the cart icon bump) use
  `requestAnimationFrame` or the browser's Web Animations API, not Motion's `animate`, which
  would pull Motion's whole engine into the first download.
- Curves and durations come from `lib/motion/tokens.ts` (mirrored as CSS variables in
  `styles/index.css`).
- `MotionConfig reducedMotion="user"` turns movement off for visitors who ask for less motion;
  hand-started animations check `prefersReducedMotion()`.
- Tests run with `MotionGlobalConfig.skipAnimations = true`.

## Alternatives considered

- **CSS only:** no exit, shared-layout or drag animations; dialogs and lists would cut instead
  of animating out.
- **React Spring:** physics-first; harder to keep to fixed durations and curves, and no shared
  layout.
- **GSAP:** powerful timelines but imperative and outside React's model; licence terms for
  some plugins.

## Consequences

- New motion code follows the tokens and the rules in AGENTS.md §8 (Motion).
- Radix dialogs that animate out use `forceMount` inside `AnimatePresence`, and return focus
  through `useReturnFocus` because they are opened from code or plain buttons.
