package ir.p2payam.app;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {

    private static final String KEY_ALGORITHM = "AES";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int AES_KEY_SIZE = 32;

    private static final int GCM_TAG_LENGTH = 128;

    private static final int NONCE_LENGTH = 12;


    // =====================================================
    // Encrypt
    // =====================================================

    public static String encrypt(
            String plainText,
            PublicKey recipientPublicKey
    ) throws Exception {

        // ---------------------------------------------
        // Create temporary / ephemeral EC key pair
        // ---------------------------------------------

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("EC");

        keyPairGenerator.initialize(
                new java.security.spec.ECGenParameterSpec(
                        "secp256r1"
                )
        );

        KeyPair ephemeralKeyPair =
                keyPairGenerator.generateKeyPair();


        // ---------------------------------------------
        // ECDH
        // ---------------------------------------------

        byte[] sharedSecret =
                createSharedSecret(
                        ephemeralKeyPair.getPrivate(),
                        recipientPublicKey
                );


        // ---------------------------------------------
        // Derive AES key
        // ---------------------------------------------

        byte[] aesKey =
                sha256(sharedSecret);


        // ---------------------------------------------
        // Generate random nonce
        // ---------------------------------------------

        byte[] nonce =
                new byte[NONCE_LENGTH];

        SecureRandom secureRandom =
                new SecureRandom();

        secureRandom.nextBytes(nonce);


        // ---------------------------------------------
        // AES-GCM encryption
        // ---------------------------------------------

        Cipher cipher =
                Cipher.getInstance(
                        TRANSFORMATION
                );

        GCMParameterSpec gcmSpec =
                new GCMParameterSpec(
                        GCM_TAG_LENGTH,
                        nonce
                );

        SecretKeySpec secretKey =
                new SecretKeySpec(
                        aesKey,
                        KEY_ALGORITHM
                );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey,
                gcmSpec
        );


        byte[] ciphertext =
                cipher.doFinal(
                        plainText.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        // ---------------------------------------------
        // Store ephemeral public key
        // ---------------------------------------------

        byte[] ephemeralPublicKey =
                ephemeralKeyPair
                        .getPublic()
                        .getEncoded();


        // ---------------------------------------------
        // Package:
        //
        // [4 bytes public key length]
        // [public key]
        // [12 bytes nonce]
        // [ciphertext]
        // ---------------------------------------------

        ByteBuffer buffer =
                ByteBuffer.allocate(
                        4 +
                                ephemeralPublicKey.length +
                                nonce.length +
                                ciphertext.length
                );


        buffer.putInt(
                ephemeralPublicKey.length
        );

        buffer.put(
                ephemeralPublicKey
        );

        buffer.put(
                nonce
        );

        buffer.put(
                ciphertext
        );


        return Base64.encodeToString(
                buffer.array(),
                Base64.NO_WRAP
        );
    }


    public static String publicKeyToBase64(
            PublicKey publicKey
    ) {

        return Base64.encodeToString(
                publicKey.getEncoded(),
                Base64.NO_WRAP
        );
    }


    public static PublicKey base64ToPublicKey(
            String encodedKey
    ) throws Exception {

        byte[] keyBytes =
                Base64.decode(
                        encodedKey,
                        Base64.NO_WRAP
                );

        KeyFactory keyFactory =
                KeyFactory.getInstance("EC");

        return keyFactory.generatePublic(
                new X509EncodedKeySpec(
                        keyBytes
                )
        );
    }
    // =====================================================
    // Decrypt
    // =====================================================

    public static String decrypt(
            String encryptedData,
            PrivateKey recipientPrivateKey
    ) throws Exception {

        byte[] data =
                Base64.decode(
                        encryptedData,
                        Base64.NO_WRAP
                );


        ByteBuffer buffer =
                ByteBuffer.wrap(data);


        // ---------------------------------------------
        // Read ephemeral public key
        // ---------------------------------------------

        int publicKeyLength =
                buffer.getInt();


        byte[] ephemeralPublicKeyBytes =
                new byte[publicKeyLength];


        buffer.get(
                ephemeralPublicKeyBytes
        );


        // ---------------------------------------------
        // Reconstruct PublicKey
        // ---------------------------------------------

        KeyFactory keyFactory =
                KeyFactory.getInstance("EC");


        PublicKey ephemeralPublicKey =
                keyFactory.generatePublic(
                        new X509EncodedKeySpec(
                                ephemeralPublicKeyBytes
                        )
                );


        // ---------------------------------------------
        // Read nonce
        // ---------------------------------------------

        byte[] nonce =
                new byte[NONCE_LENGTH];


        buffer.get(nonce);


        // ---------------------------------------------
        // Remaining bytes = ciphertext
        // ---------------------------------------------

        byte[] ciphertext =
                new byte[buffer.remaining()];


        buffer.get(ciphertext);


        // ---------------------------------------------
        // ECDH
        // ---------------------------------------------

        byte[] sharedSecret =
                createSharedSecret(
                        recipientPrivateKey,
                        ephemeralPublicKey
                );


        // ---------------------------------------------
        // Derive AES key
        // ---------------------------------------------

        byte[] aesKey =
                sha256(sharedSecret);


        // ---------------------------------------------
        // AES-GCM decrypt
        // ---------------------------------------------

        Cipher cipher =
                Cipher.getInstance(
                        TRANSFORMATION
                );


        GCMParameterSpec gcmSpec =
                new GCMParameterSpec(
                        GCM_TAG_LENGTH,
                        nonce
                );


        SecretKeySpec secretKey =
                new SecretKeySpec(
                        aesKey,
                        KEY_ALGORITHM
                );


        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                gcmSpec
        );


        byte[] plainText =
                cipher.doFinal(ciphertext);


        return new String(
                plainText,
                StandardCharsets.UTF_8
        );
    }


    // =====================================================
    // ECDH
    // =====================================================

    private static byte[] createSharedSecret(
            PrivateKey privateKey,
            PublicKey publicKey
    ) throws Exception {

        KeyAgreement keyAgreement =
                KeyAgreement.getInstance("ECDH");


        keyAgreement.init(
                privateKey
        );


        keyAgreement.doPhase(
                publicKey,
                true
        );


        return keyAgreement.generateSecret();
    }


    // =====================================================
    // SHA-256
    // =====================================================

    private static byte[] sha256(
            byte[] data
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        return digest.digest(data);
    }
}