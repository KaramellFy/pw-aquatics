import { ExternalLink, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet, ScrollRestoration } from 'react-router';
import { Logo } from '@/components/layout/Logo';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { Button } from '@/components/ui/Button';
import { useLogout } from '@/features/auth';
import { cn } from '@/lib/cn';
import { PageMeta } from '@/lib/seo/PageMeta';

const groups = [
  {
    labelKey: 'admin.nav.overview',
    items: [{ to: '/admin', labelKey: 'admin.nav.dashboard', end: true }],
  },
  {
    labelKey: 'admin.nav.sales',
    items: [
      { to: '/admin/orders', labelKey: 'admin.nav.orders', end: false },
      { to: '/admin/customers', labelKey: 'admin.nav.customers', end: false },
    ],
  },
  {
    labelKey: 'admin.nav.catalog',
    items: [
      { to: '/admin/products', labelKey: 'admin.nav.products', end: false },
      { to: '/admin/inventory', labelKey: 'admin.nav.inventory', end: false },
    ],
  },
  {
    labelKey: 'admin.nav.taxonomy',
    items: [
      { to: '/admin/categories', labelKey: 'admin.nav.categories', end: false },
      { to: '/admin/species', labelKey: 'admin.nav.species', end: false },
      { to: '/admin/colours', labelKey: 'admin.nav.colours', end: false },
      { to: '/admin/grades', labelKey: 'admin.nav.grades', end: false },
    ],
  },
  {
    labelKey: 'admin.nav.content',
    items: [
      { to: '/admin/inbox', labelKey: 'admin.nav.inbox', end: false },
      { to: '/admin/gallery', labelKey: 'admin.nav.gallery', end: false },
    ],
  },
  {
    labelKey: 'admin.nav.settings',
    items: [{ to: '/admin/shipping', labelKey: 'admin.nav.shipping', end: false }],
  },
] as const;

/** Shell of the admin area: its own header and navigation, without the shop's chrome. */
export function AdminLayout() {
  const { t } = useTranslation();
  const logout = useLogout();

  return (
    <>
      <PageMeta title={t('admin.metaTitle')} noIndex />
      <div className="flex min-h-dvh flex-col bg-mist">
        <a
          href="#admin-main"
          className="sr-only z-50 rounded-full bg-ink px-5 py-3 text-white focus:not-sr-only focus:fixed focus:top-4 focus:left-4"
        >
          {t('common.skipToContent')}
        </a>
        <header className="sticky top-0 z-30 border-b border-line bg-surface">
          <div className="mx-auto flex h-16 max-w-[90rem] items-center gap-4 px-5 sm:px-8">
            <Link to="/admin" aria-label={t('admin.title')} className="flex items-center gap-3 rounded-lg">
              <Logo />
              <span className="hidden rounded-full bg-ink px-2.5 py-0.5 text-xs font-medium text-white sm:inline">
                {t('admin.title')}
              </span>
            </Link>
            <div className="ml-auto flex items-center gap-1">
              <Link
                to="/"
                className="inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-sm text-ink-soft hover:bg-ink/5"
              >
                <ExternalLink aria-hidden className="size-4" />
                <span className="hidden sm:inline">{t('admin.nav.viewShop')}</span>
                <span className="sr-only sm:hidden">{t('admin.nav.viewShop')}</span>
              </Link>
              <Button variant="ghost" disabled={logout.isPending} onClick={() => logout.mutate()}>
                <LogOut aria-hidden className="size-4" />
                <span className="hidden sm:inline">{t('auth.signOut')}</span>
                <span className="sr-only sm:hidden">{t('auth.signOut')}</span>
              </Button>
            </div>
          </div>
        </header>

        <div className="mx-auto grid w-full max-w-[90rem] flex-1 grid-cols-1 gap-6 px-5 py-6 sm:px-8 lg:grid-cols-[14rem_minmax(0,1fr)] lg:gap-10 lg:py-10">
          <nav aria-label={t('admin.nav.label')} className="min-w-0">
            <div className="-mx-5 flex gap-6 overflow-x-auto px-5 lg:mx-0 lg:flex-col lg:px-0">
              {groups.map((group) => (
                <div key={group.labelKey} className="shrink-0">
                  <p className="relative z-10 px-4 eyebrow text-muted">{t(group.labelKey)}</p>
                  <ul className="mt-2 flex gap-1 lg:flex-col">
                    {group.items.map((item) => (
                      <li key={item.to} className="shrink-0">
                        <NavLink
                          to={item.to}
                          end={item.end}
                          className={({ isActive }) =>
                            cn(
                              'relative flex min-h-11 press items-center rounded-full px-4 text-[0.9375rem] whitespace-nowrap transition-colors lg:rounded-xl',
                              isActive ? 'font-medium text-brand-800' : 'text-ink-soft hover:bg-surface',
                            )
                          }
                        >
                          {({ isActive }) => (
                            <>
                              {isActive && (
                                <SlidingIndicator
                                  layoutId="admin-nav"
                                  className="inset-0 rounded-full lg:rounded-xl"
                                  markClassName="bg-surface shadow-soft"
                                />
                              )}
                              <span className="relative z-10">{t(item.labelKey)}</span>
                            </>
                          )}
                        </NavLink>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </nav>
          <main id="admin-main" tabIndex={-1} className="min-w-0 focus:outline-none">
            <Outlet />
          </main>
        </div>
      </div>
      <ScrollRestoration />
    </>
  );
}
