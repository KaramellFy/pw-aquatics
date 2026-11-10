package com.pwaquatics.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
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
 */
@Component
public class SessionManager {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionManager(
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessions = sessions;
        // On login: new session id (prevents session fixation) and a fresh CSRF token.
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    /** Logs the user in for this browser session. */
    public void login(ShopUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        request.getSession(true);
        sessionStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
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
