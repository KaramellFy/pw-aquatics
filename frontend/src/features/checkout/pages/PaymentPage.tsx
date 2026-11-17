import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { useOrder } from '@/features/account';
import { FormPlaceholder, PaymentFrame } from '../components/PaymentFrame';
import { CardPaymentPage } from './CardPaymentPage';
import { PayPalPaymentPage } from './PayPalPaymentPage';

/**
 * `/checkout/pay/:orderNumber`: the payment page for the order's own online payment method
 * (card or PayPal), as the order was placed. Emails and order pages link here for both.
 */
export function PaymentPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const order = useOrder(orderNumber);

  if (order.data?.paymentMethod === 'PAYPAL') {
    return <PayPalPaymentPage />;
  }
  if (order.isPending) {
    return (
      <PaymentFrame orderNumber={orderNumber}>
        <FormPlaceholder label={t('checkout.pay.loading')} />
      </PaymentFrame>
    );
  }
  // Card orders, and orders that cannot be loaded (the card page explains why).
  return <CardPaymentPage />;
}
