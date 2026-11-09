import { ShoppingBag, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink } from 'react-router';
import { cn } from '@/lib/cn';
import { Container } from '@/components/ui/Container';
import { Logo } from './Logo';
import { MobileMenu } from './MobileMenu';
import { mainNavigation } from './navigation';

const iconLink =
  'inline-flex size-11 items-center justify-center rounded-full transition-colors hover:bg-ink/5 focus-visible:outline-2 focus-visible:outline-moss-500';

export function Header() {
  const { t } = useTranslation();
  const [scrolled, setScrolled] = useState(false);

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
        scrolled ? 'border-ink/8 bg-paper/85 backdrop-blur-md' : 'border-transparent bg-paper',
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
                      'after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:bg-current after:transition-transform after:duration-(--duration-base)',
                      isActive
                        ? 'text-ink after:scale-x-100'
                        : 'text-ink/65 after:scale-x-0 hover:text-ink hover:after:scale-x-100',
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
          <Link to="/account" className={cn(iconLink, 'hidden sm:inline-flex')} aria-label={t('nav.account')}>
            <User className="size-5" strokeWidth={1.5} />
          </Link>
          <Link to="/cart" className={iconLink} aria-label={t('nav.cart')}>
            <ShoppingBag className="size-5" strokeWidth={1.5} />
          </Link>
          <MobileMenu />
        </div>
      </Container>
    </header>
  );
}
