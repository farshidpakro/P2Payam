package ir.p2payam.app;

import java.security.SecureRandom;

public class MessageIdGenerator {

    private static final SecureRandom random =
            new SecureRandom();


    public static String generate() {

        byte[] bytes =
                new byte[16];

        random.nextBytes(bytes);

        StringBuilder result =
                new StringBuilder(32);


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