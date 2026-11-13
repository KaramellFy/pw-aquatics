package com.pwaquatics.shop.order.infrastructure;

import com.pwaquatics.shop.order.domain.OrderNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {

    List<OrderNote> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
