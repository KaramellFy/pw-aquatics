package com.pwaquatics.shop.content.infrastructure;

import com.pwaquatics.shop.content.domain.ContactMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {}
