package ir.p2payam.app;

import android.util.Base64;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class MessageSigner {

    private static final String SIGNATURE_ALGORITHM =
            "SHA256withECDSA";

    public static byte[] sign(
            MessagePacket packet,
            PrivateKey privateKey
    ) throws Exception {

        byte[] data =
                MessagePacketCodec.encodeForSigning(
                        packet
                );

        Signature signature =
                Signature.getInstance(
                        SIGNATURE_ALGORITHM
                );

        signature.initSign(privateKey);

        signature.update(data);

        return signature.sign();
    }

    public static boolean verify(
            MessagePacket packet,
            PublicKey publicKey
    ) throws Exception {

        if (packet.signature == null) {
            return false;
        }

        byte[] data =
                MessagePacketCodec.encodeForSigning(
                        packet
                );

        Signature signature =
                Signature.getInstance(
                        SIGNATURE_ALGORITHM
                );

        signature.initVerify(publicKey);

        signature.update(data);

        return signature.verify(
                packet.signature
        );
    }

    public static String signatureToBase64(
            byte[] signature
    ) {

        if (signature == null) {
            return null;
        }

        return Base64.encodeToString(
                signature,
                Base64.NO_WRAP
        );
    }
}