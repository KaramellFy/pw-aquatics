import { useRef } from 'react';

/**
 * Focus return for Radix dialogs that are opened from code or from a plain button instead of a
 * `Dialog.Trigger` (Radix only returns focus to its own trigger). Spread the result onto
 * `Dialog.Content`: it remembers what had focus when the dialog opened and puts focus back there
 * when it closes, so keyboard and screen-reader users continue where they were.
 */
export function useReturnFocus() {
  const returnTo = useRef<HTMLElement | null>(null);
  return {
    onOpenAutoFocus: () => {
      const active = document.activeElement;
      returnTo.current = active instanceof HTMLElement && active !== document.body ? active : null;
    },
    onCloseAutoFocus: (event: Event) => {
      const element = returnTo.current;
      if (element?.isConnected) {
        event.preventDefault();
        element.focus({ preventScroll: true });
      }
    },
  };
}
