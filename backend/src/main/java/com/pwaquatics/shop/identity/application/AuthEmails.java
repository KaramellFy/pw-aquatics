package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.web.SiteLinks;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Account emails in the user's language (English or German). Plain text. */
@Component
class AuthEmails {

    private final SiteLinks links;

    AuthEmails(SiteLinks links) {
        this.links = links;
    }

    EmailMessage verification(User user, String token) {
        String link = link(user, "/verify-email", token);
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
        String login = links.page(user.getLocale(), "/login");
        String reset = links.page(user.getLocale(), "/forgot-password");
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
        String link = link(user, "/reset-password", token);
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
                        """.formatted(user.getFirstName(), links.page(user.getLocale(), "/forgot-password")))
                : mail(user.getEmail(), "Your password was changed", """
                        Hello %s,

                        the password of your PW Aquatics account was just changed. All other devices were signed out.

                        If this was not you, please reset your password immediately: %s
                        """.formatted(user.getFirstName(), links.page(user.getLocale(), "/forgot-password")));
    }

    /** Sent to the NEW address; the change only happens when the link is opened. */
    EmailMessage emailChangeConfirmation(User user, String newEmail, String token) {
        String link = link(user, "/verify-email", token);
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

    /** Security notice: from now on the account can also be used by signing in with this Google account. */
    EmailMessage googleConnected(User user, String googleEmail) {
        String security = links.page(user.getLocale(), "/account/profile");
        return german(user)
                ? mail(user.getEmail(), "Anmeldung mit Google hinzugefügt", """
                        Hallo %s,

                        du hast dich gerade mit deinem Google-Konto (%s) bei PW Aquatics angemeldet. Ab jetzt kannst du dich damit in dein Konto einloggen.

                        Warst du das nicht? Dann melde dich bitte bei uns. Unter %s kannst du die Verbindung zu Google wieder entfernen, sobald du ein Passwort hast.
                        """.formatted(user.getFirstName(), googleEmail, security))
                : mail(user.getEmail(), "Sign-in with Google added", """
                        Hello %s,

                        you have just signed in to PW Aquatics with your Google account (%s). From now on you can use it to sign in to your account.

                        Wasn't you? Please contact us. At %s you can remove the connection to Google again once you have a password.
                        """.formatted(user.getFirstName(), googleEmail, security));
    }

    /** Security notice: the account can no longer be used through Google. */
    EmailMessage googleDisconnected(User user) {
        return german(user)
                ? mail(user.getEmail(), "Anmeldung mit Google entfernt", """
                        Hallo %s,

                        die Verbindung zwischen deinem PW Aquatics Konto und Google wurde entfernt. Du meldest dich ab jetzt mit deiner E-Mail-Adresse und deinem Passwort an.

                        Warst du das nicht? Dann melde dich bitte bei uns.
                        """.formatted(user.getFirstName()))
                : mail(user.getEmail(), "Sign-in with Google removed", """
                        Hello %s,

                        the connection between your PW Aquatics account and Google has been removed. From now on you sign in with your email address and password.

                        Wasn't you? Please contact us.
                        """.formatted(user.getFirstName()));
    }

    /** Security notice: sign-in now asks for a code from this authenticator app. */
    EmailMessage twoFactorEnabled(User user, boolean changed) {
        String security = links.page(user.getLocale(), "/account/profile");
        if (german(user)) {
            return mail(user.getEmail(), changed ? "Neue App für die Anmeldung in zwei Schritten" : "Anmeldung in zwei Schritten ist aktiv", """
                    Hallo %s,

                    %s Bei jeder Anmeldung fragen wir nach deinem Passwort nach einem Code aus dieser App.

                    Bewahre deine Wiederherstellungscodes sicher auf: Mit ihnen kommst du in dein Konto, wenn du dein Handy verlierst. Neue Codes bekommst du unter %s.

                    Warst du das nicht? Dann ändere sofort dein Passwort und melde dich bei uns.
                    """.formatted(user.getFirstName(),
                    changed
                            ? "für dein PW Aquatics Konto wurde gerade eine neue Authenticator-App eingerichtet; die bisherige funktioniert nicht mehr."
                            : "für dein PW Aquatics Konto ist jetzt die Anmeldung in zwei Schritten eingerichtet.",
                    security));
        }
        return mail(user.getEmail(), changed ? "New app for two-step sign-in" : "Two-step sign-in is on", """
                Hello %s,

                %s Every sign-in now asks for a code from this app after your password.

                Keep your recovery codes somewhere safe: they get you into your account if you lose your phone. You can get new ones at %s.

                Wasn't you? Change your password immediately and contact us.
                """.formatted(user.getFirstName(),
                changed
                        ? "a new authenticator app was just set up for your PW Aquatics account; the previous one no longer works."
                        : "two-step sign-in is now set up for your PW Aquatics account.",
                security));
    }

    /** Security notice: a recovery code was used instead of the app. */
    EmailMessage recoveryCodeUsed(User user, int left) {
        String security = links.page(user.getLocale(), "/account/profile");
        return german(user)
                ? mail(user.getEmail(), "Ein Wiederherstellungscode wurde verwendet", """
                        Hallo %s,

                        für dein PW Aquatics Konto wurde gerade ein Wiederherstellungscode statt eines Codes aus der App verwendet. Übrig: %d.

                        Hast du dein Handy verloren? Richte unter %s die App neu ein und hole dir neue Wiederherstellungscodes.

                        Warst du das nicht? Dann ändere sofort dein Passwort und melde dich bei uns.
                        """.formatted(user.getFirstName(), left, security))
                : mail(user.getEmail(), "A recovery code was used", """
                        Hello %s,

                        a recovery code was just used instead of a code from the app for your PW Aquatics account. Left: %d.

                        Lost your phone? Set up the app again at %s and get new recovery codes.

                        Wasn't you? Change your password immediately and contact us.
                        """.formatted(user.getFirstName(), left, security));
    }

    /** Security notice: the password was right, but several codes after it were wrong. */
    EmailMessage twoFactorWrongCodes(User user) {
        String reset = links.page(user.getLocale(), "/forgot-password");
        return german(user)
                ? mail(user.getEmail(), "Mehrere falsche Anmeldecodes", """
                        Hallo %s,

                        bei einer Anmeldung in dein PW Aquatics Konto wurde das richtige Passwort eingegeben, danach aber mehrmals ein falscher Code. Die Anmeldung wurde abgebrochen.

                        Warst du das nicht, kennt jemand dein Passwort. Bitte ändere es sofort: %s
                        """.formatted(user.getFirstName(), reset))
                : mail(user.getEmail(), "Several wrong sign-in codes", """
                        Hello %s,

                        someone signing in to your PW Aquatics account entered the correct password, but then several wrong codes. The sign-in was stopped.

                        If this wasn't you, someone knows your password. Please change it immediately: %s
                        """.formatted(user.getFirstName(), reset));
    }

    /** A one-time link in the user's language. */
    private String link(User user, String path, String token) {
        return links.page(user.getLocale(), path) + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static boolean german(User user) {
        return "de".equals(user.getLocale());
    }

    private static EmailMessage mail(String to, String subject, String body) {
        return new EmailMessage(to, null, subject, body);
    }
}
