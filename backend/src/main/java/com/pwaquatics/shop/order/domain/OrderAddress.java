package com.pwaquatics.shop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A postal address copied into the order when it was placed. It never changes afterwards,
 * whatever the customer does with their address book.
 */
@Embeddable
public class OrderAddress {

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

    protected OrderAddress() {}

    public OrderAddress(
            String firstName,
            String lastName,
            String company,
            String street,
            String houseNumber,
            String addressLine2,
            String postalCode,
            String city,
            String countryCode,
            String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.company = company;
        this.street = street;
        this.houseNumber = houseNumber;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.city = city;
        this.countryCode = countryCode;
        this.phone = phone;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCompany() {
        return company;
    }

    public String getStreet() {
        return street;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }
}
