package com.pwaquatics.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Starts and ends login sessions. Sessions are stored server-side (Spring Session JDBC),
 * so they can be revoked at any time.
 *
 * <p>Administrators get the {@code ADMIN} role only in a session that passed both steps of
 * signing in (password and a code from the authenticator app, ADR-015). Signed in with the
 * password alone (before they set up the second step) they are signed in, but the admin area and
 * every {@code hasRole('ADMIN')} check stay closed.
 */
@Component
public class SessionManager {

    /** How long the second step of signing in may take after the password. */
    public static final Duration SECOND_STEP_WINDOW = Duration.ofMinutes(5);
    /** Wrong codes allowed before the password must be entered again. */
    public static final int SECOND_STEP_ATTEMPTS = 5;
    /** Granted with the role to administrators who passed the second step. */
    public static final String SECOND_FACTOR = "FACTOR_TOTP";

    private static final String CONFIRMED_AT = "pwa.confirmedWithGoogleAt";
    private static final String PENDING_SIGN_IN = "pwa.pendingSignIn";

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;

    public SessionManager(
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            FindByIndexNameSessionRepository<? extends Session> sessions,
            Clock clock) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessions = sessions;
        this.clock = clock;
        // On login: new session id (prevents session fixation) and a fresh CSRF token.
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    /** Logs the user in for this browser session with one step (password or Google). */
    public void login(ShopUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        login(principal, request, response, false);
    }

    /**
     * Logs the user in for this browser session: new session id, new CSRF token.
     *
     * @param secondStepPassed the user also entered a code from the authenticator app (or set the
     *     app up just now); only then does an administrator get the {@code ADMIN} role
     */
    public void login(ShopUserPrincipal principal, HttpServletRequest request, HttpServletResponse response,
            boolean secondStepPassed) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, authorities(principal, secondStepPassed));
        HttpSession session = request.getSession(true);
        session.removeAttribute(PENDING_SIGN_IN);
        sessionStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static List<GrantedAuthority> authorities(ShopUserPrincipal principal, boolean secondStepPassed) {
        if (!principal.isAdmin()) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        }
        return secondStepPassed
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority(SECOND_FACTOR))
                : List.of();
    }

    /** Whether the current request's session may use the admin area. */
    public boolean hasAdminAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * The password was right and the account has a second step: remembers the sign-in (not yet
     * signed in) until the code arrives, for {@link #SECOND_STEP_WINDOW}. The session id changes,
     * so a planted session cannot be used for the second step.
     */
    public void startSecondStep(ShopUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        // Whoever was signed in in this browser before is signed out.
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        securityContextRepository.saveContext(empty, request, response);
        session.removeAttribute(CONFIRMED_AT);
        session.setAttribute(PENDING_SIGN_IN,
                new PendingSignIn(principal, clock.instant().plus(SECOND_STEP_WINDOW), SECOND_STEP_ATTEMPTS));
    }

    /** The account waiting for its second step in this session, if it has not expired. */
    public Optional<ShopUserPrincipal> pendingSignIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_SIGN_IN) instanceof PendingSignIn pending)) {
            return Optional.empty();
        }
        if (!pending.expiresAt().isAfter(clock.instant())) {
            session.removeAttribute(PENDING_SIGN_IN);
            return Optional.empty();
        }
        return Optional.of(pending.principal());
    }

    /**
     * Counts a wrong code of the second step.
     *
     * @return true if that was the last attempt: the sign-in is forgotten and must start again
     *     with the password
     */
    public boolean wrongSecondStepCode(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_SIGN_IN) instanceof PendingSignIn pending)) {
            return false;
        }
        if (pending.attemptsLeft() <= 1) {
            session.removeAttribute(PENDING_SIGN_IN);
            return true;
        }
        session.setAttribute(PENDING_SIGN_IN,
                new PendingSignIn(pending.principal(), pending.expiresAt(), pending.attemptsLeft() - 1));
        return false;
    }

    /**
     * Notes that the user just confirmed who they are with the Google account connected to their
     * shop account; the account service accepts this instead of the password for a while.
     */
    public void recordConfirmation(HttpServletRequest request, Instant at) {
        request.getSession(true).setAttribute(CONFIRMED_AT, at);
    }

    /** When this session last confirmed with Google, or null. */
    public @Nullable Instant confirmation(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(CONFIRMED_AT) instanceof Instant at ? at : null;
    }

    /** A confirmation counts for one sensitive change. */
    public void clearConfirmation(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(CONFIRMED_AT);
        }
    }

    /** Ends the current session and rotates the CSRF token. */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfTokenRepository.saveToken(null, request, response);
    }

    /** Ends every session of the user, e.g. after a password reset or account deletion. */
    public void endAllSessions(UUID userId) {
        endSessions(userId, null);
    }

    /** Ends every session of the user except the given one (the browser that made the change). */
    public void endOtherSessions(UUID userId, HttpServletRequest currentRequest) {
        HttpSession current = currentRequest.getSession(false);
        endSessions(userId, current == null ? null : current.getId());
    }

    private void endSessions(UUID userId, String exceptSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(exceptSessionId))
                .forEach(sessions::deleteById);
    }
}
