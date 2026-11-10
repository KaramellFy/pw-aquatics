package com.pwaquatics.shop.customer.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A saved delivery or billing address of a customer. References the user by id only,
 * so the customer module stays independent of the identity module's entities.
 */
@Entity
@Table(name = "addresses")
public class Address extends AbstractEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 50)
    private String label;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 150)
    private String company;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(name = "house_number", nullable = false, length = 20)
    private String houseNumber;

    @Column(name = "address_line2", length = 150)
    private String addressLine2;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    @Column(name = "is_default_shipping", nullable = false)
    private boolean defaultShipping;

    @Column(name = "is_default_billing", nullable = false)
    private boolean defaultBilling;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {}

    public Address(UUID userId, AddressData data, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        apply(data, now);
    }

    public void update(AddressData data, Instant now) {
        apply(data, now);
    }

    private void apply(AddressData data, Instant now) {
        this.label = data.label();
        this.firstName = data.firstName();
        this.lastName = data.lastName();
        this.company = data.company();
        this.street = data.street();
        this.houseNumber = data.houseNumber();
        this.addressLine2 = data.addressLine2();
        this.postalCode = data.postalCode();
        this.city = data.city();
        this.countryCode = data.countryCode();
        this.phone = data.phone();
        this.updatedAt = now;
    }

    public void markDefaultShipping() {
        this.defaultShipping = true;
    }

    public void markDefaultBilling() {
        this.defaultBilling = true;
    }

    public AddressData data() {
        return new AddressData(label, firstName, lastName, company, street, houseNumber, addressLine2, postalCode,
                city, countryCode, phone);
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isDefaultShipping() {
        return defaultShipping;
    }

    public boolean isDefaultBilling() {
        return defaultBilling;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
