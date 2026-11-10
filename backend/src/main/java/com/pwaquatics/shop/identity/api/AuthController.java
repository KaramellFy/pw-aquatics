package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.AuthService;
import com.pwaquatics.shop.identity.application.PasswordResetService;
import com.pwaquatics.shop.identity.application.RegisterAccount;
import com.pwaquatics.shop.identity.application.RegistrationService;
import com.pwaquatics.shop.security.SessionManager;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login and password reset (docs/API.md §2.1). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private final AuthService authService;
    private final RegistrationService registration;
    private final PasswordResetService passwordReset;
    private final SessionManager sessions;

    AuthController(
            AuthService authService,
            RegistrationService registration,
            PasswordResetService passwordReset,
            SessionManager sessions) {
        this.authService = authService;
        this.registration = registration;
        this.passwordReset = passwordReset;
        this.sessions = sessions;
    }

    @Operation(summary = "Create an account; always 202, a verification email follows")
    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http, Locale locale) {
        registration.register(
                new RegisterAccount(
                        request.email(),
                        request.password(),
                        request.firstName().strip(),
                        request.lastName().strip(),
                        Boolean.TRUE.equals(request.marketingOptIn()),
                        locale.getLanguage()),
                http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Sign in with email and password; sets the session cookie")
    @PostMapping("/login")
    SessionView login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        ShopUserPrincipal principal = authService.authenticate(request.email(), request.password(), http.getRemoteAddr());
        sessions.login(principal, http, response);
        return authService.currentUser(principal).map(SessionView::of).orElseGet(SessionView::anonymous);
    }

    @Operation(summary = "Sign out")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        sessions.logout(http, response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "The current session's user, if any")
    @GetMapping("/me")
    SessionView me(@AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http,
            HttpServletResponse response) {
        return authService.currentUser(principal).map(SessionView::of).orElseGet(() -> {
            if (principal != null) {
                // The account was deleted or locked in the meantime.
                sessions.logout(http, response);
            }
            return SessionView.anonymous();
        });
    }

    @Operation(summary = "Confirm an email address with the emailed link")
    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        registration.confirmEmail(request.token(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send the verification email again; always 202")
    @PostMapping("/resend-verification")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        registration.resendVerification(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Request a password-reset email; always 202")
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        passwordReset.requestReset(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Set a new password with the emailed link; signs out everywhere")
    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        UUID userId = passwordReset.resetPassword(request.token(), request.newPassword(), http.getRemoteAddr());
        sessions.endAllSessions(userId);
        sessions.logout(http, response);
        return ResponseEntity.noContent().build();
    }
}
