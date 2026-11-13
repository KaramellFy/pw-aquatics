package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderAddress;
import com.pwaquatics.shop.order.domain.OrderItem;
import com.pwaquatics.shop.payment.application.view.PaymentInstructionsView;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.shipping.application.ShippingService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Order emails in the customer's language (English or German), plain text. They only
 * present what is stored in the order; nothing is calculated here.
 */
@Component
class OrderEmails {

    private static final Locale ENGLISH = Locale.of("en", "IE"); // "€12.34", "16 November 2026"

    private final String baseUrl;

    OrderEmails(AppProperties properties) {
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    /**
     * Confirmation with all items and totals, sent as soon as the order is placed. For bank
     * transfer it carries the payment details; for card payment, the link to pay and the deadline.
     */
    EmailMessage confirmation(Order order, PaymentInstructionsView payment) {
        boolean german = german(order);
        Locale locale = locale(order);
        StringBuilder body = new StringBuilder();
        body.append(german ? "Hallo " : "Hello ").append(order.billingAddress().getFirstName()).append(",\n\n");
        body.append(german
                ? "vielen Dank für deine Bestellung bei PW Aquatics. Wir haben sie erhalten und die Tiere für dich reserviert.\n\n"
                : "thank you for your order at PW Aquatics. We have received it and reserved the animals for you.\n\n");
        body.append(german ? "Bestellnummer: " : "Order number: ").append(order.getOrderNumber()).append('\n');
        body.append(german ? "Bestelldatum: " : "Order date: ").append(date(order.getPlacedAt(), locale)).append("\n\n");

        body.append(german ? "Deine Bestellung\n" : "Your order\n");
        for (OrderItem item : order.getItems()) {
            body.append("  ").append(item.getQuantity()).append(" × ").append(item.getProductName())
                    .append(" – ").append(item.getVariantName())
                    .append(": ").append(money(Money.eur(item.getLineTotalCents()), locale)).append('\n');
        }
        body.append('\n');
        body.append(german ? "Zwischensumme: " : "Subtotal: ").append(money(order.subtotal(), locale)).append('\n');
        body.append(german ? "Versand (" : "Shipping (").append(order.getShippingMethodName()).append("): ")
                .append(money(order.shipping(), locale)).append('\n');
        body.append(german ? "Gesamtsumme: " : "Total: ").append(money(order.total(), locale))
                .append(german ? " (inkl. " : " (includes ").append(money(order.includedTax(), locale))
                .append(german ? " MwSt.)\n\n" : " VAT)\n\n");

        if (payment != null) {
            body.append(german ? "Zahlung per Überweisung\n" : "Payment by bank transfer\n");
            if (order.getPaymentDueAt() != null) {
                body.append(german
                        ? "Bitte überweise den Gesamtbetrag bis zum %s. Wir versenden, sobald deine Zahlung eingegangen ist.\n"
                                .formatted(date(order.getPaymentDueAt(), locale))
                        : "Please transfer the total by %s. We ship as soon as your payment has arrived.\n"
                                .formatted(date(order.getPaymentDueAt(), locale)));
            }
            body.append(german ? "  Kontoinhaber: " : "  Account holder: ").append(payment.accountHolder()).append('\n');
            body.append("  IBAN: ").append(payment.iban()).append('\n');
            if (payment.bic() != null) {
                body.append("  BIC: ").append(payment.bic()).append('\n');
            }
            if (payment.bankName() != null) {
                body.append("  Bank: ").append(payment.bankName()).append('\n');
            }
            body.append(german ? "  Betrag: " : "  Amount: ").append(money(payment.amount(), locale)).append('\n');
            body.append(german ? "  Verwendungszweck: " : "  Reference: ").append(payment.reference()).append('\n');
            body.append(german
                    ? "Geht bis dahin keine Zahlung ein, wird die Bestellung automatisch storniert und die Tiere gehen wieder in den Verkauf.\n\n"
                    : "If no payment has arrived by then, the order is cancelled automatically and the animals go back on sale.\n\n");
        } else if (order.getPaymentMethod() == PaymentMethod.STRIPE && order.getPaymentDueAt() != null) {
            body.append(german ? "Zahlung mit Karte\n" : "Payment by card\n");
            body.append(german
                    ? "Falls du die Zahlung noch nicht abgeschlossen hast, kannst du das bis %s (deutsche Zeit) hier tun:\n"
                            .formatted(dateTime(order.getPaymentDueAt(), locale))
                    : "If you have not completed the payment yet, you can do so until %s (German time) here:\n"
                            .formatted(dateTime(order.getPaymentDueAt(), locale)));
            body.append("  ").append(payLink(order)).append('\n');
            body.append(german
                    ? "Danach wird die Bestellung automatisch storniert und die Tiere gehen wieder in den Verkauf. Sobald die Zahlung bestätigt ist, bekommst du eine weitere E-Mail.\n\n"
                    : "After that the order is cancelled automatically and the animals go back on sale. You will get another email as soon as the payment is confirmed.\n\n");
        }

        body.append(german ? "Lieferadresse\n" : "Shipping to\n");
        appendAddress(body, order.shippingAddress());
        if (order.getPlannedDispatchDate() != null) {
            body.append('\n').append(german ? "Frühester Versandtag: " : "Earliest dispatch day: ")
                    .append(date(order.getPlannedDispatchDate(), locale)).append('\n');
        }
        body.append('\n').append(german ? "Deine Bestellung im Überblick: " : "Follow your order: ")
                .append(orderLink(order)).append('\n');

        String subject = (german ? "Deine Bestellung " : "Your order ") + order.getOrderNumber();
        return new EmailMessage(order.getCustomerEmail(), null, subject, body.toString());
    }

    /** The card payment has been confirmed by Stripe; the order is paid. */
    EmailMessage paymentReceived(Order order) {
        boolean german = german(order);
        Locale locale = locale(order);
        StringBuilder body = new StringBuilder();
        body.append(german ? "Hallo " : "Hello ").append(order.billingAddress().getFirstName()).append(",\n\n");
        body.append(german
                ? "deine Zahlung über %s für die Bestellung %s ist bei uns eingegangen. Vielen Dank!\n"
                        .formatted(money(order.total(), locale), order.getOrderNumber())
                : "we have received your payment of %s for order %s. Thank you!\n"
                        .formatted(money(order.total(), locale), order.getOrderNumber()));
        if (order.getPlannedDispatchDate() != null) {
            body.append(german
                    ? "Wir versenden frühestens am %s und melden uns, sobald das Paket unterwegs ist.\n"
                            .formatted(date(order.getPlannedDispatchDate(), locale))
                    : "We dispatch on %s at the earliest and will let you know as soon as the parcel is on its way.\n"
                            .formatted(date(order.getPlannedDispatchDate(), locale)));
        }
        body.append('\n').append(german ? "Deine Bestellung im Überblick: " : "Follow your order: ")
                .append(orderLink(order)).append('\n');
        String subject = (german ? "Zahlung erhalten – Bestellung %s" : "Payment received – order %s")
                .formatted(order.getOrderNumber());
        return new EmailMessage(order.getCustomerEmail(), null, subject, body.toString());
    }

    /**
     * For the shop owner: a card payment was confirmed for an order that had already been
     * cancelled. The animals may be sold again, so the owner decides whether to refund
     * (in the Stripe Dashboard) or to reinstate the order. Contains no customer details.
     */
    EmailMessage paymentForCancelledOrder(Order order, String shopInbox) {
        String body = "Stripe has confirmed a card payment of %s for order %s, but the order had already been cancelled.\n\n"
                        .formatted(money(order.total(), ENGLISH), order.getOrderNumber())
                + "Please check whether the animals are still available. Then either refund the payment in the Stripe "
                + "Dashboard or get in touch with the customer.\n";
        return new EmailMessage(shopInbox, null, "Payment for cancelled order " + order.getOrderNumber(), body);
    }

    /**
     * @param paymentMissed true when the order expired because no payment arrived; false when the
     *     customer cancelled it
     */
    EmailMessage cancelled(Order order, boolean paymentMissed) {
        boolean german = german(order);
        boolean card = order.getPaymentMethod() == PaymentMethod.STRIPE;
        String firstName = order.billingAddress().getFirstName();
        String reason;
        if (paymentMissed && card) {
            reason = german
                    ? "die Kartenzahlung wurde nicht rechtzeitig abgeschlossen. Wir haben deine Bestellung %s deshalb storniert."
                    : "the card payment was not completed in time, so your order %s has been cancelled.";
        } else if (paymentMissed) {
            reason = german
                    ? "bis zum Zahlungsziel ist keine Zahlung bei uns eingegangen. Wir haben deine Bestellung %s deshalb storniert."
                    : "we did not receive a payment by the due date, so your order %s has been cancelled.";
        } else {
            reason = german
                    ? "deine Bestellung %s wurde wie gewünscht storniert."
                    : "your order %s has been cancelled as requested.";
        }
        String money;
        if (card) {
            money = german
                    ? "Die Tiere sind wieder im Shop verfügbar. Deine Karte wurde nicht belastet.\n\n"
                    : "The animals are available in the shop again. Your card has not been charged.\n\n";
        } else {
            money = german
                    ? "Die Tiere sind wieder im Shop verfügbar. Falls du bereits überwiesen hast, melde dich bitte bei uns – wir erstatten den Betrag.\n\n"
                    : "The animals are available in the shop again. If you have already transferred the money, please get in touch – we will refund it.\n\n";
        }
        String body = (german ? "Hallo " : "Hello ") + firstName + ",\n\n"
                + reason.formatted(order.getOrderNumber()) + "\n\n"
                + money
                + (german ? "Bestellung ansehen: " : "View the order: ") + orderLink(order) + "\n";
        String subject = (german ? "Bestellung %s storniert" : "Order %s cancelled").formatted(order.getOrderNumber());
        return new EmailMessage(order.getCustomerEmail(), null, subject, body);
    }

    private String orderLink(Order order) {
        return baseUrl + "/account/orders/" + order.getOrderNumber();
    }

    private String payLink(Order order) {
        return baseUrl + "/checkout/pay/" + order.getOrderNumber();
    }

    private static void appendAddress(StringBuilder body, OrderAddress address) {
        body.append("  ").append(address.getFirstName()).append(' ').append(address.getLastName()).append('\n');
        if (address.getCompany() != null) {
            body.append("  ").append(address.getCompany()).append('\n');
        }
        body.append("  ").append(address.getStreet()).append(' ').append(address.getHouseNumber()).append('\n');
        if (address.getAddressLine2() != null) {
            body.append("  ").append(address.getAddressLine2()).append('\n');
        }
        body.append("  ").append(address.getPostalCode()).append(' ').append(address.getCity()).append('\n');
        body.append("  ").append(Locale.of("", address.getCountryCode()).getDisplayCountry(Locale.ENGLISH)).append('\n');
    }

    private static boolean german(Order order) {
        return "de".equals(order.getCustomerLocale());
    }

    private static Locale locale(Order order) {
        return german(order) ? Locale.GERMANY : ENGLISH;
    }

    /** Formats a stored amount for reading; no arithmetic. */
    private static String money(Money money, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance(money.currency()));
        return format.format(BigDecimal.valueOf(money.amount(), 2));
    }

    private static String date(Instant instant, Locale locale) {
        return date(LocalDate.ofInstant(instant, ShippingService.SHOP_ZONE), locale);
    }

    private static String dateTime(Instant instant, Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ShippingService.SHOP_ZONE)
                .format(instant);
    }

    private static String date(LocalDate date, Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date);
    }
}
