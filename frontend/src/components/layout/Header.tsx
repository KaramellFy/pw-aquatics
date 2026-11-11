import { ShoppingBag, SlidersHorizontal, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink } from 'react-router';
import { cn } from '@/lib/cn';
import { Container } from '@/components/ui/Container';
import { useSession } from '@/features/auth';
import { useCart } from '@/features/cart';
import { Logo } from './Logo';
import { MobileMenu } from './MobileMenu';
import { mainNavigation } from './navigation';

const iconLink =
  'inline-flex size-11 items-center justify-center rounded-full text-ink transition-colors hover:bg-brand-50 active:bg-brand-100';

export function Header() {
  const { t } = useTranslation();
  const [scrolled, setScrolled] = useState(false);
  const session = useSession().data;
  const signedIn = session?.authenticated === true;
  const isAdmin = session?.user?.role === 'ADMIN';
  const cartCount = useCart().data?.itemCount ?? 0;

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={cn(
        'sticky top-0 z-40 border-b transition-[background-color,border-color,backdrop-filter] duration-(--duration-base)',
        scrolled ? 'border-line bg-surface/90 backdrop-blur-md' : 'border-transparent bg-surface',
      )}
    >
      <Container className="flex h-18 items-center justify-between gap-6 lg:h-20">
        <Link to="/" aria-label={t('common.brand')} className="rounded-lg">
          <Logo />
        </Link>

        <nav aria-label={t('nav.main')} className="hidden lg:block">
          <ul className="flex items-center gap-10">
            {mainNavigation.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      'relative py-2 text-[0.9375rem] transition-colors',
                      'after:absolute after:inset-x-0 after:-bottom-0.5 after:h-0.5 after:origin-left after:rounded-full after:bg-brand-400 after:transition-transform after:duration-(--duration-base)',
                      isActive
                        ? 'font-medium text-ink after:scale-x-100'
                        : 'text-ink-soft after:scale-x-0 hover:text-ink hover:after:scale-x-100',
                    )
                  }
                >
                  {t(item.labelKey)}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex items-center gap-1">
          {isAdmin && (
            <Link to="/admin" className={cn(iconLink, 'hidden sm:inline-flex')} aria-label={t('nav.admin')}>
              <SlidersHorizontal className="size-5" strokeWidth={1.5} />
            </Link>
          )}
          <Link
            to={signedIn ? '/account' : '/login'}
            className={cn(iconLink, 'relative hidden sm:inline-flex')}
            aria-label={signedIn ? t('nav.account') : t('auth.signIn')}
          >
            <User className="size-5" strokeWidth={1.5} />
            {signedIn && (
              <span aria-hidden className="absolute top-2 right-2 size-2 rounded-full bg-brand-500" />
            )}
          </Link>
          <Link
            to="/cart"
            className={cn(iconLink, 'relative')}
            aria-label={cartCount > 0 ? t('nav.cartWithCount', { count: cartCount }) : t('nav.cart')}
          >
            <ShoppingBag className="size-5" strokeWidth={1.5} />
            {cartCount > 0 && (
              <span
                aria-hidden
                className="absolute top-0.5 right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-400 px-1 text-[0.6875rem] font-semibold text-ink tabular-nums"
              >
                {cartCount > 99 ? '99+' : cartCount}
              </span>
            )}
          </Link>
          <MobileMenu />
        </div>
      </Container>
    </header>
  );
}
