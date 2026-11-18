package com.pwaquatics.shop.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private static final byte[] PLAIN = "a two-step sign-in key".getBytes(StandardCharsets.UTF_8);

    private final SecretBox box = new SecretBox(randomKey());

    @Test
    void opensWhatItSealedForTheSameContext() {
        String sealed = box.seal(PLAIN, "two-factor:a");

        assertThat(box.open(sealed, "two-factor:a")).isEqualTo(PLAIN);
        assertThat(sealed).doesNotContain("two-step");
        // A new IV every time: the same secret never looks the same twice.
        assertThat(box.seal(PLAIN, "two-factor:a")).isNotEqualTo(sealed);
    }

    @Test
    void refusesAnotherContextAnotherKeyAndChangedValues() {
        String sealed = box.seal(PLAIN, "two-factor:a");

        assertThatThrownBy(() -> box.open(sealed, "two-factor:b")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretBox(randomKey()).open(sealed, "two-factor:a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");

        byte[] bytes = Base64.getDecoder().decode(sealed);
        bytes[bytes.length - 1] ^= 1;
        String changed = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> box.open(changed, "two-factor:a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open("AAAA", "two-factor:a")).isInstanceOf(IllegalStateException.class);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
