import { LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { useLogout } from '@/features/auth';
import { cn } from '@/lib/cn';
import { PageMeta } from '@/lib/seo/PageMeta';

const navigation = [
  { to: '/account', labelKey: 'account.nav.overview', end: true },
  { to: '/account/orders', labelKey: 'account.nav.orders', end: false },
  { to: '/account/addresses', labelKey: 'account.nav.addresses', end: false },
  { to: '/account/profile', labelKey: 'account.nav.profile', end: false },
] as const;

/** Shell of the customer account area: section navigation plus the active page. */
export function AccountLayout() {
  const { t } = useTranslation();
  const logout = useLogout();

  return (
    <>
      <PageMeta title={t('account.metaTitle')} noIndex />
      <Container className="grid gap-10 pt-10 pb-24 sm:pt-14 lg:grid-cols-12 lg:gap-12">
        <aside className="lg:col-span-3">
          <nav aria-label={t('account.nav.label')}>
            <ul className="-mx-5 flex gap-1 overflow-x-auto px-5 lg:mx-0 lg:flex-col lg:px-0">
              {navigation.map((item) => (
                <li key={item.to} className="shrink-0">
                  <NavLink
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      cn(
                        'flex min-h-11 items-center rounded-full px-4 text-[0.9375rem] whitespace-nowrap transition-colors lg:rounded-xl',
                        isActive ? 'bg-brand-50 font-medium text-brand-800' : 'text-ink-soft hover:bg-mist',
                      )
                    }
                  >
                    {t(item.labelKey)}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <Button
            variant="ghost"
            className="mt-4 hidden text-ink-soft lg:inline-flex"
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
          >
            <LogOut aria-hidden className="size-4" />
            {t('auth.signOut')}
          </Button>
        </aside>

        <div className="lg:col-span-9">
          <Outlet />
          <Button
            variant="secondary"
            className="mt-12 lg:hidden"
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
          >
            <LogOut aria-hidden className="size-4" />
            {t('auth.signOut')}
          </Button>
        </div>
      </Container>
    </>
  );
}
