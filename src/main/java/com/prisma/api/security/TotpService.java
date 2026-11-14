package com.prisma.api.security;

import com.prisma.api.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.OptionalLong;

@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long STEP_SECONDS = 30;

    private final String encryptionKeyBase64;

    public TotpService(@Value("${prisma.totp.encryption-key:}") String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    public String generateSecret() {
        byte[] secret = new byte[20];
        SECURE_RANDOM.nextBytes(secret);
        return encodeBase32(secret);
    }

    public String buildOtpAuthUri(String secret, String email) {
        String label = urlEncode("Prisma:" + email);
        String issuer = urlEncode("Prisma");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
    }

    public OptionalLong matchingStep(String secret, String code) {
        if (secret == null || code == null || !code.matches("[0-9]{6}")) {
            return OptionalLong.empty();
        }

        long currentStep = System.currentTimeMillis() / 1000 / STEP_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (constantTimeEquals(code, codeForStep(secret, step))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public String encryptSecret(String secret) {
        byte[] iv = new byte[12];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP", ex);
        }
    }

    public String decryptSecret(String encryptedSecret) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedSecret);
            if (payload.length <= 12) {
                throw new IllegalArgumentException("Secreto TOTP cifrado inválido");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto TOTP", ex);
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKeyBase64);
            if (key.length != 32) {
                throw new IllegalArgumentException("La clave debe tener 32 bytes");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La configuración de autenticación en dos pasos no está disponible.");
        }
    }

    private static String codeForStep(String base32Secret, long step) {
        try {
            byte[] key = decodeBase32(base32Secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 no disponible", ex);
        }
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return result.toString();
    }

    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").toUpperCase();
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = BASE32_ALPHABET.indexOf(character);
            if (digit < 0) {
                throw new IllegalArgumentException("Secreto TOTP inválido");
            }
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }

    private static boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
