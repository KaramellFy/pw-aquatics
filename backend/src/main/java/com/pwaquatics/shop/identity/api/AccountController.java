package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.AccountService;
import com.pwaquatics.shop.identity.application.UpdateProfile;
import com.pwaquatics.shop.identity.application.view.ProfileView;
import com.pwaquatics.shop.security.SessionManager;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own account (docs/API.md §2.6). Always scoped to the session user. */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account")
class AccountController {

    private final AccountService accounts;
    private final SessionManager sessions;

    AccountController(AccountService accounts, SessionManager sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Operation(summary = "Own profile")
    @GetMapping("/profile")
    ProfileView profile(@AuthenticationPrincipal ShopUserPrincipal principal) {
        return accounts.profile(principal.userId());
    }

    @Operation(summary = "Update name, phone, newsletter preference and language")
    @PatchMapping("/profile")
    ProfileView updateProfile(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest http) {
        return accounts.updateProfile(
                principal.userId(),
                new UpdateProfile(
                        request.firstName().strip(),
                        request.lastName().strip(),
                        request.phone() == null || request.phone().isBlank() ? null : request.phone().strip(),
                        request.marketingOptIn(),
                        request.locale()),
                http.getRemoteAddr());
    }

    @Operation(summary = "Change the password; signs out all other devices")
    @PutMapping("/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest http) {
        accounts.changePassword(
                principal.userId(), request.currentPassword(), request.newPassword(), http.getRemoteAddr());
        sessions.endOtherSessions(principal.userId(), http);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request an email change; a confirmation link is sent to the new address")
    @PutMapping("/email")
    ResponseEntity<Void> changeEmail(
            @AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody ChangeEmailRequest request) {
        accounts.requestEmailChange(principal.userId(), request.newEmail(), request.currentPassword());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Delete the account and personal data")
    @DeleteMapping
    ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        accounts.deleteAccount(principal.userId(), request.currentPassword(), http.getRemoteAddr());
        sessions.endAllSessions(principal.userId());
        sessions.logout(http, response);
        return ResponseEntity.noContent().build();
    }
}
