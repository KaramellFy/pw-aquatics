package com.pwaquatics.shop.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base32Test {

    /** Test vectors of RFC 4648 §10, without the padding. */
    @Test
    void encodesLikeRfc4648() {
        assertThat(encode("")).isEmpty();
        assertThat(encode("f")).isEqualTo("MY");
        assertThat(encode("fo")).isEqualTo("MZXQ");
        assertThat(encode("foo")).isEqualTo("MZXW6");
        assertThat(encode("foob")).isEqualTo("MZXW6YQ");
        assertThat(encode("fooba")).isEqualTo("MZXW6YTB");
        assertThat(encode("foobar")).isEqualTo("MZXW6YTBOI");
    }

    @Test
    void decodesWhatItEncodesIgnoringCaseSpacesAndPadding() {
        assertThat(new String(Base32.decode("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("mzxw 6ytb oi======"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        byte[] random = new byte[20];
        new java.security.SecureRandom().nextBytes(random);
        assertThat(Base32.decode(Base32.encode(random))).isEqualTo(random);
        assertThatThrownBy(() -> Base32.decode("MZ1")).isInstanceOf(IllegalArgumentException.class);
    }

    private static String encode(String text) {
        return Base32.encode(text.getBytes(StandardCharsets.US_ASCII));
    }
}
