package com.pwaquatics.shop.pages.application;

/** Supplies the built frontend's page template and texts. */
public interface ShellSource {

    /**
     * The current template; checks for a new frontend build first.
     *
     * @throws ShellUnavailableException if there is no template (frontend never reached, or
     *     page filling switched off)
     */
    PageShell current();

    /** Thrown when no template is available; the edge proxy then serves the plain index.html. */
    final class ShellUnavailableException extends RuntimeException {
        public ShellUnavailableException(String message) {
            super(message);
        }

        public ShellUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
