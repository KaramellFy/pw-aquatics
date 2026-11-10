package com.pwaquatics.shop.identity.infrastructure;

import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalised (see {@code EmailAddress.normalise}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);
}
