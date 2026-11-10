package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Account emails in the user's language (English or German). Plain text. */
@Component
class AuthEmails {

    private final String baseUrl;

    AuthEmails(AppProperties properties) {
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    EmailMessage verification(User user, String token) {
        String link = link("/verify-email", token);
        return german(user)
                ? mail(user.getEmail(), "Bitte bestätige deine E-Mail-Adresse", """
                        Hallo %s,

                        willkommen bei PW Aquatics! Bitte bestätige deine E-Mail-Adresse über diesen Link:

                        %s

                        Der Link ist 48 Stunden gültig. Wenn du kein Konto erstellt hast, kannst du diese E-Mail ignorieren.
                        """.formatted(user.getFirstName(), link))
                : mail(user.getEmail(), "Please confirm your email address", """
                        Hello %s,

                        welcome to PW Aquatics! Please confirm your email address with this link:

                        %s

                        The link is valid for 48 hours. If you did not create an account, you can ignore this email.
                        """.formatted(user.getFirstName(), link));
    }

    /** Sent instead of a verification when someone registers with an address that already has an account. */
    EmailMessage alreadyRegistered(User user) {
        String login = baseUrl + "/login";
        String reset = baseUrl + "/forgot-password";
        return german(user)
                ? mail(user.getEmail(), "Du hast bereits ein Konto bei PW Aquatics", """
                        Hallo %s,

                        jemand hat versucht, mit dieser E-Mail-Adresse ein neues Konto zu erstellen. Du hast bereits ein Konto.

                        Anmelden: %s
                        Passwort vergessen: %s

                        Wenn du das nicht warst, musst du nichts tun.
                        """.formatted(user.getFirstName(), login, reset))
                : mail(user.getEmail(), "You already have a PW Aquatics account", """
                        Hello %s,

                        someone tried to create a new account with this email address. You already have an account.

                        Sign in: %s
                        Forgot your password: %s

                        If this was not you, there is nothing you need to do.
                        """.formatted(user.getFirstName(), login, reset));
    }

    EmailMessage passwordReset(User user, String token) {
        String link = link("/reset-password", token);
        return german(user)
                ? mail(user.getEmail(), "Passwort zurücksetzen", """
                        Hallo %s,

                        über diesen Link kannst du ein neues Passwort festlegen:

                        %s

                        Der Link ist 1 Stunde gültig. Wenn du das nicht angefordert hast, kannst du diese E-Mail ignorieren – dein Passwort bleibt unverändert.
                        """.formatted(user.getFirstName(), link))
                : mail(user.getEmail(), "Reset your password", """
                        Hello %s,

                        use this link to choose a new password:

                        %s

                        The link is valid for 1 hour. If you did not request this, you can ignore this email – your password stays unchanged.
                        """.formatted(user.getFirstName(), link));
    }

    EmailMessage passwordChanged(User user) {
        return german(user)
                ? mail(user.getEmail(), "Dein Passwort wurde geändert", """
                        Hallo %s,

                        das Passwort deines PW Aquatics Kontos wurde soeben geändert. Alle anderen Geräte wurden abgemeldet.

                        Wenn du das nicht warst, setze dein Passwort bitte sofort zurück: %s
                        """.formatted(user.getFirstName(), baseUrl + "/forgot-password"))
                : mail(user.getEmail(), "Your password was changed", """
                        Hello %s,

                        the password of your PW Aquatics account was just changed. All other devices were signed out.

                        If this was not you, please reset your password immediately: %s
                        """.formatted(user.getFirstName(), baseUrl + "/forgot-password"));
    }

    /** Sent to the NEW address; the change only happens when the link is opened. */
    EmailMessage emailChangeConfirmation(User user, String newEmail, String token) {
        String link = link("/verify-email", token);
        return german(user)
                ? mail(newEmail, "Bitte bestätige deine neue E-Mail-Adresse", """
                        Hallo %s,

                        bitte bestätige diese Adresse als neue E-Mail-Adresse für dein PW Aquatics Konto:

                        %s

                        Der Link ist 48 Stunden gültig. Wenn du das nicht angefordert hast, kannst du diese E-Mail ignorieren.
                        """.formatted(user.getFirstName(), link))
                : mail(newEmail, "Please confirm your new email address", """
                        Hello %s,

                        please confirm this address as the new email address of your PW Aquatics account:

                        %s

                        The link is valid for 48 hours. If you did not request this, you can ignore this email.
                        """.formatted(user.getFirstName(), link));
    }

    /** Sent to the OLD address after a completed change, so a takeover does not go unnoticed. */
    EmailMessage emailChanged(String oldEmail, User user) {
        return german(user)
                ? mail(oldEmail, "Deine E-Mail-Adresse wurde geändert", """
                        Hallo %s,

                        die E-Mail-Adresse deines PW Aquatics Kontos wurde geändert. Diese Adresse ist nicht mehr mit dem Konto verbunden.

                        Wenn du das nicht warst, melde dich bitte umgehend bei uns.
                        """.formatted(user.getFirstName()))
                : mail(oldEmail, "Your email address was changed", """
                        Hello %s,

                        the email address of your PW Aquatics account was changed. This address is no longer linked to the account.

                        If this was not you, please contact us immediately.
                        """.formatted(user.getFirstName()));
    }

    EmailMessage accountDeleted(String email, String firstName, boolean german) {
        return german
                ? mail(email, "Dein Konto wurde gelöscht", """
                        Hallo %s,

                        dein PW Aquatics Konto wurde gelöscht und deine persönlichen Daten wurden entfernt. Bestelldaten bewahren wir nur so lange auf, wie es gesetzlich vorgeschrieben ist.

                        Danke, dass du bei uns warst.
                        """.formatted(firstName))
                : mail(email, "Your account was deleted", """
                        Hello %s,

                        your PW Aquatics account was deleted and your personal data was removed. We keep order records only for as long as the law requires.

                        Thank you for having been with us.
                        """.formatted(firstName));
    }

    private String link(String path, String token) {
        return baseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static boolean german(User user) {
        return "de".equals(user.getLocale());
    }

    private static EmailMessage mail(String to, String subject, String body) {
        return new EmailMessage(to, null, subject, body);
    }
}
