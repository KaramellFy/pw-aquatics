package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.identity.application.UserDirectory;
import com.pwaquatics.shop.payment.application.view.PaymentRefundView;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentRefund;
import com.pwaquatics.shop.payment.infrastructure.PaymentRefundRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The refunds of a payment as the admin order page lists them. */
@Component
class RefundHistory {

    private final PaymentRefundRepository refunds;
    private final UserDirectory users;

    RefundHistory(PaymentRefundRepository refunds, UserDirectory users) {
        this.refunds = refunds;
        this.users = users;
    }

    List<PaymentRefundView> of(Payment payment) {
        List<PaymentRefund> all = refunds.findAllByPaymentIdOrderByCreatedAtAsc(payment.getId());
        List<UUID> people = all.stream().map(PaymentRefund::getRequestedBy).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> names = people.isEmpty() ? Map.of() : users.displayNames(people);
        return all.stream()
                .map(refund -> new PaymentRefundView(
                        refund.getId(),
                        refund.amount(),
                        refund.getStatus(),
                        refund.getCreatedAt(),
                        refund.getProviderReference(),
                        refund.getReason(),
                        refund.getFailureCode(),
                        refund.getRequestedBy() == null ? null : names.get(refund.getRequestedBy())))
                .toList();
    }
}
