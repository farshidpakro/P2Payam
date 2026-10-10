package ir.p2payam.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ConversationIdGenerator {

    private ConversationIdGenerator() {
    }

    public static String fromUserIds(
            String userId1,
            String userId2
    ) throws Exception {

        if (userId1 == null ||
                userId2 == null) {

            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        String first;
        String second;

        if (userId1.compareTo(userId2) <= 0) {
            first = userId1;
            second = userId2;
        } else {
            first = userId2;
            second = userId1;
        }

        String input =
                first +
                        ":" +
                        second;

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        byte[] hash =
                digest.digest(
                        input.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder result =
                new StringBuilder();

        for (byte b : hash) {
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