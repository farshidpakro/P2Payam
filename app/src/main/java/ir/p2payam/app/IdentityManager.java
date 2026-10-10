package ir.p2payam.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class IdentityManager {

    private static final String KEY_ALIAS =
            "p2payam_identity";

    private static final String PREFS_NAME =
            "p2payam_identity_prefs";

    private static final String PREF_PRIVATE_KEY =
            "private_key";

    private static final String PREF_PUBLIC_KEY =
            "public_key";

    private final Context context;

    private KeyPair identity;


    public IdentityManager(Context context) {

        this.context =
                context.getApplicationContext();
    }


    // =====================================================
    // Get or Create Identity
    // =====================================================

    public synchronized KeyPair getIdentity()
            throws Exception {

        // -------------------------------------------------
        // Already loaded in memory
        // -------------------------------------------------

        if (identity != null) {
            return identity;
        }


        // -------------------------------------------------
        // First try Android Keystore
        // -------------------------------------------------

        try {

            KeyPair keyPair =
                    getFromAndroidKeyStore();

            if (keyPair != null) {

                identity = keyPair;

                return identity;
            }

        } catch (Exception e) {

            /*
             * Older Android/Samsung devices can fail here
             * because PURPOSE_AGREE_KEY is not supported
             * correctly by their Keystore implementation.
             *
             * We intentionally fall back to a software EC key.
             */

            e.printStackTrace();
        }


        // -------------------------------------------------
        // Software-key fallback
        // -------------------------------------------------

        KeyPair storedKey =
                loadSoftwareKey();

        if (storedKey != null) {

            identity = storedKey;

            return identity;
        }


        // -------------------------------------------------
        // Create new software identity
        // -------------------------------------------------

        KeyPair newKey =
                generateSoftwareKey();

        saveSoftwareKey(newKey);

        identity = newKey;

        return identity;
    }


    // =====================================================
    // Android Keystore
    // =====================================================

    private KeyPair getFromAndroidKeyStore()
            throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "AndroidKeyStore"
                );

        keyStore.load(null);


        // -------------------------------------------------
        // Existing identity
        // -------------------------------------------------

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.PrivateKeyEntry entry =
                    (KeyStore.PrivateKeyEntry)
                            keyStore.getEntry(
                                    KEY_ALIAS,
                                    null
                            );

            if (entry == null) {
                return null;
            }

            return new KeyPair(

                    entry.getCertificate()
                            .getPublicKey(),

                    entry.getPrivateKey()
            );
        }


        // -------------------------------------------------
        // Create identity
        // -------------------------------------------------

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(

                        KeyProperties.KEY_ALGORITHM_EC,

                        "AndroidKeyStore"
                );


        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(

                        KEY_ALIAS,

                        KeyProperties.PURPOSE_SIGN |
                                KeyProperties.PURPOSE_VERIFY |
                                KeyProperties.PURPOSE_AGREE_KEY
                )

                        .setDigests(
                                KeyProperties.DIGEST_SHA256
                        )

                        .setAlgorithmParameterSpec(
                                new ECGenParameterSpec(
                                        "secp256r1"
                                )
                        )

                        .setUserAuthenticationRequired(
                                false
                        )

                        .build();


        generator.initialize(spec);


        return generator.generateKeyPair();
    }


    // =====================================================
    // Software EC Key
    // =====================================================

    private KeyPair generateSoftwareKey()
            throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "EC"
                );

        generator.initialize(
                new ECGenParameterSpec(
                        "secp256r1"
                )
        );

        return generator.generateKeyPair();
    }


    // =====================================================
    // Save Software Key
    // =====================================================

    private void saveSoftwareKey(
            KeyPair keyPair
    ) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );


        String privateKey =
                Base64.encodeToString(
                        keyPair.getPrivate()
                                .getEncoded(),
                        Base64.NO_WRAP
                );


        String publicKey =
                Base64.encodeToString(
                        keyPair.getPublic()
                                .getEncoded(),
                        Base64.NO_WRAP
                );


        preferences.edit()

                .putString(
                        PREF_PRIVATE_KEY,
                        privateKey
                )

                .putString(
                        PREF_PUBLIC_KEY,
                        publicKey
                )

                .apply();
    }


    // =====================================================
    // Load Software Key
    // =====================================================

    private KeyPair loadSoftwareKey()
            throws Exception {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );


        String privateKeyBase64 =
                preferences.getString(
                        PREF_PRIVATE_KEY,
                        null
                );


        String publicKeyBase64 =
                preferences.getString(
                        PREF_PUBLIC_KEY,
                        null
                );


        if (privateKeyBase64 == null ||
                publicKeyBase64 == null) {

            return null;
        }


        byte[] privateKeyBytes =
                Base64.decode(
                        privateKeyBase64,
                        Base64.NO_WRAP
                );


        byte[] publicKeyBytes =
                Base64.decode(
                        publicKeyBase64,
                        Base64.NO_WRAP
                );


        KeyFactory keyFactory =
                KeyFactory.getInstance(
                        "EC"
                );


        PrivateKey privateKey =
                keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(
                                privateKeyBytes
                        )
                );


        PublicKey publicKey =
                keyFactory.generatePublic(
                        new X509EncodedKeySpec(
                                publicKeyBytes
                        )
                );


        return new KeyPair(
                publicKey,
                privateKey
        );
    }


    // =====================================================
    // Public Key
    // =====================================================

    public PublicKey getPublicKey()
            throws Exception {

        return getIdentity()
                .getPublic();
    }


    // =====================================================
    // Private Key
    // =====================================================

    public PrivateKey getPrivateKey()
            throws Exception {

        return getIdentity()
                .getPrivate();
    }


    // =====================================================
    // Node ID
    // =====================================================

    public String getNodeId()
            throws Exception {

        PublicKey publicKey =
                getPublicKey();


        byte[] publicKeyBytes =
                publicKey.getEncoded();


        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        byte[] hash =
                digest.digest(
                        publicKeyBytes
                );


        return bytesToHex(hash);
    }


    // =====================================================
    // Short Node ID
    // =====================================================

    public String getShortNodeId()
            throws Exception {

        String nodeId =
                getNodeId();


        return nodeId.substring(
                0,
                Math.min(
                        16,
                        nodeId.length()
                )
        );
    }


    // =====================================================
    // Bytes → Hex
    // =====================================================

    private String bytesToHex(
            byte[] bytes
    ) {

        StringBuilder result =
                new StringBuilder();


        for (byte b : bytes) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }


        return result.toString();
    }
}