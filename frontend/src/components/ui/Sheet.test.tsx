import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '@/test/render';
import { Dialog } from './Dialog';
import { Sheet } from './Sheet';

/** Opens the panel from a plain button (no Radix trigger), like the cart and the forms do. */
function Harness({ kind }: { kind: 'sheet' | 'dialog' }) {
  const [open, setOpen] = useState(false);
  const Panel = kind === 'sheet' ? Sheet : Dialog;
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Open panel
      </button>
      <Panel open={open} onOpenChange={setOpen} title="Panel title">
        <button type="button">Inside</button>
      </Panel>
    </>
  );
}

describe.each(['sheet', 'dialog'] as const)('%s focus', (kind) => {
  it('moves focus inside when opened and back to the opener when closed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness kind={kind} />);
    const opener = screen.getByRole('button', { name: 'Open panel' });

    await user.click(opener);
    const dialog = await screen.findByRole('dialog', { name: 'Panel title' });
    expect(dialog).toContainElement(document.activeElement as HTMLElement);

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });
});
