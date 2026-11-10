package com.pwaquatics.shop.customer.infrastructure;

import com.pwaquatics.shop.customer.domain.Address;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is scoped to a user id, so one customer can never reach another's addresses. */
public interface AddressRepository extends JpaRepository<Address, UUID> {

    @Query("SELECT a FROM Address a WHERE a.userId = :userId "
            + "ORDER BY a.defaultShipping DESC, a.defaultBilling DESC, a.createdAt ASC, a.id ASC")
    List<Address> findAllOf(@Param("userId") UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    // The bulk updates below bypass loaded entities, so the persistence context is cleared afterwards.

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Address a SET a.defaultShipping = false WHERE a.userId = :userId AND a.defaultShipping = true")
    void clearDefaultShipping(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Address a SET a.defaultBilling = false WHERE a.userId = :userId AND a.defaultBilling = true")
    void clearDefaultBilling(@Param("userId") UUID userId);

    /** Not clearing here: this runs inside the account-deletion transaction of another module. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Address a WHERE a.userId = :userId")
    void deleteAllOf(@Param("userId") UUID userId);
}
