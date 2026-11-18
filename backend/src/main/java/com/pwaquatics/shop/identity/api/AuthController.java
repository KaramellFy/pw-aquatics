package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.common.error.UnauthorizedException;
import com.pwaquatics.shop.identity.application.AuthService;
import com.pwaquatics.shop.identity.application.PasswordResetService;
import com.pwaquatics.shop.identity.application.RegisterAccount;
import com.pwaquatics.shop.identity.application.RegistrationService;
import com.pwaquatics.shop.identity.application.TwoFactorService;
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

/** Registration, login (with the second step for administrators) and password reset (docs/API.md §2.1). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private final AuthService authService;
    private final RegistrationService registration;
    private final PasswordResetService passwordReset;
    private final TwoFactorService twoFactor;
    private final SessionManager sessions;

    AuthController(
            AuthService authService,
            RegistrationService registration,
            PasswordResetService passwordReset,
            TwoFactorService twoFactor,
            SessionManager sessions) {
        this.authService = authService;
        this.registration = registration;
        this.passwordReset = passwordReset;
        this.twoFactor = twoFactor;
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

    @Operation(summary = "Sign in with email and password; sets the session cookie, or asks for the "
            + "second step (twoFactorRequired)")
    @PostMapping("/login")
    SessionView login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        ShopUserPrincipal principal = authService.authenticate(request.email(), request.password(), http.getRemoteAddr());
        if (twoFactor.isEnabled(principal.userId())) {
            sessions.startSecondStep(principal, http, response);
            return SessionView.secondStepRequired();
        }
        sessions.login(principal, http, response);
        return view(principal);
    }

    @Operation(summary = "Second step of signing in: a code from the authenticator app or a recovery code")
    @PostMapping("/login/two-factor")
    SessionView loginSecondStep(
            @Valid @RequestBody TwoFactorCodeRequest request, HttpServletRequest http, HttpServletResponse response) {
        ShopUserPrincipal pending = sessions.pendingSignIn(http)
                .orElseThrow(() -> new UnauthorizedException("TWO_FACTOR_EXPIRED", "Please sign in again."));
        try {
            twoFactor.verifySignIn(pending.userId(), request.code(), http.getRemoteAddr());
        } catch (UnauthorizedException e) {
            if ("TWO_FACTOR_CODE_INVALID".equals(e.code()) && sessions.wrongSecondStepCode(http)) {
                twoFactor.tooManyWrongCodes(pending.userId(), http.getRemoteAddr());
                throw new UnauthorizedException("TWO_FACTOR_EXPIRED", "Too many wrong codes. Please sign in again.");
            }
            throw e;
        }
        sessions.login(pending, http, response, true);
        return view(pending);
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
        if (principal == null) {
            return sessions.pendingSignIn(http).isPresent() ? SessionView.secondStepRequired() : SessionView.anonymous();
        }
        return authService.currentUser(principal)
                .map(user -> SessionView.of(user, sessions.hasAdminAccess()))
                .orElseGet(() -> {
                    // The account was deleted or locked in the meantime.
                    sessions.logout(http, response);
                    return SessionView.anonymous();
                });
    }

    private SessionView view(ShopUserPrincipal principal) {
        return authService.currentUser(principal)
                .map(user -> SessionView.of(user, sessions.hasAdminAccess()))
                .orElseGet(SessionView::anonymous);
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
