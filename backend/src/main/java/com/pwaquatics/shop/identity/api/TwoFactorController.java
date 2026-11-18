package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.TwoFactorService;
import com.pwaquatics.shop.identity.application.view.RecoveryCodesView;
import com.pwaquatics.shop.identity.application.view.TwoFactorSetupView;
import com.pwaquatics.shop.identity.application.view.TwoFactorStatusView;
import com.pwaquatics.shop.security.SessionManager;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two-step sign-in of the signed-in administrator (docs/API.md §2.6, ADR-015). Customers get
 * 403 {@code TWO_FACTOR_ADMINS_ONLY}.
 */
@RestController
@RequestMapping("/api/v1/account/two-factor")
@Tag(name = "Account")
class TwoFactorController {

    private final TwoFactorService twoFactor;
    private final SessionManager sessions;

    TwoFactorController(TwoFactorService twoFactor, SessionManager sessions) {
        this.twoFactor = twoFactor;
        this.sessions = sessions;
    }

    @Operation(summary = "Whether two-step sign-in is set up, and how many recovery codes are left")
    @GetMapping
    TwoFactorStatusView twoFactorStatus(@AuthenticationPrincipal ShopUserPrincipal principal) {
        return twoFactor.status(principal.userId());
    }

    @Operation(summary = "A new key for the authenticator app; replacing a working app needs a current code")
    @PostMapping("/setup")
    TwoFactorSetupView startTwoFactorSetup(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody TwoFactorSetupRequest request,
            HttpServletRequest http) {
        return twoFactor.startSetup(principal.userId(), request.code(), http.getRemoteAddr());
    }

    @Operation(summary = "Turn on the new key with its first code; returns ten recovery codes once and "
            + "signs out the account's other sessions")
    @PostMapping("/confirm")
    RecoveryCodesView confirmTwoFactorSetup(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody TwoFactorCodeRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        RecoveryCodesView codes = twoFactor.confirmSetup(principal.userId(), request.code(), http.getRemoteAddr());
        // Sessions elsewhere sign in again (ended first, while this session still has the id the
        // session store knows); this one has now passed both steps and gets a new id.
        sessions.endOtherSessions(principal.userId(), http);
        sessions.login(principal, http, response, true);
        return codes;
    }

    @Operation(summary = "Ten new recovery codes for a current code; the old ones stop working")
    @PostMapping("/recovery-codes")
    RecoveryCodesView renewRecoveryCodes(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @Valid @RequestBody TwoFactorCodeRequest request,
            HttpServletRequest http) {
        return twoFactor.renewRecoveryCodes(principal.userId(), request.code(), http.getRemoteAddr());
    }
}
