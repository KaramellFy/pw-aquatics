package com.pwaquatics.shop.shipping.infrastructure;

import com.pwaquatics.shop.shipping.domain.ShippingMethod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, UUID> {

    /** Active methods that deliver to a country, in display order. */
    @Query("""
            SELECT DISTINCT m FROM ShippingMethod m
            JOIN FETCH m.countries
            WHERE m.active = true AND :country MEMBER OF m.countries
            ORDER BY m.sortOrder, m.name
            """)
    List<ShippingMethod> findActiveFor(@Param("country") String countryCode);
}
