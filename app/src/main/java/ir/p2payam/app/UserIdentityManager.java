package ir.p2payam.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;

public class UserIdentityManager {

    private static final String PREFS_NAME =
            "p2payam_user_identity";

    private static final String KEY_USERNAME =
            "username";

    private final Context context;

    private String username;
    private String userId;

    public UserIdentityManager(Context context) {
        this.context =
                context.getApplicationContext();
    }


    // =====================================================
    // Initialize
    // =====================================================

    public synchronized void initialize(
            IdentityManager identityManager
    ) throws Exception {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        username =
                preferences.getString(
                        KEY_USERNAME,
                        null
                );


        /*
         * User ID is derived from the identity
         * public key.
         *
         * Username is only the human-readable alias.
         */
        byte[] publicKey =
                identityManager
                        .getPublicKey()
                        .getEncoded();


        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        userId =
                bytesToHex(
                        digest.digest(
                                publicKey
                        )
                );
    }


    // =====================================================
    // First Launch
    // =====================================================

    public synchronized boolean hasUsername() {

        return username != null &&
                !username.trim().isEmpty();
    }


    // =====================================================
    // Username
    // =====================================================

    public synchronized String getUsername() {

        if (!hasUsername()) {

            throw new IllegalStateException(
                    "Username not configured"
            );
        }

        return username;
    }


    public synchronized void setUsername(
            String username
    ) {

        String normalized =
                normalizeUsername(
                        username
                );


        if (normalized.length() < 3) {

            throw new IllegalArgumentException(
                    "Username must contain at least 3 characters"
            );
        }


        if (normalized.length() > 32) {

            throw new IllegalArgumentException(
                    "Username must contain at most 32 characters"
            );
        }


        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );


        preferences.edit()
                .putString(
                        KEY_USERNAME,
                        normalized
                )
                .apply();


        this.username =
                normalized;
    }


    // =====================================================
    // User ID
    // =====================================================

    public synchronized String getUserId() {

        if (userId == null) {

            throw new IllegalStateException(
                    "User identity not initialized"
            );
        }

        return userId;
    }


    // =====================================================
    // Normalize Username
    // =====================================================

    private String normalizeUsername(
            String username
    ) {

        if (username == null) {

            throw new IllegalArgumentException(
                    "Username is null"
            );
        }


        String value =
                username.trim()
                        .toLowerCase();


        if (value.startsWith("@")) {

            value =
                    value.substring(1);
        }


        if (!value.matches(
                "[a-z0-9_]{3,32}"
        )) {

            throw new IllegalArgumentException(
                    "Username can contain only a-z, 0-9 and _"
            );
        }


        return value;
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