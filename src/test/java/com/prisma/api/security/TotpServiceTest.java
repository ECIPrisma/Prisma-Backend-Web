package com.prisma.api.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsAndDecryptsSecret() {
        String secret = "JBSWY3DPEHPK3PXP";

        String encrypted = totpService.encryptSecret(secret);

        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(totpService.decryptSecret(encrypted)).isEqualTo(secret);
    }

    @Test
    void validatesCurrentTotpCodeAndRejectsInvalidCode() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        long step = System.currentTimeMillis() / 1000 / 30;
        String code = codeForStep(secret, step);

        assertThat(totpService.matchingStep(secret, code)).hasValue(step);
        assertThat(totpService.matchingStep(secret, "invalid").isPresent()).isFalse();
    }

    @Test
    void generatesCompatibleOtpAuthUri() {
        String uri = totpService.buildOtpAuthUri("ABC234", "user@example.com");

        assertThat(uri).startsWith("otpauth://totp/Prisma%3Auser%40example.com?");
        assertThat(uri).contains("secret=ABC234", "issuer=Prisma", "digits=6", "period=30");
    }

    private String codeForStep(String base32Secret, long step) throws Exception {
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
    }

    private byte[] decodeBase32(String secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] result = new byte[secret.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : secret.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(character);
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
