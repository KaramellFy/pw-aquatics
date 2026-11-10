import * as Dialog from '@radix-ui/react-dialog';
import { Menu, X } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router';
import { useSession } from '@/features/auth';
import { cn } from '@/lib/cn';
import { LanguageSwitcher } from './LanguageSwitcher';
import { Logo } from './Logo';
import { mainNavigation } from './navigation';

export function MobileMenu() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const signedIn = useSession().data?.authenticated === true;
  const accountItem = signedIn
    ? ({ to: '/account', labelKey: 'nav.account' } as const)
    : ({ to: '/login', labelKey: 'auth.signIn' } as const);

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger
        className="-mr-2 inline-flex size-11 items-center justify-center rounded-full text-ink hover:bg-brand-50 active:bg-brand-100 lg:hidden"
        aria-label={t('nav.openMenu')}
      >
        <Menu className="size-5" strokeWidth={1.5} />
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-ink/30 backdrop-blur-sm data-[state=open]:animate-[fade-up_200ms_ease-out]" />
        <Dialog.Content className="fixed inset-y-0 right-0 z-50 flex w-full max-w-sm flex-col bg-surface px-6 pt-5 pb-8 shadow-lift focus:outline-none">
          <div className="flex items-center justify-between">
            <Logo />
            <Dialog.Close
              className="-mr-2 inline-flex size-11 items-center justify-center rounded-full hover:bg-brand-50 active:bg-brand-100"
              aria-label={t('common.close')}
            >
              <X className="size-5" strokeWidth={1.5} />
            </Dialog.Close>
          </div>
          <Dialog.Title className="sr-only">{t('nav.menu')}</Dialog.Title>
          <Dialog.Description className="sr-only">{t('nav.main')}</Dialog.Description>
          <nav aria-label={t('nav.main')} className="mt-14 flex-1">
            <ul className="space-y-1">
              {[...mainNavigation, accountItem].map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    onClick={() => setOpen(false)}
                    className={({ isActive }) =>
                      cn(
                        'block py-3 font-display text-3xl font-light tracking-tight transition-colors',
                        isActive
                          ? 'relative pl-5 text-brand-700 before:absolute before:top-1/2 before:left-0 before:size-2 before:-translate-y-1/2 before:rounded-full before:bg-brand-400'
                          : 'text-ink hover:text-brand-700',
                      )
                    }
                  >
                    {t(item.labelKey)}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <LanguageSwitcher />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
