package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 40) @Pattern(regexp = "^[0-9+()/ .-]*$") String phone,
        @NotNull Boolean marketingOptIn,
        @Pattern(regexp = "en|de") String locale) {}
