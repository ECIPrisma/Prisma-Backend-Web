package com.prisma.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HU-12: cifrado AES-256-GCM de los tokens de la pasarela de pagos antes de guardarlos en la base de datos.
 * La llave (32 bytes en Base64) viene SOLO de la variable PAGOS_ENCRYPTION_KEY; sin ella, la vinculación
 * de cuentas de cobro queda deshabilitada en lugar de guardar tokens sin protección.
 */
@Component
public class CifradorSecretos {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec llave;

    public CifradorSecretos(@Value("${prisma.pagos.encryption-key:}") String llaveBase64) {
        this.llave = construirLlave(llaveBase64);
    }

    private static SecretKeySpec construirLlave(String llaveBase64) {
        if (!StringUtils.hasText(llaveBase64)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(llaveBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PAGOS_ENCRYPTION_KEY no es Base64 válido", ex);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("PAGOS_ENCRYPTION_KEY debe tener 32 bytes (AES-256) codificados en Base64");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public boolean estaConfigurado() {
        return llave != null;
    }

    public String cifrar(String textoPlano) {
        exigirLlave();
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No fue posible cifrar el secreto", ex);
        }
    }

    public String descifrar(String textoCifrado) {
        exigirLlave();
        try {
            byte[] datos = Base64.getDecoder().decode(textoCifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(TAG_BITS, datos, 0, IV_BYTES));
            return new String(cipher.doFinal(datos, IV_BYTES, datos.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No fue posible descifrar el secreto", ex);
        }
    }

    private void exigirLlave() {
        if (llave == null) {
            throw new IllegalStateException("PAGOS_ENCRYPTION_KEY no está configurada");
        }
    }
}
