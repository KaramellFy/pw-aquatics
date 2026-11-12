import { AlertTriangle, Banknote, PackageCheck, PackageX } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { formatMoney } from '@/lib/format/money';
import { formatCount } from '@/lib/format/number';
import type { Dashboard } from '../../api/statistics';

/** What needs attention now, independent of the chosen period. */
export function RightNow({ dashboard }: { dashboard: Dashboard }) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  return (
    <ul className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <Tile
        icon={<Banknote aria-hidden className="size-5" />}
        label={t('admin.dashboard.now.awaitingPayment')}
        value={formatMoney(dashboard.awaitingPayment, locale)}
        detail={t('admin.dashboard.now.orders', { count: dashboard.awaitingPaymentOrders })}
      />
      <Tile
        icon={<PackageCheck aria-hidden className="size-5" />}
        label={t('admin.dashboard.now.toShip')}
        value={formatCount(dashboard.ordersToShip, locale)}
        detail={t('admin.dashboard.now.toShipDetail')}
      />
      <Tile
        icon={<AlertTriangle aria-hidden className="size-5" />}
        label={t('admin.dashboard.now.lowStock')}
        value={formatCount(dashboard.lowStockProducts, locale)}
        detail={t('admin.dashboard.now.ofLive', { count: dashboard.liveProducts })}
        to="/admin/inventory?stock=LOW_STOCK"
        linkLabel={t('admin.dashboard.now.viewLowStock')}
        attention={dashboard.lowStockProducts > 0}
      />
      <Tile
        icon={<PackageX aria-hidden className="size-5" />}
        label={t('admin.dashboard.now.soldOut')}
        value={formatCount(dashboard.soldOutProducts, locale)}
        detail={t('admin.dashboard.now.ofLive', { count: dashboard.liveProducts })}
        to="/admin/inventory?stock=OUT_OF_STOCK"
        linkLabel={t('admin.dashboard.now.viewSoldOut')}
        attention={dashboard.soldOutProducts > 0}
      />
    </ul>
  );
}

function Tile({
  icon,
  label,
  value,
  detail,
  to,
  linkLabel,
  attention = false,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  detail: string;
  to?: string;
  linkLabel?: string;
  attention?: boolean;
}) {
  return (
    <li className="flex flex-col rounded-card border border-line bg-surface p-4 sm:p-5">
      <p className="flex items-center gap-2 text-sm text-ink-soft">
        <span className={attention ? 'text-warning' : 'text-brand-700'}>{icon}</span>
        {label}
      </p>
      <p className="mt-2 text-xl leading-tight font-semibold break-words text-ink sm:text-[1.75rem]">
        {value}
      </p>
      <p className="mt-1 text-xs text-muted">{detail}</p>
      {to && linkLabel && (
        <Link
          to={to}
          className="mt-3 inline-flex min-h-11 items-center self-start text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
        >
          {linkLabel}
        </Link>
      )}
    </li>
  );
}
