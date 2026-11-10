import { ArrowRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useSession } from '@/features/auth';
import { useIntlLocale } from '@/lib/i18n';
import { useAddresses, useProfile } from '../api/account';
import { AddressBlock } from '../components/AddressBlock';

function Card({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="flex flex-col rounded-card border border-line bg-surface p-6">
      <h2 className="font-sans eyebrow text-brand-700">{title}</h2>
      <div className="mt-4 flex-1">{children}</div>
      {action && <div className="mt-6">{action}</div>}
    </section>
  );
}

function CardLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="group inline-flex items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
    >
      {children}
      <ArrowRight aria-hidden className="size-4 transition-transform group-hover:translate-x-0.5" />
    </Link>
  );
}

export function AccountOverviewPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const session = useSession();
  const profile = useProfile();
  const addresses = useAddresses();
  const user = session.data?.user;
  const defaultAddress = addresses.data?.find((address) => address.defaultShipping);

  return (
    <>
      <h1 className="text-headline">{t('account.overview.greeting', { name: user?.firstName ?? '' })}</h1>
      <p className="mt-3 text-lg text-ink-soft">{t('account.overview.lead')}</p>

      <div className="mt-10 grid gap-5 md:grid-cols-2">
        <Card
          title={t('account.overview.addressTitle')}
          action={
            <CardLink to="/account/addresses">
              {defaultAddress ? t('account.overview.manageAddresses') : t('account.overview.addAddress')}
            </CardLink>
          }
        >
          {defaultAddress ? (
            <AddressBlock address={defaultAddress} />
          ) : (
            <p className="text-ink-soft">
              {addresses.isPending ? t('common.loading') : t('account.overview.noAddress')}
            </p>
          )}
        </Card>

        <Card
          title={t('account.overview.profileTitle')}
          action={<CardLink to="/account/profile">{t('account.overview.editProfile')}</CardLink>}
        >
          {user && (
            <p className="leading-relaxed text-ink-soft">
              <span className="block text-ink">
                {user.firstName} {user.lastName}
              </span>
              <span className="block break-all">{user.email}</span>
              {profile.data && (
                <span className="mt-2 block text-sm text-muted">
                  {t('account.overview.memberSince', {
                    date: new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(
                      new Date(profile.data.memberSince),
                    ),
                  })}
                </span>
              )}
            </p>
          )}
        </Card>

        <div className="md:col-span-2">
          <Card title={t('account.overview.ordersTitle')}>
            <p className="text-ink-soft">{t('account.overview.ordersText')}</p>
          </Card>
        </div>
      </div>
    </>
  );
}
