package pe.sfe.core.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM para proteger secretos en reposo (RNF-02). Formato del blob:
 * {@code [version:1][iv:12][ciphertext+tag]}. El AAD liga el cifrado a su contexto
 * (p. ej. "emisor:20608945123:sol") para que un blob no sea reutilizable en otro campo.
 * La clave maestra proviene de una variable de entorno (Base64 de 32 bytes).
 */
public final class AesGcmCipher {

    private static final byte VERSION = 1;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    private AesGcmCipher(byte[] rawKey) {
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    /** Falla rapido si la clave no son exactamente 32 bytes. El mensaje nunca incluye la clave. */
    public static AesGcmCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Falta la clave maestra (SFE_MASTER_KEY)");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La clave maestra no es Base64 valido");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("La clave maestra debe tener 32 bytes (AES-256); tiene " + raw.length);
        }
        return new AesGcmCipher(raw);
    }

    public byte[] encrypt(byte[] plain, String aad) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plain);
            return ByteBuffer.allocate(1 + IV_LEN + ct.length).put(VERSION).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar", e);
        }
    }

    public byte[] decrypt(byte[] blob, String aad) {
        if (blob == null || blob.length < 1 + IV_LEN + 16 || blob[0] != VERSION) {
            throw new IllegalArgumentException("Blob cifrado invalido");
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 1, IV_LEN));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return c.doFinal(blob, 1 + IV_LEN, blob.length - 1 - IV_LEN);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException: clave incorrecta, AAD distinto o dato alterado. Sin detalles.
            throw new IllegalArgumentException("No se pudo descifrar (dato alterado o clave incorrecta)");
        }
    }

    public String encryptToBase64(String plain, String aad) {
        return Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8), aad));
    }

    public Secret decryptToSecret(String base64Blob, String aad) {
        byte[] plain = decrypt(Base64.getDecoder().decode(base64Blob), aad);
        return Secret.of(new String(plain, StandardCharsets.UTF_8));
    }
}
