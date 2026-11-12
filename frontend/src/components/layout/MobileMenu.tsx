import { Menu } from 'lucide-react';
import { m } from 'motion/react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router';
import { Sheet } from '@/components/ui/Sheet';
import { useSession } from '@/features/auth';
import { cn } from '@/lib/cn';
import { duration, ease, STAGGER } from '@/lib/motion/tokens';
import { LanguageSwitcher } from './LanguageSwitcher';
import { Logo } from './Logo';
import { mainNavigation } from './navigation';

/** Navigation for small screens: a sheet from the right whose links arrive one after another. */
export function MobileMenu() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const signedIn = useSession().data?.authenticated === true;
  const accountItem = signedIn
    ? ({ to: '/account', labelKey: 'nav.account' } as const)
    : ({ to: '/login', labelKey: 'auth.signIn' } as const);
  const items = [...mainNavigation, accountItem];

  return (
    <>
      <button
        type="button"
        className="-mr-2 inline-flex size-11 press items-center justify-center rounded-full text-ink hover:bg-brand-50 active:bg-brand-100 lg:hidden"
        aria-label={t('nav.openMenu')}
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <Menu className="size-5" strokeWidth={1.5} />
      </button>
      <Sheet open={open} onOpenChange={setOpen} title={t('nav.menu')} header={<Logo />} className="max-w-sm">
        <nav aria-label={t('nav.main')} className="flex-1 overflow-y-auto px-6 pt-8">
          <ul>
            {items.map((item, index) => (
              <m.li
                key={item.to}
                initial={{ opacity: 0, x: 16 }}
                animate={{
                  opacity: 1,
                  x: 0,
                  transition: { duration: duration.base, ease: ease.out, delay: 0.08 + index * STAGGER },
                }}
              >
                <NavLink
                  to={item.to}
                  onClick={() => setOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 py-3 font-display text-3xl tracking-tight transition-colors',
                      isActive ? 'text-brand-700' : 'text-ink hover:text-brand-700',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      {t(item.labelKey)}
                      {isActive && <span aria-hidden className="size-2 rounded-full bg-brand-400" />}
                    </>
                  )}
                </NavLink>
              </m.li>
            ))}
          </ul>
        </nav>
        <div className="px-6 pb-8">
          <LanguageSwitcher />
        </div>
      </Sheet>
    </>
  );
}
