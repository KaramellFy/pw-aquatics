import { Copy, Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SwapText } from '@/components/motion/SwapText';
import { Button } from '@/components/ui/Button';

/**
 * New recovery codes, shown once: to copy, download as a text file or write down. They exist
 * only in this page's memory; the server keeps hashes.
 */
export function RecoveryCodesList({ codes }: { codes: string[] }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const text = [t('account.twoFactor.fileTitle'), t('account.twoFactor.fileNote'), '', ...codes, ''].join(
    '\n',
  );

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied(true);
    } catch {
      // Clipboard not allowed (e.g. an insecure origin): the codes are still on screen.
    }
  };

  const download = () => {
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'pw-aquatics-recovery-codes.txt';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      <ol
        aria-label={t('account.twoFactor.codesLabel')}
        className="grid gap-x-8 gap-y-2 rounded-2xl border border-line bg-mist px-6 py-5 font-mono text-[0.9375rem] text-ink sm:grid-cols-2"
      >
        {codes.map((code) => (
          <li key={code} translate="no">
            {code}
          </li>
        ))}
      </ol>
      <div className="flex flex-wrap gap-3">
        <Button type="button" variant="secondary" onClick={() => void copy()}>
          <Copy aria-hidden className="size-4" />
          <SwapText value={copied ? 'copied' : 'copy'}>
            {copied ? t('account.twoFactor.copied') : t('account.twoFactor.copy')}
          </SwapText>
        </Button>
        <Button type="button" variant="secondary" onClick={download}>
          <Download aria-hidden className="size-4" />
          {t('account.twoFactor.download')}
        </Button>
      </div>
    </div>
  );
}
