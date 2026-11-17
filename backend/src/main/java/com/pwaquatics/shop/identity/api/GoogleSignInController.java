package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.error.RateLimitedException;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Cancelled;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Confirmed;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Failed;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Intent;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Origin;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Outcome;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow.Unknown;
import com.pwaquatics.shop.identity.application.GoogleSignInRefused;
import com.pwaquatics.shop.identity.application.GoogleSignInService;
import com.pwaquatics.shop.security.SessionManager;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in with Google (docs/API.md §2.1). The browser opens {@code /auth/google} as a normal page
 * load (a link, not a fetch), goes through Google and comes back to {@code /auth/google/callback},
 * which signs in and sends the browser on to the shop page it came from. Problems are reported to
 * that page with {@code ?google=<code>}.
 */
@RestController
@Tag(name = "Auth")
class GoogleSignInController {

    static final String CALLBACK_PATH = "/api/v1/auth/google/callback";
    private static final String RATE_LIMIT = "google-sign-in";
    private static final String DEFAULT_RETURN = "/account";

    private final GoogleAuthorizationFlow flow;
    private final GoogleSignInService signIn;
    private final SessionManager sessions;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    GoogleSignInController(
            GoogleAuthorizationFlow flow,
            GoogleSignInService signIn,
            SessionManager sessions,
            RateLimiter rateLimiter,
            Clock clock) {
        this.flow = flow;
        this.signIn = signIn;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Operation(summary = "Ways to sign in besides email and password")
    @GetMapping("/api/v1/auth/sign-in-options")
    SignInOptionsView signInOptions() {
        return new SignInOptionsView(flow.isAvailable());
    }

    @Operation(summary = "Start signing in with Google (open as a page, not with fetch); redirects to Google")
    @GetMapping("/api/v1/auth/google")
    ResponseEntity<Void> start(
            @RequestParam(required = false) String returnTo,
            @RequestParam(defaultValue = "sign-in") String intent,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http,
            HttpServletResponse response) {
        if (!flow.isAvailable()) {
            throw new NotFoundException("Signing in with Google is not available.");
        }
        Origin origin = origin("confirm".equals(intent) ? Intent.CONFIRM : Intent.SIGN_IN, returnTo);
        try {
            rateLimiter.consume(RATE_LIMIT, http.getRemoteAddr());
        } catch (RateLimitedException e) {
            return back(origin, "too_many");
        }
        if (origin.intent() == Intent.CONFIRM && principal == null) {
            return back(new Origin(Intent.SIGN_IN, origin.returnTo(), origin.language()), "failed");
        }
        return redirect(flow.start(http, response, origin).toString());
    }

    @Operation(summary = "Google sends the browser back here; signs in and redirects to the shop")
    @GetMapping(CALLBACK_PATH)
    ResponseEntity<Void> callback(
            @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http, HttpServletResponse response) {
        Outcome outcome = flow.finish(http, response);
        return switch (outcome) {
            case Unknown ignored -> back(origin(Intent.SIGN_IN, null), "expired");
            case Cancelled cancelled -> back(cancelled.origin(), "cancelled");
            case Failed failed -> back(failed.origin(), "failed");
            case Confirmed confirmed -> complete(confirmed, principal, http, response);
        };
    }

    @Operation(summary = "Remove the connection to Google (only while the account has a password)")
    @DeleteMapping("/api/v1/account/google")
    ResponseEntity<Void> disconnect(@AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        signIn.disconnect(principal.userId(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> complete(
            Confirmed confirmed, @Nullable ShopUserPrincipal principal, HttpServletRequest http,
            HttpServletResponse response) {
        Origin origin = confirmed.origin();
        try {
            rateLimiter.consume(RATE_LIMIT, http.getRemoteAddr());
        } catch (RateLimitedException e) {
            return back(origin, "too_many");
        }
        try {
            if (origin.intent() == Intent.CONFIRM) {
                if (principal == null) {
                    return back(new Origin(Intent.SIGN_IN, origin.returnTo(), origin.language()), "expired");
                }
                signIn.confirm(principal.userId(), confirmed.account());
                sessions.recordConfirmation(http, clock.instant());
                return back(origin, "confirmed");
            }
            ShopUserPrincipal signedIn = signIn.signIn(confirmed.account(), origin.language(), http.getRemoteAddr());
            sessions.login(signedIn, http, response);
            return redirect(origin.returnTo());
        } catch (GoogleSignInRefused e) {
            return back(origin, switch (e.reason()) {
                case UNVERIFIED_EMAIL -> "unverified";
                case LOCKED -> "locked";
                case PASSWORD_ONLY -> "password_only";
                case OTHER_GOOGLE_ACCOUNT -> "other_account";
                case NOT_CONNECTED -> "not_connected";
            });
        }
    }

    /**
     * Back to the shop with a message: to the sign-in page (keeping where the customer wanted to
     * go) for a sign-in, to the page the customer came from for a confirmation.
     */
    private static ResponseEntity<Void> back(Origin origin, String code) {
        String prefix = "de".equals(origin.language()) ? "/de" : "";
        String target = origin.intent() == Intent.CONFIRM
                ? origin.returnTo()
                : prefix + "/login?redirect=" + URLEncoder.encode(withoutLanguage(origin.returnTo()), StandardCharsets.UTF_8);
        return redirect(target + (target.contains("?") ? "&" : "?") + "google=" + code);
    }

    /** A same-site path (already URL-encoded, see {@link #isSafe}) or Google's address. */
    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    /**
     * Where to go afterwards, as a path on this site (with {@code /de} for German pages, URL-encoded
     * as the browser shows it). Anything else — other sites, {@code //host}, API paths, characters
     * that a link would have encoded — falls back to the account page, so the redirect cannot be
     * abused to send people elsewhere.
     */
    static Origin origin(Intent intent, @Nullable String returnTo) {
        String path = isSafe(returnTo) ? returnTo : DEFAULT_RETURN;
        boolean german = path.equals("/de") || path.startsWith("/de/") || path.startsWith("/de?");
        return new Origin(intent, path, german ? "de" : "en");
    }

    private static boolean isSafe(@Nullable String path) {
        return path != null
                && path.length() <= 512
                && path.startsWith("/")
                && !path.startsWith("//")
                && !path.contains("\\")
                && !path.contains("#")
                && path.chars().allMatch(c -> c > 0x20 && c < 0x7f)
                && !path.startsWith("/api/")
                && !path.startsWith("/_");
    }

    /** The app's sign-in page adds the language itself, so its {@code redirect} has none. */
    private static String withoutLanguage(String path) {
        if (path.equals("/de")) {
            return "/";
        }
        if (path.startsWith("/de/")) {
            return path.substring(3);
        }
        return path.startsWith("/de?") ? "/" + path.substring(3) : path;
    }
}
