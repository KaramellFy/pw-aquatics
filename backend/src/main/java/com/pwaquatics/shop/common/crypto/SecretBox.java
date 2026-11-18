package com.pwaquatics.shop.common.crypto;

import com.pwaquatics.shop.common.config.AppProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encrypts small secrets the shop must be able to read again (the administrators' two-step
 * sign-in keys) with AES-256-GCM and the key from {@code APP_ENCRYPTION_KEY}. A copy of the
 * database alone does not reveal them.
 *
 * <p>Stored form: Base64 of {@code version (1 byte) | IV (12 bytes) | ciphertext and tag}. The
 * {@code context} (e.g. the user id) is bound to the ciphertext as associated data, so a value
 * copied to another row does not open.
 */
@Component
public class SecretBox {

    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecretBox(AppProperties properties) {
        this(properties.security().encryptionKeyBytes());
    }

    /** With a key of 32 bytes (tests). */
    SecretBox(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
    }

    public String seal(byte[] plaintext, String context) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, context);
            byte[] sealed = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + IV_BYTES + sealed.length)
                    .put(VERSION).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * @throws IllegalStateException if the value was changed, belongs to another context or was
     *     sealed with another key
     */
    public byte[] open(String sealed, String context) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(sealed));
        if (buffer.remaining() < 1 + IV_BYTES || buffer.get() != VERSION) {
            throw new IllegalStateException("Unknown format of an encrypted value");
        }
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        try {
            return cipher(Cipher.DECRYPT_MODE, iv, context).doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "An encrypted value could not be opened (was APP_ENCRYPTION_KEY changed?)", e);
        }
    }

    private Cipher cipher(int mode, byte[] iv, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
