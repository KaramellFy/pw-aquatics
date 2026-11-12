import { useLayoutEffect, useRef } from 'react';

/**
 * Makes an opening popover or dialog grow from the control that opened it: when `open`
 * turns true, the panel's transform-origin is set to the centre of the element that had
 * focus at that moment (the trigger). Runs before the dialog moves focus inside, and falls
 * back to the panel's own centre.
 */
export function useTriggerOrigin<T extends HTMLElement>(open: boolean) {
  const panel = useRef<T>(null);
  useLayoutEffect(() => {
    const element = panel.current;
    if (!open || !element) return;
    const active = document.activeElement;
    if (!(active instanceof HTMLElement) || active === document.body || element.contains(active)) {
      element.style.transformOrigin = '50% 50%';
      return;
    }
    const trigger = active.getBoundingClientRect();
    const box = element.getBoundingClientRect();
    const x = trigger.left + trigger.width / 2 - box.left;
    const y = trigger.top + trigger.height / 2 - box.top;
    element.style.transformOrigin = `${Math.round(x)}px ${Math.round(y)}px`;
  }, [open]);
  return panel;
}
