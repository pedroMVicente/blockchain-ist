package pt.tecnico.blockchainist.common.key;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import pt.tecnico.blockchainist.common.DebugLog;

/**
 * Utility class for loading RSA and AES cryptographic keys from classpath resources
 * and performing RSA/SHA-256 signature and AES-256-GCM encryption operations.
 *
 * <p>Supported key formats:
 * <ul>
 *   <li>RSA public keys: DER-encoded <b>X.509 (SubjectPublicKeyInfo)</b></li>
 *   <li>RSA private keys: DER-encoded <b>PKCS#8</b></li>
 *   <li>AES keys: raw 32-byte binary (e.g. generated with {@code openssl rand -out secret.key 32})</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ClassLoader cl = MyClass.class.getClassLoader();
 * PublicKey  pub  = KeyManager.loadPublicKey(cl,  "keys/public.der");
 * PrivateKey priv = KeyManager.loadPrivateKey(cl, "keys/private.der");
 * SecretKeySpec aes = KeyManager.loadAESKey(cl,  "secret.key");
 * }</pre>
 */
public class KeyManager {

    private static final String CLASSNAME = KeyManager.class.getSimpleName();
    private static final String RSA_ALGORITHM = "RSA";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final int AES_KEY_SIZE = 32; // bytes (256 bits)
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes (96 bits)

    public static final String RSA_KEY_EXTENSION = ".der";
    public static final String AES_KEY_EXTENSION = ".key";

    /**
     * Reads all bytes from a classpath resource.
     *
     * @param classLoader the {@link ClassLoader} used to locate the resource
     * @param path        the classpath-relative path to the resource
     * @return the full contents of the resource as a byte array
     * @throws IOException              if an I/O error occurs while reading
     * @throws IllegalArgumentException if no resource exists at the given path
     */
    private static byte[] readResource(
        ClassLoader classLoader,
        String path
    ) throws IOException {

        try (InputStream is = classLoader.getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return is.readAllBytes();
        }
    }

    /**
     * Loads an RSA public key from a DER-encoded classpath resource.
     *
     * @param classLoader  the {@link ClassLoader} used to locate the resource
     * @param resourcePath classpath-relative path to the X.509 DER-encoded public key file
     * @return the decoded {@link PublicKey}
     * @throws IOException              if the resource cannot be read or is not found
     * @throws NoSuchAlgorithmException if RSA is unavailable in the current JVM
     * @throws InvalidKeySpecException  if the key bytes do not conform to X.509 encoding
     */
    public static PublicKey loadPublicKey(
        ClassLoader classLoader,
        String resourcePath
    )
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] keyBytes = readResource(classLoader, resourcePath);
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    /**
     * Loads an RSA private key from a DER-encoded classpath resource.
     *
     * <p>To convert a PEM private key to PKCS#8 DER format:
     * <pre>{@code
     * openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in private.pem -out private.der
     * }</pre>
     *
     * @param classLoader  the {@link ClassLoader} used to locate the resource
     * @param resourcePath classpath-relative path to the PKCS#8 DER-encoded private key file
     * @return the decoded {@link PrivateKey}
     * @throws IOException              if the resource cannot be read or is not found
     * @throws NoSuchAlgorithmException if RSA is unavailable in the current JVM
     * @throws InvalidKeySpecException  if the key bytes do not conform to PKCS#8 encoding
     */
    public static PrivateKey loadPrivateKey(
        ClassLoader classLoader,
        String resourcePath
    )
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] keyBytes = readResource(classLoader, resourcePath);
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    /**
     * Loads a raw 32-byte AES-256 key from a classpath resource.
     *
     * <p>The file must contain exactly 32 raw bytes, as produced by:
     * <pre>{@code openssl rand -out secret.key 32}</pre>
     *
     * @param classLoader  the {@link ClassLoader} used to locate the resource
     * @param resourcePath classpath-relative path to the raw AES key file
     * @return the {@link SecretKeySpec} wrapping the key, or {@code null} if loading fails
     */
    public static SecretKeySpec loadAESKey(
        ClassLoader classLoader,
        String resourcePath
    ) {

        try {
            byte[] keyBytes = readResource(classLoader, resourcePath);
            if (keyBytes.length != AES_KEY_SIZE) throw new IllegalArgumentException(
                "Expected 32-byte AES-256 key, got " + keyBytes.length + " bytes"
            );
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IOException | IllegalArgumentException e) {
            DebugLog.log(CLASSNAME, "Failed to load AES key from " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates a random 12-byte IV suitable for AES-GCM.
     *
     * @return a fresh 12-byte IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param aesKey         the AES-256 key
     * @param iv             the 12-byte IV (must never be reused with the same key)
     * @param plaintextBytes the data to encrypt
     * @return the ciphertext (with GCM auth tag appended), or {@code null} if encryption fails
     */
    public static byte[] encrypt(
        SecretKeySpec aesKey,
        byte[] iv,
        byte[] plaintextBytes
    ) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(plaintextBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            DebugLog.log(CLASSNAME, "Encryption failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext. Authentication tag verification is performed
     * automatically; tampered ciphertext will cause decryption to fail.
     *
     * @param aesKey           the AES-256 key
     * @param iv               the 12-byte IV used during encryption
     * @param encryptedPayload the ciphertext (with GCM auth tag appended)
     * @return the decrypted plaintext, or {@code null} if decryption or authentication fails
     */
    public static byte[] decrypt(
        SecretKeySpec aesKey,
        byte[] iv,
        byte[] encryptedPayload
    ) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(encryptedPayload);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            DebugLog.log(CLASSNAME, "Decryption failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verifies an RSA/SHA-256 signature over the given data.
     *
     * @param publicKey the RSA public key to verify against
     * @param data      the original data that was signed
     * @param signature the signature bytes to verify
     * @return {@code true} if the signature is valid, {@code false} otherwise
     */
    public static boolean verifySignature(
        PublicKey publicKey,
        byte[] data,
        byte[] signature
    ) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            DebugLog.log(CLASSNAME, "Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Signs data using an RSA private key and the SHA256withRSA algorithm.
     *
     * @param privateKey the RSA private key to sign with
     * @param data       the data to sign
     * @return the signature bytes, or {@code null} if signing fails
     */
    public static byte[] signRequest(PrivateKey privateKey, byte[] data) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            DebugLog.log(CLASSNAME, "Request signing failed: " + e.getMessage());
            return null;
        }
    }
}