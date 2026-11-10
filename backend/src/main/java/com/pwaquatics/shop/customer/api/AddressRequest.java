package com.pwaquatics.shop.customer.api;

import com.pwaquatics.shop.customer.domain.AddressData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record AddressRequest(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 150) String company,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String houseNumber,
        @Size(max = 150) String addressLine2,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @Size(max = 40) @Pattern(regexp = "^[0-9+()/ .-]*$") String phone) {

    AddressData toData() {
        return new AddressData(
                clean(label),
                firstName.strip(),
                lastName.strip(),
                clean(company),
                street.strip(),
                houseNumber.strip(),
                clean(addressLine2),
                postalCode.strip(),
                city.strip(),
                countryCode.toUpperCase(Locale.ROOT),
                clean(phone));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
