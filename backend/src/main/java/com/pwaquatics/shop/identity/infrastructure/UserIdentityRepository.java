package com.pwaquatics.shop.identity.infrastructure;

import com.pwaquatics.shop.identity.domain.IdentityProvider;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    Optional<UserIdentity> findByUserAndProvider(User user, IdentityProvider provider);

    @Modifying
    @Query("DELETE FROM UserIdentity i WHERE i.user = :user")
    void deleteAllByUser(User user);
}
