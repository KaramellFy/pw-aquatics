package com.pwaquatics.shop.identity.application.view;

import com.pwaquatics.shop.identity.domain.UserRole;
import java.util.UUID;

/** The logged-in user as shown in the UI (header, account area). */
public record CurrentUserView(UUID id, String email, String firstName, String lastName, UserRole role) {}
